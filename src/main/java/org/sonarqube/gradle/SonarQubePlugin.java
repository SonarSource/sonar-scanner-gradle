/**
 * SonarQube Gradle Plugin
 * Copyright (C) 2015-2016 SonarSource
 * sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarqube.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.gradle.api.Nullable;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.listener.ActionBroadcast;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;

/**
 * A plugin for analyzing projects with the <a href="http://redirect.sonarsource.com/doc/analyzing-with-sq-gradle.html">SonarQube Runner</a>.
 * When applied to a project, both the project itself and its subprojects will be analyzed (in a single run).
 * Please see the “SonarQube Runner Plugin” chapter of the Gradle User Guide for more information.
 */
public class SonarQubePlugin implements Plugin<Project> {

  private static final Predicate<File> FILE_EXISTS = input -> input.exists();
  private static final Predicate<File> IS_DIRECTORY = input -> input.isDirectory();
  private static final Predicate<File> IS_FILE = input -> input.isFile();
  public static final String SONAR_SOURCES_PROP = "sonar.sources";

  private Project targetProject;

  private static void evaluateSonarPropertiesBlocks(ActionBroadcast<? super SonarQubeProperties> propertiesActions, Map<String, Object> properties) {
    SonarQubeProperties sqProperties = new SonarQubeProperties(properties);
    propertiesActions.execute(sqProperties);
  }

  @Override
  public void apply(Project project) {
    targetProject = project;

    final Map<Project, ActionBroadcast<SonarQubeProperties>> actionBroadcastMap = new HashMap<>();
    createTask(project, actionBroadcastMap);

    ActionBroadcast<SonarQubeProperties> actionBroadcast = addBroadcaster(actionBroadcastMap, project);
    project.subprojects(p -> {
      ActionBroadcast<SonarQubeProperties> action = addBroadcaster(actionBroadcastMap, p);
      p.getExtensions().create(SonarQubeExtension.SONARQUBE_EXTENSION_NAME, SonarQubeExtension.class, action);
    });
    project.getExtensions().create(SonarQubeExtension.SONARQUBE_EXTENSION_NAME, SonarQubeExtension.class, actionBroadcast);
  }

  private static ActionBroadcast<SonarQubeProperties> addBroadcaster(Map<Project, ActionBroadcast<SonarQubeProperties>> actionBroadcastMap, Project project) {
    ActionBroadcast<SonarQubeProperties> actionBroadcast = new ActionBroadcast<>();
    actionBroadcastMap.put(project, actionBroadcast);
    return actionBroadcast;
  }

  private SonarQubeTask createTask(final Project project, final Map<Project, ActionBroadcast<SonarQubeProperties>> actionBroadcastMap) {
    SonarQubeTask sonarQubeTask = project.getTasks().create(SonarQubeExtension.SONARQUBE_TASK_NAME, SonarQubeTask.class);
    sonarQubeTask.setDescription("Analyzes " + project + " and its subprojects with SonarQube.");

    ConventionMapping conventionMapping = new DslObject(sonarQubeTask).getConventionMapping();
    conventionMapping.map("properties", () -> {
      Map<String, Object> properties = new LinkedHashMap<>();
      computeSonarProperties(project, properties, actionBroadcastMap, "");
      return properties;
    });

    Callable<Iterable<? extends Task>> callable = () ->
      project.getAllprojects().stream()
        .filter(input -> input.getPlugins().hasPlugin(JavaPlugin.class) && !input.getExtensions().getByType(SonarQubeExtension.class).isSkipProject())
        .map(p -> p.getTasks().getByName(JavaPlugin.TEST_TASK_NAME))
        .collect(Collectors.toList());
    sonarQubeTask.dependsOn(callable);
    return sonarQubeTask;
  }

  public void computeSonarProperties(Project project, Map<String, Object> properties, Map<Project, ActionBroadcast<SonarQubeProperties>> sonarPropertiesActionBroadcastMap,
    String prefix) {
    SonarQubeExtension extension = project.getExtensions().getByType(SonarQubeExtension.class);
    if (extension.isSkipProject()) {
      return;
    }

    Map<String, Object> rawProperties = new LinkedHashMap<>();
    addGradleDefaults(project, rawProperties);
    evaluateSonarPropertiesBlocks(sonarPropertiesActionBroadcastMap.get(project), rawProperties);
    if (project.equals(targetProject)) {
      addSystemProperties(rawProperties);
    }

    convertProperties(rawProperties, prefix, properties);

    List<Project> enabledChildProjects = project.getChildProjects().values().stream()
      .filter(input -> !input.getExtensions().getByType(SonarQubeExtension.class).isSkipProject())
      .collect(Collectors.toList());

    if (enabledChildProjects.isEmpty()) {
      return;
    }

    List<String> moduleIds = new ArrayList<>();

    for (Project childProject : enabledChildProjects) {
      String moduleId = childProject.getPath();
      moduleIds.add(moduleId);
      String modulePrefix = (prefix.length() > 0) ? (prefix + "." + moduleId) : moduleId;
      computeSonarProperties(childProject, properties, sonarPropertiesActionBroadcastMap, modulePrefix);
    }
    properties.put(convertKey("sonar.modules", prefix), moduleIds.stream().collect(Collectors.joining(",")));
  }

