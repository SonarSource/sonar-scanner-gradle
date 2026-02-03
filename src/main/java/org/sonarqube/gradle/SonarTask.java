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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.file.Directory;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.VisibleForTesting;
import org.sonarqube.gradle.properties.SonarProperty;
import org.sonarsource.scanner.lib.ScannerEngineBootstrapResult;
import org.sonarsource.scanner.lib.ScannerEngineBootstrapper;
import org.sonarsource.scanner.lib.ScannerEngineFacade;
import org.sonarsource.scanner.lib.internal.batch.LogOutput;

import static org.sonarqube.gradle.properties.SonarProperty.JAVA_BINARIES;
import static org.sonarqube.gradle.properties.SonarProperty.JAVA_LIBRARIES;
import static org.sonarqube.gradle.properties.SonarProperty.JAVA_TEST_LIBRARIES;
import static org.sonarqube.gradle.properties.SonarProperty.LIBRARIES;
import static org.sonarqube.gradle.properties.SonarProperty.VERBOSE;

/**
 * Analyses one or more projects with the <a href="http://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle">SonarQube Scanner</a>.
 * Can be used with or without the {@code "sonar-gradle"} plugin.
 * If used together with the plugin, {@code properties} will be populated with defaults based on Gradle's object model and user-defined
 * values configured via {@link SonarExtension}.
 * If used without the plugin, all properties have to be configured manually.
 * For more information on how to configure the SonarQube Scanner, and on which properties are available, see the
 * <a href="http://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle">SonarQube Scanner documentation</a>.
 */
public class SonarTask extends ConventionTask {

  private static final Logger LOGGER = Logging.getLogger(SonarTask.class);
  private static final Pattern TEST_RESULT_FILE_PATTERN = Pattern.compile("TESTS?-.*\\.xml");

  private LogOutput logOutput = new DefaultLogOutput();

  private Provider<Map<String, String>> properties;
  private Provider<Set<String>> userDefinedKeys;
  private Provider<Directory> buildSonar;

  private static class DefaultLogOutput implements LogOutput {
    @Override
    public void log(String formattedMessage, Level level) {
      switch (level) {
        case TRACE:
          LOGGER.trace(formattedMessage);
          return;
        case DEBUG:
          LOGGER.debug(formattedMessage);
          return;
        case INFO:
          LOGGER.info(formattedMessage);
          return;
        case WARN:
          LOGGER.warn(formattedMessage);
          return;
        case ERROR:
          LOGGER.error(formattedMessage);
          return;
        default:
          throw new IllegalArgumentException(level.name());
      }
    }
  }

  @Inject
  public SonarTask(){
    super();
    // some input are annotated with internal, thus grade cannot correctly compute if the task is up to date or not
    this.getOutputs().upToDateWhen(task -> false);
  }

  /**
   * Logs output from the given {@link Level} at the {@link LogLevel#LIFECYCLE} log level, which is the default log
   * level for Gradle tasks. This can be used to specify the level of Sonar Scanner which it output during standard
   * task execution, without needing to override the log level for the full Gradle execution.
   */
  private static class LifecycleLogOutput implements LogOutput {

    private final Level logLevel;

    public LifecycleLogOutput(Level logLevel) {
      this.logLevel = logLevel;
    }

    @Override
    public void log(String formattedMessage, Level level) {
      if (level.ordinal() <= logLevel.ordinal()) {
        LOGGER.lifecycle(formattedMessage);
      }
    }
  }

