/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2025 SonarSource
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.gradle.util.GradleVersion;
import org.sonarqube.gradle.SonarUtils.InputFileType;
import org.sonarsource.scanner.lib.EnvironmentConfig;

import static java.util.stream.Collectors.groupingBy;
import static org.sonarqube.gradle.SonarUtils.appendProp;
import static org.sonarqube.gradle.SonarUtils.computeReportPaths;
import static org.sonarqube.gradle.SonarUtils.exists;
import static org.sonarqube.gradle.SonarUtils.findProjectBaseDir;
import static org.sonarqube.gradle.SonarUtils.getSourceSets;
import static org.sonarqube.gradle.SonarUtils.isAndroidProject;
import static org.sonarqube.gradle.SonarUtils.nonEmptyOrNull;
import static org.sonarqube.gradle.SonarUtils.setMainClasspathProps;
import static org.sonarqube.gradle.SonarUtils.setTestClasspathProps;

public class SonarPropertyComputer {
  private static final Logger LOGGER = Logging.getLogger(SonarPropertyComputer.class);
  private static final Pattern TEST_RESULT_FILE_PATTERN = Pattern.compile("TESTS?-.*\\.xml");

  private static final String SONAR_GRADLE_SCAN_ALL = "sonar.gradle.scanAll";
  private static final String MAIN_SOURCE_SET_SUFFIX = "main";
  private static final String TEST_SOURCE_SET_SUFFIX = "test";
  public static final String SONAR_PROJECT_BASE_DIR = "sonar.projectBaseDir";

  private final Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap;
  private final Project targetProject;

  public SonarPropertyComputer(Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap, Project targetProject) {
    this.actionBroadcastMap = actionBroadcastMap;
    this.targetProject = targetProject;
  }

  public Map<String, Object> computeSonarProperties() {
    Map<String, Object> properties = new LinkedHashMap<>();

    computeSonarProperties(targetProject, properties);

    properties.computeIfPresent(SONAR_PROJECT_BASE_DIR, (k, v) -> findProjectBaseDir(properties));

    if (SonarQubePlugin.notSkipped(targetProject)) {
      properties.put("sonar.kotlin.gradleProjectRoot", targetProject.getRootProject().getProjectDir().getAbsolutePath());
    }

    return properties;
  }

  private void computeSonarProperties(Project project, Map<String, Object> properties) {
    computeDefaultProperties(project, properties, "");

    if (shouldApplyScanAll(project, properties)) {
      computeScanAllProperties(project, properties);
    }
  }

  private void computeDefaultProperties(Project project, Map<String, Object> properties, String prefix) {
    if (SonarQubePlugin.isSkipped(project)) {
      return;
    }
    Map<String, Object> rawProperties = new LinkedHashMap<>();

    addGradleDefaults(project, rawProperties);

    if (isAndroidProject(project)) {
      AndroidUtils.configureForAndroid(project, SonarQubePlugin.getConfiguredAndroidVariant(project), rawProperties);
    }

    if (isRootProject(project)) {
      addKotlinBuildScriptsToSources(project, rawProperties);
    }

    overrideWithUserDefinedProperties(project, rawProperties);

    // These empty assignments are required because modules with no `sonar.sources` or `sonar.tests` value inherit the value from their parent module.
    // This can eventually lead to a double indexing issue in the scanner-engine.
    rawProperties.putIfAbsent(ScanProperties.PROJECT_SOURCE_DIRS, "");
    rawProperties.putIfAbsent(ScanProperties.PROJECT_TEST_DIRS, "");

    if (project.equals(targetProject)) {
      rawProperties.putIfAbsent("sonar.projectKey", computeProjectKey());
    } else {
      String projectKey = (String) properties.get("sonar.projectKey");
      rawProperties.putIfAbsent("sonar.moduleKey", projectKey + project.getPath());
    }

    addGithubFolder(project, rawProperties);
    convertProperties(rawProperties, prefix, properties);

    List<Project> enabledChildProjects = project.getChildProjects().values().stream()
      .filter(SonarQubePlugin::notSkipped)
      .collect(Collectors.toList());

    List<Project> skippedChildProjects = project.getChildProjects().values().stream()
      .filter(SonarQubePlugin::isSkipped)
      .collect(Collectors.toList());

    if (!skippedChildProjects.isEmpty()) {
      LOGGER.debug("Skipping collecting Sonar properties on: {}", skippedChildProjects);
    }

    if (enabledChildProjects.isEmpty()) {
      return;
    }

    List<String> moduleIds = new ArrayList<>();

    String toPrefix = prefix.isEmpty() ? "" : (prefix + ".");
    for (Project childProject : enabledChildProjects) {
      String moduleId = childProject.getPath();
      moduleIds.add(moduleId);
      String modulePrefix = toPrefix + moduleId;
      computeDefaultProperties(childProject, properties, modulePrefix);
    }

    properties.put(convertKey("sonar.modules", prefix), String.join(",", moduleIds));
  }

