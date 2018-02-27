/**
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2018 SonarSource
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

import com.android.build.gradle.api.BaseVariant;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;
import org.gradle.util.GradleVersion;
import org.sonarsource.scanner.api.Utils;

import static java.util.Arrays.asList;

/**
 * A plugin for analyzing projects with the <a href="http://redirect.sonarsource.com/doc/analyzing-with-sq-gradle.html">SonarQube Runner</a>.
 * When applied to a project, both the project itself and its subprojects will be analyzed (in a single run).
 */
public class SonarQubePlugin implements Plugin<Project> {

  private static final Logger LOGGER = Logging.getLogger(SonarQubePlugin.class);

  static final String SONAR_SOURCES_PROP = "sonar.sources";
  static final String SONAR_TESTS_PROP = "sonar.tests";
  static final String SONAR_JAVA_SOURCE_PROP = "sonar.java.source";
  static final String SONAR_JAVA_TARGET_PROP = "sonar.java.target";
  private static final Pattern TEST_RESULT_FILE_PATTERN = Pattern.compile("TESTS?-.*\\.xml");
  // Project where sonarqube plugin is apply. Most of the time the root project.
  private Project targetProject;

  private static void evaluateSonarPropertiesBlocks(ActionBroadcast<? super SonarQubeProperties> propertiesActions, Map<String, Object> properties) {
    SonarQubeProperties sqProperties = new SonarQubeProperties(properties);
    propertiesActions.execute(sqProperties);
  }

  private static ActionBroadcast<SonarQubeProperties> addBroadcaster(Map<Project, ActionBroadcast<SonarQubeProperties>> actionBroadcastMap, Project project) {
    ActionBroadcast<SonarQubeProperties> actionBroadcast = new ActionBroadcast<>();
    actionBroadcastMap.put(project, actionBroadcast);
    return actionBroadcast;
  }

  private static boolean addTaskByName(Project p, String name, List<Task> allCompileTasks) {
    try {
      allCompileTasks.add(p.getTasks().getByName(name));
      return true;
    } catch (UnknownTaskException e) {
      return false;
    }
  }

  private static String capitalize(final String word) {
    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
  }

  private static boolean isAndroidProject(Project project) {
    return project.getPlugins().hasPlugin("com.android.application") || project.getPlugins().hasPlugin("com.android.feature") || project.getPlugins().hasPlugin("com.android.library") || project.getPlugins().hasPlugin("com.android.test");
  }