  @TaskAction
  public void run() {
    logEnvironmentInformation();

    if (SonarExtension.SONAR_DEPRECATED_TASK_NAME.equals(this.getName())) {
      LOGGER.warn("Task 'sonarqube' is deprecated. Use 'sonar' instead.");
    }

    Map<String, String> mapProperties = getProperties().get();
    if (mapProperties.isEmpty()) {
      LOGGER.warn("Skipping Sonar analysis: no properties configured, was it skipped in all projects?");
      return;
    }

    if (LOGGER.isDebugEnabled()) {
      mapProperties = new HashMap<>(mapProperties);
      mapProperties.put(VERBOSE, "true");
      mapProperties = Collections.unmodifiableMap(mapProperties);
    }

    if (isSkippedWithProperty(mapProperties)) {
      return;
    }

    mapProperties = resolveJavaLibraries(mapProperties);
    Set<String> userDefined = userDefinedKeys.get();
    filterPathProperties(mapProperties, userDefined);

    ScannerEngineBootstrapper scanner = ScannerEngineBootstrapper
      .create("ScannerGradle", getPluginVersion() + "/" + GradleVersion.current())
      .addBootstrapProperties(mapProperties);
    try (ScannerEngineBootstrapResult boostrapping = scanner.bootstrap()) {
      // implement behavior according to SCANJLIB-169
      if (!boostrapping.isSuccessful()) {
        throw new AnalysisException("The scanner boostrapping has failed! See the logs for more details.");
      }
      try (ScannerEngineFacade engineFacade = boostrapping.getEngineFacade()) {
        boolean analysisIsSuccessful = engineFacade.analyze(new HashMap<>());
        if (!analysisIsSuccessful) {
          throw new AnalysisException("The analysis has failed! See the logs for more details.");
        }
      }
    } catch (AnalysisException e) {
      throw e;
    } catch (Exception e) {
      throw new AnalysisException(e);
    }
  }

