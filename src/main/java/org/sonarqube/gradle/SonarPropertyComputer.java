/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2023 SonarSource
 * mailto:info AT sonarsource DOT com
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

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.gradle.util.GradleVersion;
import org.sonarsource.scanner.api.Utils;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.sonarqube.gradle.SonarUtils.*;

public class SonarPropertyComputer {
  private static final Logger LOGGER = Logging.getLogger(SonarPropertyComputer.class);
  private static final Pattern TEST_RESULT_FILE_PATTERN = Pattern.compile("TESTS?-.*\\.xml");

  static final String SONAR_SOURCES_PROP = "sonar.sources";
  static final String SONAR_TESTS_PROP = "sonar.tests";
  private static final String MAIN_SOURCE_SET_SUFFIX = "main";
  private static final String TEST_SOURCE_SET_SUFFIX = "test";

  private final Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap;
  private final Project targetProject;

  public SonarPropertyComputer(Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap, Project targetProject) {
    this.actionBroadcastMap = actionBroadcastMap;
    this.targetProject = targetProject;
  }

  public Map<String, Object> computeSonarProperties() {
    Map<String, Object> properties = new LinkedHashMap<>();
    computeSonarProperties(targetProject, properties, "");
    if (properties.containsKey("sonar.projectBaseDir")) {
      properties.put("sonar.projectBaseDir", SonarUtils.findProjectBaseDir(properties));
    }
    return properties;
  }

  private void computeSonarProperties(Project project, Map<String, Object> properties, String prefix) {
    if (!SonarQubePlugin.notSkipped(project)) {
      return;
    }

    Map<String, Object> rawProperties = new LinkedHashMap<>();
    addGradleDefaults(project, rawProperties);
    if (isAndroidProject(project)) {
      AndroidUtils.configureForAndroid(project, SonarQubePlugin.getConfiguredAndroidVariant(project), rawProperties);
    }

    ActionBroadcast<SonarProperties> actionBroadcast = actionBroadcastMap.get(project.getPath());
    if (actionBroadcast != null) {
      evaluateSonarPropertiesBlocks(actionBroadcast, rawProperties);
    }
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
      .filter(SonarQubePlugin::notSkipped)
      .collect(Collectors.toList());

    List<Project> skippedChildProjects = project.getChildProjects().values().stream()
      .filter(p -> !SonarQubePlugin.notSkipped(p))
      .collect(Collectors.toList());

    if (!skippedChildProjects.isEmpty()) {
      LOGGER.debug("Skipping collecting Sonar properties on: " + Arrays.toString(skippedChildProjects.toArray(new Project[0])));
    }

    if (enabledChildProjects.isEmpty()) {
      return;
    }

    List<String> moduleIds = new ArrayList<>();

    for (Project childProject : enabledChildProjects) {
      String moduleId = childProject.getPath();
      moduleIds.add(moduleId);
      String modulePrefix = (prefix.length() > 0) ? (prefix + "." + moduleId) : moduleId;
      computeSonarProperties(childProject, properties, modulePrefix);
    }
    properties.put(convertKey("sonar.modules", prefix), String.join(",", moduleIds));
  }

  private static void evaluateSonarPropertiesBlocks(ActionBroadcast<? super SonarProperties> propertiesActions, Map<String, Object> properties) {
    SonarProperties sqProperties = new SonarProperties(properties);
    propertiesActions.execute(sqProperties);
  }

  private static void convertProperties(Map<String, Object> rawProperties, final String projectPrefix, final Map<String, Object> properties) {
    for (Map.Entry<String, Object> entry : rawProperties.entrySet()) {
      String value = convertValue(entry.getValue(), false);
      if (value != null) {
        properties.put(convertKey(entry.getKey(), projectPrefix), value);
      }
    }
  }

  private static String convertKey(String key, final String projectPrefix) {
    return projectPrefix.isEmpty() ? key : (projectPrefix + "." + key);
  }

  private static String convertValue(@Nullable Object value, boolean escapeFilePath) {
    if (value == null) {
      return null;
    }
    if (value instanceof Iterable<?>) {
      String joined = StreamSupport.stream(((Iterable<Object>) value).spliterator(), false)
        .map(v -> SonarPropertyComputer.convertValue(v, true))
        .filter(Objects::nonNull)
        .collect(Collectors.joining(","));
      return joined.isEmpty() ? null : joined;
    } else {
      if (value instanceof File && escapeFilePath) {
        return getEscapedFilePath((File) value);
      }
      return value.toString();
    }
  }