  private void addGradleDefaults(final Project project, final Map<String, Object> properties) {

    // IMPORTANT: Whenever changing the properties/values here, ensure that the Gradle User Guide chapter on this is still in sync.

    properties.put("sonar.projectName", project.getName());
    properties.put("sonar.projectDescription", project.getDescription());
    properties.put("sonar.projectVersion", project.getVersion());
    properties.put("sonar.projectBaseDir", project.getProjectDir());

    if (project.equals(targetProject)) {
      // Root project
      properties.put("sonar.projectKey", getProjectKey(project));
      properties.put("sonar.working.directory", new File(project.getBuildDir(), "sonar"));
    } else {
      properties.put("sonar.moduleKey", getProjectKey(project));
    }

    configureForJava(project, properties);
    configureForGroovy(project, properties);

    properties.putIfAbsent(SONAR_SOURCES_PROP, "");
  }

  private void configureForJava(final Project project, final Map<String, Object> properties) {
    project.getPlugins().withType(JavaBasePlugin.class, javaBasePlugin -> configureJdkSourceAndTarget(project, properties));

    project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
      boolean hasSourceOrTest = configureSourceDirsAndJavaClasspath(project, properties);
      if (hasSourceOrTest) {
        configureSourceEncoding(project, properties);
        final Test testTask = (Test) project.getTasks().getByName(JavaPlugin.TEST_TASK_NAME);
        configureTestReports(testTask, properties);
        configureJaCoCoCoverageReport(testTask, false, project, properties);
      }
    });
  }

  /**
   * Groovy projects support joint compilation of a mix of Java and Groovy classes. That's why we set both
   * sonar.java.* and sonar.groovy.* properties.
   */
  private void configureForGroovy(final Project project, final Map<String, Object> properties) {
    project.getPlugins().withType(GroovyBasePlugin.class, groovyBasePlugin -> configureJdkSourceAndTarget(project, properties));

    project.getPlugins().withType(GroovyPlugin.class, groovyPlugin -> {
      boolean hasSourceOrTest = configureSourceDirsAndJavaClasspath(project, properties);
      if (hasSourceOrTest) {
        configureSourceEncoding(project, properties);
        final Test testTask = (Test) project.getTasks().getByName(JavaPlugin.TEST_TASK_NAME);
        configureTestReports(testTask, properties);
        configureJaCoCoCoverageReport(testTask, true, project, properties);
      }
    });
  }

  private void configureJaCoCoCoverageReport(final Test testTask, final boolean addForGroovy, Project project, final Map<String, Object> properties) {
    project.getPlugins().withType(JacocoPlugin.class, jacocoPlugin -> {
      JacocoTaskExtension jacocoTaskExtension = testTask.getExtensions().getByType(JacocoTaskExtension.class);
      File destinationFile = jacocoTaskExtension.getDestinationFile();
      if (destinationFile.exists()) {
        properties.put("sonar.jacoco.reportPath", destinationFile);
        if (addForGroovy) {
          properties.put("sonar.groovy.jacoco.reportPath", destinationFile);
        }
      }
    });
  }

  private static void configureTestReports(Test testTask, Map<String, Object> properties) {
    File testResultsDir = testTask.getReports().getJunitXml().getDestination();
    // create the test results folder to prevent SonarQube from emitting
    // a warning if a project does not contain any tests
    try {
      Files.createDirectories(testResultsDir.toPath());
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create test report directory", e);
    }

    properties.put("sonar.junit.reportsPath", testResultsDir);
    // For backward compatibility
    properties.put("sonar.surefire.reportsPath", testResultsDir);
  }

  private boolean configureSourceDirsAndJavaClasspath(Project project, Map<String, Object> properties) {
    JavaPluginConvention javaPluginConvention = new DslObject(project).getConvention().getPlugin(JavaPluginConvention.class);

    SourceSet main = javaPluginConvention.getSourceSets().getAt("main");
    List<File> sourceDirectories = nonEmptyOrNull(main.getAllSource().getSrcDirs().stream().filter(FILE_EXISTS).collect(Collectors.toList()));
    properties.put(SONAR_SOURCES_PROP, sourceDirectories);
    SourceSet test = javaPluginConvention.getSourceSets().getAt("test");
    List<File> testDirectories = nonEmptyOrNull(test.getAllSource().getSrcDirs().stream().filter(FILE_EXISTS).collect(Collectors.toList()));
    properties.put("sonar.tests", testDirectories);

    List<File> mainClasspath = nonEmptyOrNull(main.getRuntimeClasspath().getFiles().stream().filter(IS_DIRECTORY).collect(Collectors.toList()));
    Collection<File> mainLibraries = getLibraries(main);
    properties.put("sonar.java.binaries", mainClasspath);
    properties.put("sonar.java.libraries", mainLibraries);
    List<File> testClasspath = nonEmptyOrNull(test.getRuntimeClasspath().getFiles().stream().filter(IS_DIRECTORY).collect(Collectors.toList()));
    Collection<File> testLibraries = getLibraries(test);
    properties.put("sonar.java.test.binaries", testClasspath);
    properties.put("sonar.java.test.libraries", testLibraries);

    // Populate deprecated properties for backward compatibility
    properties.put("sonar.binaries", mainClasspath);
    properties.put("sonar.libraries", mainLibraries);

    return sourceDirectories != null || testDirectories != null;
  }

  private void configureSourceEncoding(Project project, final Map<String, Object> properties) {
    project.getTasks().withType(JavaCompile.class, compile -> {
      String encoding = compile.getOptions().getEncoding();
      if (encoding != null) {
        properties.put("sonar.sourceEncoding", encoding);
      }
    });
  }

  private void configureJdkSourceAndTarget(Project project, Map<String, Object> properties) {
    JavaPluginConvention javaPluginConvention = new DslObject(project).getConvention().getPlugin(JavaPluginConvention.class);
    properties.put("sonar.java.source", javaPluginConvention.getSourceCompatibility());
    properties.put("sonar.java.target", javaPluginConvention.getTargetCompatibility());
  }

  private static String getProjectKey(Project project) {
    Project rootProject = project.getRootProject();
    String rootProjectName = rootProject.getName();
    String rootGroup = rootProject.getGroup().toString();
    String rootKey = rootGroup.isEmpty() ? rootProjectName : (rootGroup + ":" + rootProjectName);
    if (project == rootProject) {
      return rootKey;
    }
    return rootKey + project.getPath();
  }

  private static void addSystemProperties(Map<String, Object> properties) {
    for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
      String key = entry.getKey().toString();
      if (key.startsWith("sonar")) {
        properties.put(key, entry.getValue());
      }
    }
  }

  private static Collection<File> getLibraries(SourceSet main) {
    List<File> libraries = main.getRuntimeClasspath().getFiles().stream()
      .filter(IS_FILE)
      .collect(Collectors.toList());

    File runtimeJar = getRuntimeJar();
    if (runtimeJar != null) {
      libraries.add(runtimeJar);
    }

    return libraries;
  }

  private static File getRuntimeJar() {
    try{
      final File javaBase =  new File(System.getProperty("java.home")).getCanonicalFile();
      File runtimeJar = new File(javaBase, "lib/rt.jar");
      if (runtimeJar.exists()) {
        return runtimeJar;
      }
      runtimeJar = new File(javaBase, "jre/lib/rt.jar");
      return runtimeJar.exists() ? runtimeJar : null;
    } catch(IOException e){
      throw new RuntimeException(e);
    }

  }


  private static void convertProperties(Map<String, Object> rawProperties, final String projectPrefix, final Map<String, Object> properties) {
    for (Map.Entry<String, Object> entry : rawProperties.entrySet()) {
      String value = convertValue(entry.getValue());
      if (value != null) {
        properties.put(convertKey(entry.getKey(), projectPrefix), value);
      }
    }
  }

  private static String convertKey(String key, final String projectPrefix) {
    return projectPrefix.isEmpty() ? key : (projectPrefix + "." + key);
  }

  private static String convertValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Iterable<?>) {
      String joined = StreamSupport.stream(((Iterable<Object>) value).spliterator(), false)
        .map(SonarQubePlugin::convertValue)
        .filter(v -> v != null)
        .collect(Collectors.joining(","));
      return joined.isEmpty() ? null : joined;
    } else {
      return value.toString();
    }
  }

  @Nullable
  public static <T> List<T> nonEmptyOrNull(Collection<T> collection) {
    List<T> list = Collections.unmodifiableList(new ArrayList<>(collection));
    return list.isEmpty() ? null : list;
  }

}