  private boolean shouldApplyScanAll(Project project, Map<String, Object> properties) {
    // when the parent module is skipped, the properties are empty thus the scan all logic is not applied
    var scanAllValue = (String) properties.getOrDefault(SONAR_GRADLE_SCAN_ALL, "false");
    var scanAllEnabled = "true".equalsIgnoreCase(scanAllValue.trim());

    if (scanAllEnabled) {
      LOGGER.info("Parameter sonar.gradle.scanAll is enabled. The scanner will attempt to collect additional sources.");

      // Collecting the properties configured in the Gradle build configuration
      var sonarProps = new SonarProperties(new HashMap<>());
      actionBroadcastMap.get(project.getPath()).execute(sonarProps);

      boolean sourcesOrTestsAlreadySet = Stream.of(System.getProperties().keySet(), EnvironmentConfig.load(System.getenv()).keySet(), sonarProps.getProperties().keySet())
        .flatMap(Collection::stream)
        .map(String.class::cast)
        .anyMatch(k -> ScanProperties.PROJECT_SOURCE_DIRS.endsWith(k) || ScanProperties.PROJECT_TEST_DIRS.endsWith(k));

      if (sourcesOrTestsAlreadySet) {
        LOGGER.warn("Parameter sonar.gradle.scanAll is enabled but the scanner will not collect additional sources because sonar.sources or sonar.tests has been overridden.");
        return false;
      }
    }

    return scanAllEnabled;
  }

  private static void computeScanAllProperties(Project project, Map<String, Object> properties) {
    // Collecting the existing sources from all modules, i.e. 'sonar.sources' and all 'submodule.sonar.sources'
    Set<Path> allModulesExistingSourcesAndTests = properties.entrySet()
      .stream()
      .filter(e -> e.getKey().endsWith(ScanProperties.PROJECT_SOURCE_DIRS) || e.getKey().endsWith(ScanProperties.PROJECT_TEST_DIRS))
      .map(Map.Entry::getValue)
      .map(String.class::cast)
      .map(SonarUtils::splitAsCsv)
      .flatMap(Collection::stream)
      .filter(Predicate.not(String::isBlank))
      .map(Paths::get)
      .collect(Collectors.toSet());

    Set<Path> skippedDirs = skippedProjects(project)
      .map(Project::getProjectDir)
      .map(File::toPath)
      .collect(Collectors.toSet());

    Set<Path> excludedFiles = computeReportPaths(properties);

    SourceCollector visitor = SourceCollector.builder()
      .setRoot(project.getProjectDir().toPath())
      .setExistingSources(allModulesExistingSourcesAndTests)
      .setExcludedFiles(excludedFiles)
      .setDirectoriesToIgnore(skippedDirs)
      .build();


    Path projectDir = project.getProjectDir().toPath();
    try {
      Files.walkFileTree(projectDir, visitor);
    } catch (IOException e) {
      LOGGER.error(String.valueOf(e));
    }

    Map<InputFileType, List<Path>> collectedSourceByType = visitor.getCollectedSources().stream()
      .map(Path::toAbsolutePath)
      .collect(groupingBy(path -> SonarUtils.findProjectFileType(projectDir, path)));

    List<Path> collectedMainSources = collectedSourceByType.getOrDefault(InputFileType.MAIN, List.of());
    appendAdditionalSourceFiles(properties, ScanProperties.PROJECT_SOURCE_DIRS, collectedMainSources);

    List<Path> collectedTestSources = collectedSourceByType.getOrDefault(InputFileType.TEST, List.of());
    appendAdditionalSourceFiles(properties, ScanProperties.PROJECT_TEST_DIRS, collectedTestSources);
  }

  private static void appendAdditionalSourceFiles(Map<String, Object> properties, String sourcePropertyToUpdate, List<Path> collectedSources) {
    String existingValue = (String) properties.getOrDefault(sourcePropertyToUpdate, "");
    Set<Path> existingSources = existingValue.isBlank() ? Collections.emptySet() : SonarUtils.splitAsCsv(existingValue)
      .stream()
      .filter(Predicate.not(String::isBlank))
      .map(Paths::get)
      .collect(Collectors.toSet());

    List<String> mergedSources = Stream.of(existingSources, collectedSources)
      .flatMap(Collection::stream)
      .map(Path::toString)
      .sorted()
      .collect(Collectors.toList());

    properties.put(sourcePropertyToUpdate, SonarUtils.joinAsCsv(mergedSources));
  }