  private static String getEscapedFilePath(File file) {
    String filePath = file.toString();
    if (filePath.contains(",")) {
      return "\"" + filePath.replace("\"", "\\\"") + "\"";
    }
    return filePath;
  }

  private static void configureSourceEncoding(Project project, final Map<String, Object> properties) {
    project.getTasks().withType(JavaCompile.class, compile -> {
      String encoding = compile.getOptions().getEncoding();
      if (encoding != null) {
        properties.put("sonar.sourceEncoding", encoding);
      }
    });
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

  private static void configureForJava(final Project project, final Map<String, Object> properties) {
    project.getPlugins().withType(JavaBasePlugin.class, javaBasePlugin -> populateJdkProperties(project, properties));

    project.getPlugins()
      .withType(JavaPlugin.class, javaPlugin -> configureSourceDirsAndJavaClasspath(project, properties, false));
  }

  private static void configureForKotlin(Project project, Map<String, Object> properties, Object kotlinProjectExtension) {
    Collection<File> sourceDirectories = getKotlinSourceFiles(kotlinProjectExtension, MAIN_SOURCE_SET_SUFFIX);
    if (sourceDirectories != null) {
      SonarUtils.appendProps(properties, SONAR_SOURCES_PROP, sourceDirectories);
    }

    Collection<File> testDirectories = getKotlinSourceFiles(kotlinProjectExtension, TEST_SOURCE_SET_SUFFIX);
    if (testDirectories != null) {
      SonarUtils.appendProps(properties, SONAR_TESTS_PROP, testDirectories);
    }

    if (sourceDirectories != null || testDirectories != null) {
      configureSourceEncoding(project, properties);
      extractTestProperties(project, properties, false);
    }

    // TODO: replace with `(JavaPluginExtension) project.getExtensions().findByName("java");` once we drop support for Gradle < 7.1.0
    project.getPlugins()
      .withType(JavaPlugin.class, javaPlugin -> configureJavaClasspath(project, properties, false));
  }

  /**
   * Groovy projects support joint compilation of a mix of Java and Groovy classes. That's why we set both
   * sonar.java.* and sonar.groovy.* properties.
   */
  private static void configureForGroovy(final Project project, final Map<String, Object> properties) {
    project.getPlugins().withType(GroovyBasePlugin.class, groovyBasePlugin -> populateJdkProperties(project, properties));

    project.getPlugins()
      .withType(GroovyPlugin.class, groovyPlugin -> configureSourceDirsAndJavaClasspath(project, properties, true));
  }

  private static void populateJdkProperties(final Project project, final Map<String, Object> properties) {
    JavaCompilerUtils.extractJavaCompilerConfigurationFromCompileTasks(project).ifPresent(
      config -> SonarUtils.populateJdkProperties(properties, config));
  }

  private static void extractTestProperties(Project project, Map<String, Object> properties, boolean addForGroovy) {
    Task testTask = project.getTasks().findByName(JavaPlugin.TEST_TASK_NAME);
    if (testTask instanceof Test) {
      configureTestReports((Test) testTask, properties);
      configureJaCoCoCoverageReport((Test) testTask, project, properties, addForGroovy);
    }
  }

  private static void configureJaCoCoCoverageReport(final Test testTask, Project project, final Map<String, Object> properties, final boolean addForGroovy) {
    project.getTasks().withType(JacocoReport.class, jacocoReportTask -> {
      SingleFileReport xmlReport = jacocoReportTask.getReports().getXml();
      File reportDestination = getDestination(xmlReport);
      if (isReportEnabled(xmlReport) && reportDestination != null && reportDestination.exists()) {
        appendProp(properties, "sonar.coverage.jacoco.xmlReportPaths", reportDestination);
      } else {
        LOGGER.info("JaCoCo report task detected, but XML report is not enabled or it was not produced. " +
          "Coverage for this task will not be reported.");
      }
    });
    // for backward compatibility we are also setting properties used by SonarJava's JaCoCo sensor
    project.getPlugins().withType(JacocoPlugin.class, jacocoPlugin -> {
      JacocoTaskExtension jacocoTaskExtension = testTask.getExtensions().getByType(JacocoTaskExtension.class);
      File destinationFile = jacocoTaskExtension.getDestinationFile();
      if (destinationFile != null && destinationFile.exists()) {
        properties.put("sonar.jacoco.reportPath", destinationFile);
        appendProp(properties, "sonar.jacoco.reportPaths", destinationFile);
        if (addForGroovy) {
          properties.put("sonar.groovy.jacoco.reportPath", destinationFile);
        }
      }
    });
  }

  private static void configureTestReports(Test testTask, Map<String, Object> properties) {
    File testResultsDir = getDestination(testTask.getReports().getJunitXml());

    // do not set a custom test reports path if it does not exists, otherwise Sonar will emit an error
    // do not set a custom test reports path if there are no files, otherwise Sonar will emit a warning
    if (testResultsDir != null && testResultsDir.isDirectory()
      && Arrays.stream(testResultsDir.list()).anyMatch(file -> TEST_RESULT_FILE_PATTERN.matcher(file).matches())) {
      appendProp(properties, "sonar.junit.reportPaths", testResultsDir);
      // For backward compatibility
      appendProp(properties, "sonar.junit.reportsPath", testResultsDir);
      appendProp(properties, "sonar.surefire.reportsPath", testResultsDir);
    }
  }

  private static void configureSourceDirsAndJavaClasspath(Project project, Map<String, Object> properties, boolean addForGroovy) {
    JavaPluginConvention javaPluginConvention = new DslObject(project).getConvention().getPlugin(JavaPluginConvention.class);

    SourceSet main = javaPluginConvention.getSourceSets().getAt("main");
    Collection<File> sourceDirectories = getJavaSourceFiles(main);
    if (sourceDirectories != null) {
      SonarUtils.appendProps(properties, SONAR_SOURCES_PROP, sourceDirectories);
    }

    SourceSet test = javaPluginConvention.getSourceSets().getAt("test");
    Collection<File> testDirectories = getJavaSourceFiles(test);
    if (testDirectories != null) {
      SonarUtils.appendProps(properties, SONAR_TESTS_PROP, testDirectories);
    }

    if (sourceDirectories != null || testDirectories != null) {
      configureSourceEncoding(project, properties);
      extractTestProperties(project, properties, addForGroovy);
    }

    configureJavaClasspath(project, properties, addForGroovy);
  }

  private static void configureJavaClasspath(Project project, Map<String, Object> properties, boolean addForGroovy) {
    JavaPluginConvention javaPluginConvention = new DslObject(project).getConvention().getPlugin(JavaPluginConvention.class);

    SourceSet main = javaPluginConvention.getSourceSets().getAt("main");
    Collection<File> mainClassDirs = getJavaOutputDirs(main);
    Collection<File> mainLibraries = getJavaLibraries(main);
    setMainClasspathProps(properties, mainClassDirs, mainLibraries, addForGroovy);

    SourceSet test = javaPluginConvention.getSourceSets().getAt("test");
    Collection<File> testClassDirs = getJavaOutputDirs(test);
    Collection<File> testLibraries = getJavaLibraries(test);
    setTestClasspathProps(properties, testClassDirs, testLibraries);
  }

  private static @Nullable Collection<File> getJavaSourceFiles(SourceSet sourceSet) {
    List<File> sourceDirectories = sourceSet.getAllJava().getSrcDirs()
      .stream()
      .filter(File::exists)
      .collect(Collectors.toList());

    return nonEmptyOrNull(sourceDirectories);
  }

  private static Collection<File> getJavaOutputDirs(SourceSet sourceSet) {
    return exists(sourceSet.getOutput().getClassesDirs().getFiles());
  }

  private static @Nullable Collection<File> getKotlinSourceFiles(Object extension, String sourceSetNameSuffix) {
    try {
      Method getSourceSetsMethod = extension.getClass().getMethod("getSourceSets");
      NamedDomainObjectContainer<?> sourceSets = (NamedDomainObjectContainer) getSourceSetsMethod.invoke(extension);
      Collection<File> sourceFiles = sourceSets.stream()
              .map(InternalKotlinSourceSet::of)
              .filter(s -> s.name.toLowerCase(Locale.ROOT).endsWith(sourceSetNameSuffix))
              .flatMap(s -> s.srcDirs.stream())
              .filter(File::exists)
              .collect(Collectors.toList());
      return nonEmptyOrNull(sourceFiles);
    } catch (Exception e) {
      LOGGER.warn("Sonar plugin wasn't able to locate Kotlin source sets. Continue without sources. Root cause: " + e.getMessage());
      return null;
    }
  }

  private static class InternalKotlinSourceSet {
    private String name;
    private Collection<File> srcDirs;

    private InternalKotlinSourceSet() {}

    private static InternalKotlinSourceSet of(Object rawSourceSet) {
      InternalKotlinSourceSet internalKotlinSourceSet = new InternalKotlinSourceSet();

      try {
        Method getName = rawSourceSet.getClass().getMethod("getName");
        internalKotlinSourceSet.name = (String) getName.invoke(rawSourceSet);

        Method getKotlin = rawSourceSet.getClass().getMethod("getKotlin");
        Object kotlin = getKotlin.invoke(rawSourceSet);
        Method getSrcDirs = kotlin.getClass().getMethod("getSrcDirs");
        internalKotlinSourceSet.srcDirs = (Collection<File>) getSrcDirs.invoke(kotlin);

      } catch (Exception e) {
        LOGGER.warn("Sonar plugin wasn't able to locate source set. Root cause: " + e.getMessage());
      }

      return internalKotlinSourceSet;
    }
  }

  private static Collection<File> getJavaLibraries(SourceSet main) {
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

  private void addGradleDefaults(final Project project, final Map<String, Object> properties) {
    properties.put("sonar.projectName", project.getName());
    properties.put("sonar.projectDescription", project.getDescription());
    properties.put("sonar.projectVersion", project.getVersion());
    properties.put("sonar.projectBaseDir", project.getProjectDir());
    properties.put("sonar.kotlin.gradleProjectRoot", project.getRootProject().getProjectDir().getAbsolutePath());

    addKotlinBuildScriptsToSources(project, properties);

    if (project.equals(targetProject)) {
      // Root project of the analysis
      properties.put("sonar.working.directory", new File(project.getBuildDir(), "sonar"));
    }

    Object kotlinExtension = project.getExtensions().findByName("kotlin");
    if (kotlinExtension != null && kotlinExtension.getClass().getName().startsWith("org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension")) {
      configureForKotlin(project, properties, kotlinExtension);
    } else if (project.getPlugins().hasPlugin(GroovyBasePlugin.class)) {
      // Groovy extends the Java plugin, so no need to configure twice
      configureForGroovy(project, properties);
    } else {
      configureForJava(project, properties);
    }
  }

  private static void addKotlinBuildScriptsToSources(Project project, Map<String, Object> properties) {
    List<File> buildScripts = project.getAllprojects().stream()
            .map(Project::getBuildFile)
            .filter(file -> file.getAbsolutePath().endsWith("kts"))
            .collect(Collectors.toList());

    var settingsFile = Path.of(project.getProjectDir().getAbsolutePath(), "settings.gradle.kts").toFile();
    if (settingsFile.exists()) buildScripts.add(settingsFile);

    if (!buildScripts.isEmpty()) SonarUtils.appendProps(properties, SONAR_SOURCES_PROP, buildScripts);
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

  private static boolean isReportEnabled(Report report) {
    try {
      if (GradleVersion.version("7.0").compareTo(GradleVersion.current()) <= 0) {
        return report.getRequired().getOrElse(false);
      } else {
        Method isEnabledGradle5 = report.getClass().getMethod("isEnabled");
        return (boolean) isEnabledGradle5.invoke(report);
      }
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalArgumentException("Unable to check if report is enabled.", e);
    }
  }

  @CheckForNull
  private static File getDestination(Report report) {
    try {
      if (GradleVersion.version("7.0").compareTo(GradleVersion.current()) <= 0) {
        return getDestinationNewApi(report);
      } else {
        return getDestinationOldApi(report);
      }
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalArgumentException("Unable to check the destination of the report.", e);
    }
  }

  /**
   * For Gradle 7+
   */
  private static File getDestinationNewApi(Report report) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Provider<? extends FileSystemLocation> provider;
    if (GradleVersion.version("8.0").compareTo(GradleVersion.current()) <= 0) {
      Method getOutputLocationGradle8 = report.getClass().getMethod("getOutputLocation");
      provider = (Property<? extends FileSystemLocation>) getOutputLocationGradle8.invoke(report, new Object[0]);
    } else {
      provider = report.getOutputLocation();
    }
    FileSystemLocation location = provider.getOrNull();
    if (location != null) {
      return location.getAsFile();
    }
    return null;
  }

  /**
   * Available in Gradle 5 to Gradle 7
   */
  private static File getDestinationOldApi(Report report) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method getDestinationGradle5 = report.getClass().getMethod("getDestination");
    return (File) getDestinationGradle5.invoke(report);
  }
}
