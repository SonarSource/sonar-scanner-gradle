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

import aQute.bnd.build.Run;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.gradle.api.Action;
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

  private static final Predicate<File> FILE_EXISTS = new Predicate<File>() {
    @Override
    public boolean apply(File input) {
      return input.exists();
    }
  };
  private static final Predicate<File> IS_DIRECTORY = new Predicate<File>() {
    @Override
    public boolean apply(File input) {
      return input.isDirectory();
    }
  };
  private static final Predicate<File> IS_FILE = new Predicate<File>() {
    @Override
    public boolean apply(File input) {
      return input.isFile();
    }
  };
  private static final Joiner COMMA_JOINER = Joiner.on(",");
  public static final String SONAR_SOURCES_PROP = "sonar.sources";

  private Project targetProject;

  private static void evaluateSonarPropertiesBlocks(ActionBroadcast<? super SonarQubeProperties> propertiesActions, Map<String, Object> properties) {
    SonarQubeProperties sqProperties = new SonarQubeProperties(properties);
    propertiesActions.execute(sqProperties);
  }

  @Override
  public void apply(Project project) {
    targetProject = project;

    final Map<Project, ActionBroadcast<SonarQubeProperties>> actionBroadcastMap = Maps.newHashMap();
    createTask(project, actionBroadcastMap);

    ActionBroadcast<SonarQubeProperties> actionBroadcast = addBroadcaster(actionBroadcastMap, project);
    project.subprojects(new Action<Project>() {
      @Override
      public void execute(Project project) {
        ActionBroadcast<SonarQubeProperties> actionBroadcast = addBroadcaster(actionBroadcastMap, project);
        project.getExtensions().create(SonarQubeExtension.SONARQUBE_EXTENSION_NAME, SonarQubeExtension.class, actionBroadcast);
      }
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
    conventionMapping.map("properties", new Callable<Object>() {
      @Override
      public Object call() throws Exception {
        Map<String, Object> properties = Maps.newLinkedHashMap();
        computeSonarProperties(project, properties, actionBroadcastMap, "");
        return properties;
      }
    });

    sonarQubeTask.dependsOn(new Callable<Iterable<? extends Task>>() {
      @Override
      public Iterable<? extends Task> call() throws Exception {
        Iterable<Project> applicableProjects = Iterables.filter(project.getAllprojects(), new Predicate<Project>() {
          @Override
          public boolean apply(Project input) {
            return input.getPlugins().hasPlugin(JavaPlugin.class)
              && !input.getExtensions().getByType(SonarQubeExtension.class).isSkipProject();
          }
        });

        return Iterables.transform(applicableProjects, new Function<Project, Task>() {
          @Nullable
          @Override
          public Task apply(Project input) {
            return input.getTasks().getByName(JavaPlugin.TEST_TASK_NAME);
          }
        });
      }
    });

    return sonarQubeTask;
  }

  public void computeSonarProperties(Project project, Map<String, Object> properties, Map<Project, ActionBroadcast<SonarQubeProperties>> sonarPropertiesActionBroadcastMap,
    String prefix) {
    SonarQubeExtension extension = project.getExtensions().getByType(SonarQubeExtension.class);
    if (extension.isSkipProject()) {
      return;
    }

    Map<String, Object> rawProperties = Maps.newLinkedHashMap();
    addGradleDefaults(project, rawProperties);
    evaluateSonarPropertiesBlocks(sonarPropertiesActionBroadcastMap.get(project), rawProperties);
    if (project.equals(targetProject)) {
      addSystemProperties(rawProperties);
    }

    convertProperties(rawProperties, prefix, properties);

    List<Project> enabledChildProjects = Lists.newLinkedList(Iterables.filter(project.getChildProjects().values(), new Predicate<Project>() {
      @Override
      public boolean apply(Project input) {
        return !input.getExtensions().getByType(SonarQubeExtension.class).isSkipProject();
      }
    }));

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
    properties.put(convertKey("sonar.modules", prefix), COMMA_JOINER.join(moduleIds));
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

    if (properties.get(SONAR_SOURCES_PROP) == null) {
      properties.put(SONAR_SOURCES_PROP, "");
    }
  }

  private void configureForJava(final Project project, final Map<String, Object> properties) {
    project.getPlugins().withType(JavaBasePlugin.class, new Action<JavaBasePlugin>() {
      @Override
      public void execute(JavaBasePlugin javaBasePlugin) {
        configureJdkSourceAndTarget(project, properties);
      }
    });

    project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
      @Override
      public void execute(JavaPlugin javaPlugin) {
        boolean hasSourceOrTest = configureSourceDirsAndJavaClasspath(project, properties);
        if (hasSourceOrTest) {
          configureSourceEncoding(project, properties);
          final Test testTask = (Test) project.getTasks().getByName(JavaPlugin.TEST_TASK_NAME);
          configureTestReports(testTask, properties);
          configureJaCoCoCoverageReport(testTask, false, project, properties);
        }
      }
    });
  }

  /**
   * Groovy projects support joint compilation of a mix of Java and Groovy classes. That's why we set both
   * sonar.java.* and sonar.groovy.* properties.
   */
  private void configureForGroovy(final Project project, final Map<String, Object> properties) {
    project.getPlugins().withType(GroovyBasePlugin.class, new Action<GroovyBasePlugin>() {
      @Override
      public void execute(GroovyBasePlugin groovyBasePlugin) {
        configureJdkSourceAndTarget(project, properties);
      }
    });

    project.getPlugins().withType(GroovyPlugin.class, new Action<GroovyPlugin>() {
      @Override
      public void execute(GroovyPlugin groovyPlugin) {
        boolean hasSourceOrTest = configureSourceDirsAndJavaClasspath(project, properties);
        if (hasSourceOrTest) {
          configureSourceEncoding(project, properties);
          final Test testTask = (Test) project.getTasks().getByName(JavaPlugin.TEST_TASK_NAME);
          configureTestReports(testTask, properties);
          configureJaCoCoCoverageReport(testTask, true, project, properties);
        }
      }
    });
  }

  private void configureJaCoCoCoverageReport(final Test testTask, final boolean addForGroovy, Project project, final Map<String, Object> properties) {
    project.getPlugins().withType(JacocoPlugin.class, new Action<JacocoPlugin>() {
      @Override
      public void execute(JacocoPlugin jacocoPlugin) {
        JacocoTaskExtension jacocoTaskExtension = testTask.getExtensions().getByType(JacocoTaskExtension.class);
        File destinationFile = jacocoTaskExtension.getDestinationFile();
        if (destinationFile.exists()) {
          properties.put("sonar.jacoco.reportPath", destinationFile);
          if (addForGroovy) {
            properties.put("sonar.groovy.jacoco.reportPath", destinationFile);
          }
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
    List<File> sourceDirectories = nonEmptyOrNull(Iterables.filter(main.getAllSource().getSrcDirs(), FILE_EXISTS));
    properties.put(SONAR_SOURCES_PROP, sourceDirectories);
    SourceSet test = javaPluginConvention.getSourceSets().getAt("test");
    List<File> testDirectories = nonEmptyOrNull(Iterables.filter(test.getAllSource().getSrcDirs(), FILE_EXISTS));
    properties.put("sonar.tests", testDirectories);

    List<File> mainClasspath = nonEmptyOrNull(Iterables.filter(main.getRuntimeClasspath(), IS_DIRECTORY));
    Collection<File> mainLibraries = getLibraries(main);
    properties.put("sonar.java.binaries", mainClasspath);
    properties.put("sonar.java.libraries", mainLibraries);
    List<File> testClasspath = nonEmptyOrNull(Iterables.filter(test.getRuntimeClasspath(), IS_DIRECTORY));
    Collection<File> testLibraries = getLibraries(test);
    properties.put("sonar.java.test.binaries", testClasspath);
    properties.put("sonar.java.test.libraries", testLibraries);

    // Populate deprecated properties for backward compatibility
    properties.put("sonar.binaries", mainClasspath);
    properties.put("sonar.libraries", mainLibraries);

    return sourceDirectories != null || testDirectories != null;
  }

  private void configureSourceEncoding(Project project, final Map<String, Object> properties) {
    project.getTasks().withType(JavaCompile.class, new Action<JavaCompile>() {
      @Override
      public void execute(final JavaCompile compile) {
        String encoding = compile.getOptions().getEncoding();
        if (encoding != null) {
          properties.put("sonar.sourceEncoding", encoding);
        }
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
    List<File> libraries = Lists.newLinkedList(Iterables.filter(main.getRuntimeClasspath(), IS_FILE));
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
      Iterable<String> flattened = Iterables.transform((Iterable<?>) value, new Function<Object, String>() {
        @Override
        public String apply(Object input) {
          return convertValue(input);
        }
      });

      Iterable<String> filtered = Iterables.filter(flattened, Predicates.notNull());
      String joined = COMMA_JOINER.join(filtered);
      return joined.isEmpty() ? null : joined;
    } else {
      return value.toString();
    }
  }

  @Nullable
  public static <T> List<T> nonEmptyOrNull(Iterable<T> iterable) {
    ImmutableList<T> list = ImmutableList.copyOf(iterable);
    return list.isEmpty() ? null : list;
  }

}