  private void overrideWithUserDefinedProperties(Project project, Map<String, Object> rawProperties) {
    ActionBroadcast<SonarProperties> actionBroadcast = actionBroadcastMap.get(project.getPath());
    if (actionBroadcast != null) {
      evaluateSonarPropertiesBlocks(actionBroadcast, rawProperties);
    }
    if (isRootProject(project)) {
      addEnvironmentProperties(rawProperties);
      addSystemProperties(rawProperties);
    }
  }

  private boolean isRootProject(Project project) {
    return project.equals(targetProject);
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
    for (Map.Entry<String, String> e : EnvironmentConfig.load(System.getenv()).entrySet()) {
      properties.put(e.getKey(), e.getValue());
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
      SonarUtils.appendSourcesProp(properties, sourceDirectories, false);
    }

    Collection<File> testDirectories = getKotlinSourceFiles(kotlinProjectExtension, TEST_SOURCE_SET_SUFFIX);
    if (testDirectories != null) {
      SonarUtils.appendSourcesProp(properties, testDirectories, true);
    }

    if (sourceDirectories != null || testDirectories != null) {
      configureSourceEncoding(project, properties);
      extractTestProperties(project, properties);
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

  private static void extractTestProperties(Project project, Map<String, Object> properties) {
    Task testTask = project.getTasks().findByName(JavaPlugin.TEST_TASK_NAME);
    if (testTask instanceof Test) {
      configureTestReports((Test) testTask, properties);
      configureJaCoCoCoverageReport(project, properties);
    }
  }

  private static void configureJaCoCoCoverageReport(Project project, final Map<String, Object> properties) {
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
    SourceSetContainer sourceSets = getSourceSets(project);

    SourceSet main = sourceSets.getAt("main");
    Collection<File> sourceDirectories = getJavaSourceFiles(main);
    if (sourceDirectories != null) {
      SonarUtils.appendSourcesProp(properties, sourceDirectories, false);
    }

    SourceSet test = sourceSets.getAt("test");
    Collection<File> testDirectories = getJavaSourceFiles(test);
    if (testDirectories != null) {
      SonarUtils.appendSourcesProp(properties, testDirectories, true);
    }

    if (sourceDirectories != null || testDirectories != null) {
      configureSourceEncoding(project, properties);
      extractTestProperties(project, properties);
    }

    configureJavaClasspath(project, properties, addForGroovy);
  }

  private static void configureJavaClasspath(Project project, Map<String, Object> properties, boolean addForGroovy) {
    SourceSetContainer sourceSets = getSourceSets(project);
    SourceSet main = sourceSets.getAt("main");
    Collection<File> mainClassDirs = getJavaOutputDirs(main);
    Collection<File> mainLibraries = getJavaLibraries(main);
    setMainClasspathProps(properties, mainClassDirs, mainLibraries, addForGroovy);

    SourceSet test = sourceSets.getAt("test");
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

    private InternalKotlinSourceSet() {
    }

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
    properties.put(SONAR_PROJECT_BASE_DIR, project.getProjectDir());

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
    Set<Project> skippedProjects = skippedProjects(project).collect(Collectors.toSet());

    List<File> buildScripts = project.getAllprojects()
      .stream()
      .filter(Predicate.not(skippedProjects::contains))
      .map(Project::getBuildFile)
      .filter(file -> file.getAbsolutePath().endsWith("kts"))
      .collect(Collectors.toList());

    var settingsFile = Path.of(project.getProjectDir().getAbsolutePath(), "settings.gradle.kts").toFile();
    if (settingsFile.exists()) {
      buildScripts.add(settingsFile);
    }
    if (!buildScripts.isEmpty()) {
      SonarUtils.appendSourcesProp(properties, buildScripts, false);
    }
  }

  private static void addGithubFolder(Project project, Map<String, Object> properties) {
    File githubFolder = project.getProjectDir().toPath().resolve(".github").toFile();
    if (githubFolder.exists() && githubFolder.isDirectory()) {
      SonarUtils.appendSourcesProp(properties, List.of(githubFolder), false);
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

  /**
   * Returns the stream of projects that are marked as skipped or have an ancestor marked as skipped
   */
  private static Stream<Project> skippedProjects(Project project) {
    Set<Project> projectsMarkedAsSkipped = project.getAllprojects()
      .stream()
      .filter(SonarQubePlugin::isSkipped)
      .collect(Collectors.toSet());

    return project.getAllprojects().stream().filter(p -> hasSkippedAncestor(p, projectsMarkedAsSkipped));
  }

  /**
   * Traverse the project hierarchy to find if the project or any of its ancestors are skipped
   */
  private static boolean hasSkippedAncestor(Project project, Set<Project> skippedProjects) {
    while (project != null) {
      if (skippedProjects.contains(project)) {
        return true;
      }
      project = project.getParent();
    }
    return false;
  }
}