  private static void configureForJava(final Project project, final Map<String, Object> properties) {
    project.getPlugins().withType(JavaBasePlugin.class, javaBasePlugin -> configureJdkSourceAndTarget(project, properties));

    project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
      boolean hasSourceOrTest = configureSourceDirsAndJavaClasspath(project, properties, false);
      if (hasSourceOrTest) {
        configureSourceEncoding(project, properties);
        extractTestProperties(project, properties, false);
      }
    });
  }

  /**
   * Groovy projects support joint compilation of a mix of Java and Groovy classes. That's why we set both
   * sonar.java.* and sonar.groovy.* properties.
   */
  private static void configureForGroovy(final Project project, final Map<String, Object> properties) {
    project.getPlugins().withType(GroovyBasePlugin.class, groovyBasePlugin -> configureJdkSourceAndTarget(project, properties));

    project.getPlugins().withType(GroovyPlugin.class, groovyPlugin -> {
      boolean hasSourceOrTest = configureSourceDirsAndJavaClasspath(project, properties, true);
      if (hasSourceOrTest) {
        configureSourceEncoding(project, properties);
        extractTestProperties(project, properties, true);
      }
    });
  }

  private static void extractTestProperties(Project project, Map<String, Object> properties, boolean addForGroovy) {
    Task testTask = project.getTasks().getByName(JavaPlugin.TEST_TASK_NAME);
    if (testTask instanceof Test) {
      configureTestReports((Test) testTask, properties);
      configureJaCoCoCoverageReport((Test) testTask, addForGroovy, project, properties);
    } else {
      LOGGER.warn("Non standard test task: unable to automatically find test execution and coverage reports paths");
    }
  }

  private static void configureJaCoCoCoverageReport(final Test testTask, final boolean addForGroovy, Project project, final Map<String, Object> properties) {
    project.getPlugins().withType(JacocoPlugin.class, jacocoPlugin -> {
      JacocoTaskExtension jacocoTaskExtension = testTask.getExtensions().getByType(JacocoTaskExtension.class);
      File destinationFile = jacocoTaskExtension.getDestinationFile();
      if (destinationFile.exists()) {
        properties.put("sonar.jacoco.reportPath", destinationFile);
        appendProp(properties, "sonar.jacoco.reportPaths", destinationFile);
        if (addForGroovy) {
          properties.put("sonar.groovy.jacoco.reportPath", destinationFile);
        }
      }
    });
  }

  private static void configureTestReports(Test testTask, Map<String, Object> properties) {
    File testResultsDir = testTask.getReports().getJunitXml().getDestination();

    // do not set a custom test reports path if it does not exists, otherwise SonarQube will emit an error
    // do not set a custom test reports path if there are no files, otherwise SonarQube will emit a warning
    if (testResultsDir.isDirectory()
      && asList(testResultsDir.list()).stream().anyMatch(file -> TEST_RESULT_FILE_PATTERN.matcher(file).matches())) {
      appendProp(properties, "sonar.junit.reportPaths", testResultsDir);
      // For backward compatibility
      appendProp(properties, "sonar.junit.reportsPath", testResultsDir);
      appendProp(properties, "sonar.surefire.reportsPath", testResultsDir);
    }
  }

  private static boolean configureSourceDirsAndJavaClasspath(Project project, Map<String, Object> properties, final boolean addForGroovy) {
    JavaPluginConvention javaPluginConvention = new DslObject(project).getConvention().getPlugin(JavaPluginConvention.class);

    SourceSet main = javaPluginConvention.getSourceSets().getAt("main");
    List<File> sourceDirectories = nonEmptyOrNull(main.getAllJava().getSrcDirs().stream().filter(File::exists).collect(Collectors.toList()));
    properties.put(SONAR_SOURCES_PROP, sourceDirectories);
    SourceSet test = javaPluginConvention.getSourceSets().getAt("test");
    List<File> testDirectories = nonEmptyOrNull(test.getAllJava().getSrcDirs().stream().filter(File::exists).collect(Collectors.toList()));
    properties.put(SONAR_TESTS_PROP, testDirectories);

    Collection<File> mainClassDirs = getOutputDirs(main);
    Collection<File> mainLibraries = getLibraries(main);
    setMainClasspathProps(properties, addForGroovy, mainClassDirs, mainLibraries);

    Collection<File> testClassDirs = getOutputDirs(test);
    Collection<File> testLibraries = getLibraries(test);
    setTestClasspathProps(properties, testClassDirs, testLibraries);

    return sourceDirectories != null || testDirectories != null;
  }

  private static Collection<File> getOutputDirs(SourceSet sourceSet) {
    Collection<File> result;
    if (GradleVersion.version("4.0").compareTo(GradleVersion.current()) <= 0) {
      result = sourceSet.getOutput().getClassesDirs().getFiles();
    } else {
      result = Collections.singletonList(sourceSet.getOutput().getClassesDir());
    }
    return exists(result);
  }

  static void setMainClasspathProps(Map<String, Object> properties, boolean addForGroovy, Collection<File> mainClassDirs, Collection<File> mainLibraries) {
    appendProps(properties, "sonar.java.binaries", exists(mainClassDirs));
    if (addForGroovy) {
      appendProps(properties, "sonar.groovy.binaries", exists(mainClassDirs));
    }
    // Populate deprecated properties for backward compatibility
    appendProps(properties, "sonar.binaries", exists(mainClassDirs));

    appendProps(properties, "sonar.java.libraries", exists(mainLibraries));
    // Populate deprecated properties for backward compatibility
    appendProps(properties, "sonar.libraries", exists(mainLibraries));
  }

  static void appendProps(Map<String, Object> properties, String key, Iterable<?> valuesToAppend) {
    properties.putIfAbsent(key, new LinkedHashSet<String>());
    StreamSupport.stream(valuesToAppend.spliterator(), false)
      .forEach(v -> ((Collection<String>) properties.get(key)).add(v.toString()));
  }

  static void appendProp(Map<String, Object> properties, String key, Object valueToAppend) {
    properties.putIfAbsent(key, new LinkedHashSet<String>());
    ((Collection<String>) properties.get(key)).add(valueToAppend.toString());
  }

  static void setTestClasspathProps(Map<String, Object> properties, Collection<File> testClassDirs, Collection<File> testLibraries) {
    appendProps(properties, "sonar.java.test.binaries", exists(testClassDirs));
    appendProps(properties, "sonar.java.test.libraries", exists(testLibraries));
  }

  private static List<File> exists(Collection<File> files) {
    return files.stream().filter(File::exists).collect(Collectors.toList());
  }

  private static void configureSourceEncoding(Project project, final Map<String, Object> properties) {
    project.getTasks().withType(JavaCompile.class, compile -> {
      String encoding = compile.getOptions().getEncoding();
      if (encoding != null) {
        properties.put("sonar.sourceEncoding", encoding);
      }
    });
  }

  private static void configureJdkSourceAndTarget(Project project, Map<String, Object> properties) {
    JavaPluginConvention javaPluginConvention = new DslObject(project).getConvention().getPlugin(JavaPluginConvention.class);
    properties.put(SONAR_JAVA_SOURCE_PROP, javaPluginConvention.getSourceCompatibility());
    properties.put(SONAR_JAVA_TARGET_PROP, javaPluginConvention.getTargetCompatibility());
  }

  private static void addEnvironmentProperties(Map<String, Object> properties) {
    for (Map.Entry<Object, Object> e : Utils.loadEnvironmentProperties(System.getenv()).entrySet()) {
      properties.put(e.getKey().toString(), e.getValue().toString());
    }
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
    List<File> libraries = exists(main.getCompileClasspath().getFiles());

    File runtimeJar = getRuntimeJar();
    if (runtimeJar != null) {
      libraries.add(runtimeJar);
    }

    File fxRuntimeJar = getFxRuntimeJar();
    if (fxRuntimeJar != null) {
      libraries.add(fxRuntimeJar);
    }

    return libraries;
  }

  private static File getRuntimeJar() {
    try {
      final File javaBase = new File(System.getProperty("java.home")).getCanonicalFile();
      File runtimeJar = new File(javaBase, "lib/rt.jar");
      if (runtimeJar.exists()) {
        return runtimeJar;
      }
      runtimeJar = new File(javaBase, "jre/lib/rt.jar");
      return runtimeJar.exists() ? runtimeJar : null;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static File getFxRuntimeJar() {
    try {
      final File javaBase = new File(System.getProperty("java.home")).getCanonicalFile();
      File runtimeJar = new File(javaBase, "lib/ext/jfxrt.jar");
      if (runtimeJar.exists()) {
        return runtimeJar;
      }
      runtimeJar = new File(javaBase, "jre/lib/ext/jfxrt.jar");
      return runtimeJar.exists() ? runtimeJar : null;
    } catch (IOException e) {
      throw new IllegalStateException(e);
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
        .filter(Objects::nonNull)
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

  private SonarQubeTask createTask(final Project project, final Map<Project, ActionBroadcast<SonarQubeProperties>> actionBroadcastMap) {
    SonarQubeTask sonarQubeTask = project.getTasks().create(SonarQubeExtension.SONARQUBE_TASK_NAME, SonarQubeTask.class);
    sonarQubeTask.setDescription("Analyzes " + project + " and its subprojects with SonarQube.");

    ConventionMapping conventionMapping = new DslObject(sonarQubeTask).getConventionMapping();
    conventionMapping.map("properties", () -> {
      Map<String, Object> properties = new LinkedHashMap<>();
      computeSonarProperties(project, properties, actionBroadcastMap, "");
      return properties;
    });

    Callable<Iterable<? extends Task>> testTask = () -> project.getAllprojects().stream()
      .filter(p -> p.getPlugins().hasPlugin(JavaPlugin.class) && !p.getExtensions().getByType(SonarQubeExtension.class).isSkipProject())
      .map(p -> p.getTasks().getByName(JavaPlugin.TEST_TASK_NAME))
      .collect(Collectors.toList());
    sonarQubeTask.dependsOn(testTask);

    Callable<Iterable<? extends Task>> callable = () -> project.getAllprojects().stream()
      .filter(p -> isAndroidProject(p) && !p.getExtensions().getByType(SonarQubeExtension.class).isSkipProject())
      .map(p -> {
        BaseVariant variant = AndroidUtils.findVariant(p, p.getExtensions().getByType(SonarQubeExtension.class).getAndroidVariant());
        List<Task> allCompileTasks = new ArrayList<>();
        boolean unitTestTaskDepAdded = addTaskByName(p, "compile" + capitalize(variant.getName()) + "UnitTestJavaWithJavac", allCompileTasks);
        boolean androidTestTaskDepAdded = addTaskByName(p, "compile" + capitalize(variant.getName()) + "AndroidTestJavaWithJavac", allCompileTasks);
        // unit test compile and android test compile tasks already depends on main code compile so don't add a useless dependency
        // that would lead to run main compile task several times
        if (!unitTestTaskDepAdded && !androidTestTaskDepAdded) {
          addTaskByName(p, "compile" + capitalize(variant.getName()) + "JavaWithJavac", allCompileTasks);
        }
        return allCompileTasks;
      })
      .flatMap(List::stream)
      .collect(Collectors.toList());
    sonarQubeTask.dependsOn(callable);
    return sonarQubeTask;
  }

  private void computeSonarProperties(Project project, Map<String, Object> properties, Map<Project, ActionBroadcast<SonarQubeProperties>> sonarPropertiesActionBroadcastMap,
    String prefix) {
    SonarQubeExtension extension = project.getExtensions().getByType(SonarQubeExtension.class);
    if (extension.isSkipProject()) {
      return;
    }

    Map<String, Object> rawProperties = new LinkedHashMap<>();
    addGradleDefaults(project, rawProperties);
    if (isAndroidProject(project)) {
      AndroidUtils.configureForAndroid(project, extension.getAndroidVariant(), rawProperties);
    }

    evaluateSonarPropertiesBlocks(sonarPropertiesActionBroadcastMap.get(project), rawProperties);
    if (project.equals(targetProject)) {
      addEnvironmentProperties(rawProperties);
      addSystemProperties(rawProperties);
    }

    rawProperties.putIfAbsent(SONAR_SOURCES_PROP, "");

    if (project.equals(targetProject)) {
      rawProperties.putIfAbsent("sonar.projectKey", computeProjectKey());
    } else {
      String projectKey = (String) properties.get("sonar.projectKey");
      rawProperties.putIfAbsent("sonar.moduleKey", projectKey + project.getPath());
    }

    convertProperties(rawProperties, prefix, properties);

    List<Project> enabledChildProjects = project.getChildProjects().values().stream()
      .filter(p -> !p.getExtensions().getByType(SonarQubeExtension.class).isSkipProject())
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
    properties.put("sonar.projectName", project.getName());
    properties.put("sonar.projectDescription", project.getDescription());
    properties.put("sonar.projectVersion", project.getVersion());
    properties.put("sonar.projectBaseDir", project.getProjectDir());

    if (project.equals(targetProject)) {
      // Root project
      properties.put("sonar.working.directory", new File(project.getBuildDir(), "sonar"));
    }

    if (project.getPlugins().hasPlugin(GroovyBasePlugin.class)) {
      // Groovy extends the Java plugin, so no need to configure twice
      configureForGroovy(project, properties);
    } else {
      configureForJava(project, properties);
    }
  }

  private String computeProjectKey() {
    Project rootProject = targetProject.getRootProject();
    String rootProjectName = rootProject.getName();
    String rootGroup = rootProject.getGroup().toString();
    String rootKey = rootGroup.isEmpty() ? rootProjectName : (rootGroup + ":" + rootProjectName);
    if (targetProject == rootProject) {
      return rootKey;
    }
    return rootKey + targetProject.getPath();
  }

}