  private static void logEnvironmentInformation() {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info("org.sonarqube Gradle plugin {}", getPluginVersion());
      LOGGER.info("Java {} {} ({}-bit)",
        System.getProperty("java.version"),
        System.getProperty("java.vm.vendor"),
        System.getProperty("sun.arch.data.model"));
      LOGGER.info("{} {} ({})",
        System.getProperty("os.name"),
        System.getProperty("os.version"),
        System.getProperty("os.arch"));
      String gradleOptions = System.getenv("GRADLE_OPTS");
      if (gradleOptions != null) {
        LOGGER.info("GRADLE_OPTS={}", gradleOptions);
      }
    }
  }

  private static String getPluginVersion() {
    InputStream inputStream = SonarTask.class.getResourceAsStream("/org/sonarqube/gradle/sonarqube-gradle-plugin-version.txt");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      return reader.readLine();
    } catch (IOException e) {
      LOGGER.warn("Failed to find the version of the plugin", e);
    }
    return "";
  }

  private static boolean isSkippedWithProperty(Map<String, String> properties) {
    if ("true".equalsIgnoreCase(properties.getOrDefault(SonarProperty.SKIP, "false"))) {
      LOGGER.warn("Sonar Scanner analysis skipped");
      return true;
    }
    return false;
  }

  /**
   * @return The String key/value pairs to be passed to the SonarQube Scanner.
   * {@code null} values are not permitted.
   */
  @Input
  public Provider<Map<String, String>> getProperties() {
    return properties;
  }

  private List<File> resolverFiles;

  @InputFiles
  public List<File> getResolverFiles() {
    return resolverFiles;
  }

  public void setResolverFiles(List<File> resolverFiles) {
    this.resolverFiles = resolverFiles;
  }

  /**
   * @return folder containing all files generated by the analysis
   * {@code null} values are not permitted.
   */
  @OutputDirectory
  public Provider<Directory> getBuildSonar() {
    return this.buildSonar;
  }

  public void setBuildSonar(Provider<Directory> buildSonar) {
    this.buildSonar = buildSonar;
  }

  /**
   * Finish the configuration of `sonar.java.libraries` and `sonar.java.test.libraries` by resolving the class paths that
   * were attached to the task at configuration time.
   * The analysis parameters are added to a copy of the properties given as input.
   */
  Map<String, String> resolveJavaLibraries(Map<String, String> properties) {

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Resolving classpath entries");
    }


    final Map<String, String> result = new HashMap<>(properties);

    LOGGER.info("About to look at resolver files: {}", getResolverFiles());
    for (File resolverFile : getResolverFiles()) {
      processResolverFile(resolverFile, result);
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Finished resolving classpath entries");
    }

    return result;
  }

  /**
   * Reads class path information produced as output of {@link SonarResolverTask}, regenerates related
   * sonar.java.libraries and sonar.java.test.libraries and stores them into the result map.
   */
  @VisibleForTesting
  static void processResolverFile(File resolverFile, Map<String, String> result) {
    LOGGER.info("Looking at file: {}", resolverFile);
    try {
      var prop = ResolutionSerializer.read(resolverFile);
      if(prop.isEmpty()){
        return;
      }
      ProjectProperties resolvedProperties = prop.get();
      List<File> libraries = resolvedProperties.compileClasspath.stream().map(File::new).collect(Collectors.toList());

      // Add mainLibraries if present (for Android projects)
      if (resolvedProperties.mainLibraries != null && !resolvedProperties.mainLibraries.isEmpty()) {
        List<File> mainLibraries = resolvedProperties.mainLibraries.stream().map(File::new).collect(Collectors.toList());
        libraries.addAll(mainLibraries);
      }

      resolveSonarJavaLibraries(resolvedProperties, libraries, result);

      List<File> testLibraries = resolvedProperties.testCompileClasspath.stream().map(File::new).collect(Collectors.toList());

      // Add testLibraries if present (for Android projects)
      if (resolvedProperties.testLibraries != null && !resolvedProperties.testLibraries.isEmpty()) {
        List<File> testLibs = resolvedProperties.testLibraries.stream().map(File::new).collect(Collectors.toList());
        testLibraries.addAll(testLibs);
      }

      resolveSonarJavaTestLibraries(resolvedProperties, testLibraries, result);
    } catch (IOException e) {
      LOGGER.warn("Could not read from resolver file {}", resolverFile, e);
    }
  }

  /**
   * Post-process the sonar properties to prepare them for analysis.
   * You should not filter properties inside Provider, as you do not have any guarantees about when they will be executed.
   * It could be that files haven't been generated yet.
   * <p>
   * Remove file and directories that are not present on the file system.
   * <p>
   * Note: User-defined properties (those explicitly set via sonarqube {} DSL or system/env properties) are not filtered,
   * as users may legitimately reference paths that don't exist yet or use wildcards/placeholders.
   */
  static void filterPathProperties(Map<String, String> properties, Set<String> userDefinedKeys) {
    Set<String> sourcePropNames = Set.of(
      SonarProperty.PROJECT_SOURCE_DIRS,
      SonarProperty.PROJECT_TEST_DIRS,
      SonarProperty.JAVA_BINARIES,
      SonarProperty.JAVA_LIBRARIES,
      SonarProperty.JAVA_TEST_BINARIES,
      SonarProperty.JAVA_TEST_LIBRARIES,
      SonarProperty.LIBRARIES,
      SonarProperty.GROOVY_BINARIES,
      SonarProperty.BINARIES);

    List<PropertyInfo> sourcesProperties = parsePropertiesWithNames(properties, sourcePropNames);


    // Filter non-existing paths and remove empty source properties.
    for (PropertyInfo prop : sourcesProperties) {
      // Skip filtering for user-defined properties.
      if (userDefinedKeys.contains(prop.fullName)) {
        continue;
      }

      properties.computeIfPresent(prop.fullName, (k, commaList) -> {
        var filtered = filterPaths(commaList, Files::exists);
        // empty assignments for `sonar.sources` and `sonar.tests` are required,
        // because modules with no `sonar.sources` or `sonar.tests` value inherit the value from their parent module.
        // This can eventually lead to a double indexing issue in the scanner-engine.
        if (filtered.isEmpty() && !SonarProperty.PROJECT_SOURCE_DIRS.equals(prop.property.getProperty())
          && !SonarProperty.PROJECT_TEST_DIRS.equals(prop.property.getProperty())) {
          return null;
        }

        return filtered;
      });
    }


    Set<String> junitReportNames = Set.of(
      SonarProperty.JUNIT_REPORT_PATHS,
      SonarProperty.SUREFIRE_REPORTS_PATH,
      SonarProperty.JUNIT_REPORTS_PATH
    );
    List<PropertyInfo> junitReportProperties = parsePropertiesWithNames(properties, junitReportNames);

    // filter report paths if directory do not exist or do not contain reports, otherwise Sonar will emit a warning
    for (PropertyInfo prop : junitReportProperties) {
      properties.computeIfPresent(prop.fullName, (k, commaList) -> {
        var filtered = filterPaths(commaList, SonarTask::containJunitReport);
        return filtered.isEmpty() ? null : filtered;
      });
    }

    // remove xml report if directory do not exist
    List<PropertyInfo> xmlReportProperties = parsePropertiesWithNames(properties, Set.of(SonarProperty.JACOCO_XML_REPORT_PATHS));
    for (PropertyInfo prop : xmlReportProperties) {
      properties.computeIfPresent(prop.fullName, (k, commaList) -> {
        var filtered = filterPaths(commaList, Files::exists);
        return filtered.isEmpty() ? null : filtered;
      });
    }
  }

  private static List<PropertyInfo> parsePropertiesWithNames(Map<String, String> properties, Set<String> sonarNames) {
    List<PropertyInfo> parsedProperties = new ArrayList<>();
    for (String propName : properties.keySet()) {
      var parsed = SonarProperty.parse(propName);
      parsed
        .filter(p -> sonarNames.contains(p.getProperty()))
        .ifPresent(property -> parsedProperties.add(new PropertyInfo(property, propName)));
    }
    return parsedProperties;
  }

  /**
   * @param value  a comma-delimited list of paths
   * @param filter predicated to filter the paths
   * @return filtered comma-delimited list of paths
   */
  private static String filterPaths(String value, Predicate<Path> filter) {
    // some of the analyzer accept and expand path containing wildcards
    // we must not filter them
    Set<String> wildcardsToken = Set.of("*", "?", "${");
    return Arrays.stream(value.split(","))
      .filter(p -> wildcardsToken.stream().anyMatch(p::contains) || filter.test(Path.of(p)))
      .collect(Collectors.joining(","));
  }

  /**
   * A simple data holder class that associates a {@link SonarProperty} with its full property name.
   */
  private static class PropertyInfo {
    final SonarProperty property;
    final String fullName;

    public PropertyInfo(SonarProperty property, String fullName) {
      this.property = property;
      this.fullName = fullName;
    }
  }

  private static boolean containJunitReport(Path p) {
    var children = p.toFile().list();
    if (children != null) {
      return Arrays.stream(children).anyMatch(file -> TEST_RESULT_FILE_PATTERN.matcher(file).matches());
    } else {
      return false;
    }
  }

  /**
   * Complete the resolution of <pre>sonar.java.libraries</pre> (and legacy <pre>sonar.binaries</pre>) by combining:
   * <ol>
   *   <li>the existing property</li>
   *   <li>the resolution of the file collection provided at configuration time</li>
   * </ol>
   * The end result is stored in the map passed as input.
   */
  static void resolveSonarJavaLibraries(ProjectProperties projectProperties, @Nullable Iterable<File> mainClassPath, Map<String, String> properties) {
    boolean isTopLevelProject = projectProperties.isRootProject;
    if (LOGGER.isDebugEnabled()) {
      if (isTopLevelProject) {
        LOGGER.debug("Resolving main class path for top-level project.");
      } else {
        LOGGER.debug("Resolving main class path for {}.", projectProperties.projectName);
      }
    }
    if (mainClassPath == null) {
      LOGGER.debug("No main class path configured. Skipping resolution.");
      return;
    }

    List<File> resolvedLibraries = SonarUtils.exists(mainClassPath);
    String resolvedAsAString = resolvedLibraries.stream()
      .filter(File::exists)
      .map(File::getAbsolutePath)
      .collect(Collectors.joining(","));

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Resolved configured main class path as: {}", resolvedAsAString);
    }

    String propertyKey = isTopLevelProject
      ? JAVA_LIBRARIES
      : (projectProperties.projectName + "." + JAVA_LIBRARIES);
    String legacyPropertyKey = isTopLevelProject
      ? LIBRARIES
      : (projectProperties.projectName + "." + LIBRARIES);

    String libraries = properties.getOrDefault(propertyKey, "");
    if (libraries.isEmpty()) {
      libraries = resolvedAsAString;
    } else {
      libraries += "," + resolvedAsAString;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Resolved {} as: {}", propertyKey, libraries);
    }

    properties.put(propertyKey, libraries);
    properties.put(legacyPropertyKey, libraries);
  }

  /**
   * Complete the resolution of <pre>sonar.java.test.libraries</pre> by combining:
   * <ol>
   *   <li><pre>sonar.java.binaries</pre></li>
   *   <li>the existing property</li>
   *   <li>the resolution of the file collection provided at configuration time</li>
   * </ol>
   * The end result is stored in the map passed as input.
   */
  static void resolveSonarJavaTestLibraries(ProjectProperties projectProperties, @Nullable Iterable<File> testClassPath, Map<String, String> properties) {
    boolean isTopLevelProject = projectProperties.isRootProject;
    if (LOGGER.isDebugEnabled()) {
      if (isTopLevelProject) {
        LOGGER.debug("Resolving test class path for top-level project.");
      } else {
        LOGGER.debug("Resolving test class path for {}.", projectProperties.projectName);
      }
    }

    if (testClassPath == null) {
      LOGGER.debug("No test class path configured. Skipping resolution.");
      return;
    }

    List<File> resolvedLibraries = SonarUtils.exists(testClassPath);
    String resolvedAsAString = resolvedLibraries.stream()
      .filter(File::exists)
      .map(File::getAbsolutePath)
      .collect(Collectors.joining(","));

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Resolved configured test class path as: {}", resolvedAsAString);
    }

    // Prepend sonar.java.binaries if it exists
    String binariesPropertyKey = isTopLevelProject
      ? JAVA_BINARIES
      : (projectProperties.projectName + "." + JAVA_BINARIES);
    String libraries = properties.getOrDefault(binariesPropertyKey, "");

    // Add existing test libraries if they exist
    String propertyKey = isTopLevelProject
      ? JAVA_TEST_LIBRARIES
      : (projectProperties.projectName + "." + JAVA_TEST_LIBRARIES);

    // Append resolved libraries
    if (properties.containsKey(propertyKey)) {
      if (libraries.isEmpty()) {
        libraries = properties.get(propertyKey);
      } else {
        libraries += "," + properties.get(propertyKey);
      }
    }

    // Append libraries resolved at configuration time
    if (libraries.isEmpty()) {
      libraries = resolvedAsAString;
    } else {
      libraries = SonarUtils.joinCsvStringsWithoutDuplicates(libraries, resolvedAsAString);
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Resolved {} as: {}", propertyKey, libraries);
    }

    properties.put(propertyKey, libraries);
  }


  void setProperties(Provider<Map<String, String>> properties, Provider<Set<String>> userDefinedKeys) {
    this.properties = properties;
    this.userDefinedKeys = userDefinedKeys;
  }

  /**
   * Sets the {@link LogLevel} to use during Scanner execution. All logged messages from the Scanner at this level or
   * greater will be printed at the {@link LogLevel#LIFECYCLE} level, which is the default level for Gradle tasks. This
   * can be used to specify the level of Sonar Scanner which it output during standard task execution, without needing
   * to override the log level for the full Gradle execution.
   * <p>
   * This overrides the default {@link LogOutput} functionality, which passes logs through to the Gradle logger without
   * modifying the log level.
   *
   * @param logLevel the minimum log level to include in {@link LogLevel#LIFECYCLE} logs
   */
  public void useLoggerLevel(LogLevel logLevel) {
    LogOutput.Level internalLevel = LogOutput.Level.valueOf(logLevel.name());
    this.logOutput = new LifecycleLogOutput(internalLevel);
  }

  /**
   * @return The {@link LogOutput} object to use during Scanner execution. All logged messages from the Scanner will
   * pass through this object. If needed, a custom implementation can be used to handle logged output, such as printing
   * {@link LogLevel#INFO}-level log output when Gradle is only configured at the {@link LogLevel#LIFECYCLE} level.
   */
  @Internal
  public LogOutput getLogOutput() {
    return this.logOutput;
  }

  public void setLogOutput(LogOutput logOutput) {
    this.logOutput = logOutput;
  }
}
