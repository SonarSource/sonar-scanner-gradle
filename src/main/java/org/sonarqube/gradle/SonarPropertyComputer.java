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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.VisibleForTesting;
import org.sonarqube.gradle.SonarUtils.InputFileType;
import org.sonarqube.gradle.properties.SonarProperty;
import org.sonarsource.scanner.lib.EnvironmentConfig;

import static java.util.stream.Collectors.groupingBy;
import static org.sonarqube.gradle.SonarUtils.appendProp;
import static org.sonarqube.gradle.SonarUtils.appendProps;
import static org.sonarqube.gradle.SonarUtils.computeReportPaths;
import static org.sonarqube.gradle.SonarUtils.findProjectBaseDir;
import static org.sonarqube.gradle.SonarUtils.getSourceSets;
import static org.sonarqube.gradle.SonarUtils.isAndroidProject;
import static org.sonarqube.gradle.SonarUtils.nonEmptyOrNull;
import static org.sonarqube.gradle.SonarUtils.setMainBinariesProps;

public class SonarPropertyComputer {
  private static final Logger LOGGER = Logging.getLogger(SonarPropertyComputer.class);

  private static final String SONAR = "sonar";
  private static final String MAIN_SOURCE_SET_SUFFIX = "main";
  private static final String TEST_SOURCE_SET_SUFFIX = "test";
  private static final Set<String> SENSITIVE_PROPERTIES = Set.of(
    "sonar.login", "sonar.password", "sonar.token", "sonar.scanner.proxypassword",
    "sonar.scanner.truststorepassword", "sonar.scanner.keystorepassword");

  private final Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap;
  private final Map<String, AndroidConfig> androidConfigMap;
  private final Project targetProject;

  public SonarPropertyComputer(Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap, Map<String, AndroidConfig> androidConfigMap, Project targetProject) {
    this.actionBroadcastMap = actionBroadcastMap;
    this.androidConfigMap = androidConfigMap;
    this.targetProject = targetProject;
  }

  public ComputedProperties computeSonarProperties() {
    ComputedProperties computedProperties = new ComputedProperties(new LinkedHashMap<>(), new LinkedHashSet<>());

    computeSonarProperties(targetProject, computedProperties);

    computedProperties.properties.computeIfPresent(SonarProperty.PROJECT_BASE_DIR, (k, v) -> findProjectBaseDir(computedProperties.properties));

    if (SonarUtils.notSkipped(targetProject)) {
      computedProperties.properties.put(SonarProperty.KOTLIN_GRADLE_PROJECT_ROOT, targetProject.getRootProject().getProjectDir().getAbsolutePath());
    }

    return computedProperties;
  }

  /** Computes only this project's properties, without reading another Gradle project model. */
  public ComputedProperties computeLocalSonarProperties(boolean analysisRoot) {
    return computeLocalSonarProperties(analysisRoot, false);
  }

  /** Sensitive values stay in the analysis task and are never written to project metadata outputs. */
  public Map<String, String> computeLocalSensitiveProperties() {
    ComputedProperties computed = computeLocalSonarProperties(true, true);
    Map<String, String> sensitive = new LinkedHashMap<>();
    computed.properties.forEach((key, value) -> {
      if (isSensitiveProperty(key)) {
        sensitive.put(key, (String) value);
      }
    });
    return sensitive;
  }

  private ComputedProperties computeLocalSonarProperties(boolean analysisRoot, boolean includeSensitive) {
    ComputedProperties computed = new ComputedProperties(new LinkedHashMap<>(), new LinkedHashSet<>());
    if (SonarUtils.isSkipped(targetProject)) {
      return computed;
    }
    Map<String, Object> raw = new LinkedHashMap<>();
    Set<String> userDefined = new LinkedHashSet<>();
    addGradleDefaults(targetProject, raw, analysisRoot);
    if (isAndroidProject(targetProject)) {
      if (AndroidConfig.usesAndroidGradlePlugin9OrGreater()) {
        androidConfigMap.get(targetProject.getPath()).configureProperties(raw);
      } else {
        LegacyAndroidConfig.configureForAndroid(targetProject, SonarUtils.getConfiguredAndroidVariant(targetProject), raw);
      }
    }
    if (analysisRoot) {
      addGithubFolder(targetProject, raw);
      // Kotlin build scripts are added by the aggregator, using each project's own metadata.
    }
    overrideWithUserDefinedProperties(targetProject, raw, userDefined, analysisRoot, false);
    raw.putIfAbsent(SonarProperty.PROJECT_SOURCE_DIRS, "");
    raw.putIfAbsent(SonarProperty.PROJECT_TEST_DIRS, "");
    if (analysisRoot && targetProject.getPath().equals(":")) {
      raw.putIfAbsent(SonarProperty.PROJECT_KEY, computeProjectKey());
      raw.put(SonarProperty.KOTLIN_GRADLE_PROJECT_ROOT, targetProject.getProjectDir().getAbsolutePath());
    }
    convertProperties(raw, "", computed.properties);
    computed.userDefinedKeys.addAll(userDefined);
    if (!includeSensitive) {
      computed.properties.keySet().removeIf(SonarPropertyComputer::isSensitiveProperty);
      computed.userDefinedKeys.removeIf(SonarPropertyComputer::isSensitiveProperty);
    }
    return computed;
  }

  static boolean isSensitiveProperty(String key) {
    return SENSITIVE_PROPERTIES.contains(key.toLowerCase(Locale.ROOT));
  }

  /** Whether a sonar DSL block explicitly supplies a source or test location. */
  public boolean hasExplicitSourceOverride() {
    ActionBroadcast<SonarProperties> actions = actionBroadcastMap.get(targetProject.getPath());
    if (actions == null) {
      return false;
    }
    SonarProperties configured = new SonarProperties(new HashMap<>());
    actions.execute(configured);
    Map<String, Object> values = configured.getProperties();
    return values.containsKey(SonarProperty.PROJECT_SOURCE_DIRS) || values.containsKey(SonarProperty.PROJECT_TEST_DIRS);
  }

  /** Combines serialized project outputs without accessing Gradle's project model. */
  public static ComputedProperties aggregateIsolatedProperties(Collection<SonarProjectMetadata> metadata, String analysisRootPath) {
    return aggregateIsolatedProperties(metadata, analysisRootPath, Map.of());
  }

  /** Global overrides are applied before scanAll so system and environment settings retain their precedence. */
  public static ComputedProperties aggregateIsolatedProperties(Collection<SonarProjectMetadata> metadata, String analysisRootPath,
    Map<String, String> globalOverrides) {
    Map<String, SonarProjectMetadata> allByPath = metadata.stream().collect(Collectors.toMap(p -> p.projectPath, p -> p));
    Map<String, SonarProjectMetadata> byPath = selectAnalysisMetadata(metadata, analysisRootPath);
    SonarProjectMetadata root = byPath.get(analysisRootPath);
    if (root == null) {
      throw new IllegalArgumentException("Missing Sonar metadata for analysis project " + analysisRootPath);
    }
    ComputedProperties result = new ComputedProperties(new LinkedHashMap<>(), new LinkedHashSet<>());
    if (root.skipped) {
      return result;
    }
    SonarProjectMetadata gradleRoot = allByPath.get(":");
    String defaultKey = root.defaultProjectKey;
    String gradleRootDirectory = root.projectDirectory;
    if (!analysisRootPath.equals(":")) {
      if (gradleRoot == null || gradleRoot.defaultProjectKey.isEmpty()) {
        throw new IllegalArgumentException("Missing root project metadata for analysis project " + analysisRootPath);
      }
      defaultKey = gradleRoot.defaultProjectKey + analysisRootPath;
      gradleRootDirectory = gradleRoot.projectDirectory;
    }
    String rootKey = globalOverrides.getOrDefault(SonarProperty.PROJECT_KEY,
      root.rootProperties.getOrDefault(SonarProperty.PROJECT_KEY, defaultKey));
    appendIsolatedProject(root, "", byPath, result, rootKey, true);
    result.properties.putIfAbsent(SonarProperty.PROJECT_KEY, rootKey);
    if (!root.rootUserDefinedKeys.contains(SonarProperty.PROJECT_SOURCE_DIRS)) {
      addIsolatedKotlinBuildScripts(root, byPath, result.properties);
    }
    result.properties.putAll(globalOverrides);
    result.userDefinedKeys.addAll(globalOverrides.keySet());
    result.properties.computeIfPresent(SonarProperty.PROJECT_BASE_DIR, (k, v) -> findProjectBaseDir(result.properties));
    applyIsolatedScanAll(root, byPath, globalOverrides, result);
    result.properties.put(SonarProperty.KOTLIN_GRADLE_PROJECT_ROOT, gradleRootDirectory);
    return result;
  }

  private static Map<String, SonarProjectMetadata> selectAnalysisMetadata(Collection<SonarProjectMetadata> metadata, String analysisRootPath) {
    Map<String, SonarProjectMetadata> byPath = new LinkedHashMap<>();
    String descendantPrefix = analysisRootPath.equals(":") ? ":" : (analysisRootPath + ":");
    for (SonarProjectMetadata project : metadata) {
      if (!(project.projectPath.equals(analysisRootPath) || project.projectPath.startsWith(descendantPrefix))) {
        continue;
      }
      if (byPath.put(project.projectPath, project) != null) {
        throw new IllegalArgumentException("Duplicate Sonar metadata for project " + project.projectPath);
      }
    }
    return byPath;
  }

  private static void applyIsolatedScanAll(SonarProjectMetadata root, Map<String, SonarProjectMetadata> byPath,
    Map<String, String> globalOverrides, ComputedProperties result) {
    if ("true".equalsIgnoreCase(((String) result.properties.getOrDefault(SonarProperty.GRADLE_SCAN_ALL, "false")).trim())) {
      if (root.scanAllSourcesOverridden || root.rootUserDefinedKeys.contains(SonarProperty.PROJECT_SOURCE_DIRS)
        || root.rootUserDefinedKeys.contains(SonarProperty.PROJECT_TEST_DIRS)
        || globalOverrides.containsKey(SonarProperty.PROJECT_SOURCE_DIRS)
        || globalOverrides.containsKey(SonarProperty.PROJECT_TEST_DIRS)) {
        LOGGER.warn("Parameter sonar.gradle.scanAll is enabled but the scanner will not collect additional sources because sonar.sources or sonar.tests has been overridden.");
      } else {
        LOGGER.info("Parameter sonar.gradle.scanAll is enabled. The scanner will attempt to collect additional sources.");
        Set<Path> skippedDirs = byPath.values().stream().filter(p -> p.skipped || hasSkippedAncestor(p.projectPath, byPath))
          .map(p -> Paths.get(p.projectDirectory)).collect(Collectors.toSet());
        computeScanAllProperties(Paths.get(root.projectDirectory), skippedDirs, result.properties);
      }
    }
  }

  private static void appendIsolatedProject(SonarProjectMetadata project, String prefix, Map<String, SonarProjectMetadata> byPath,
    ComputedProperties result, String rootKey, boolean rootView) {
    Map<String, String> properties = rootView ? project.rootProperties : project.properties;
    List<String> userDefinedKeys = rootView ? project.rootUserDefinedKeys : project.userDefinedKeys;
    properties.forEach((key, value) -> result.properties.put(convertKey(key, prefix), value));
    userDefinedKeys.forEach(key -> result.userDefinedKeys.add(convertKey(key, prefix)));
    if (!prefix.isEmpty()) {
      result.properties.putIfAbsent(convertKey(SonarProperty.MODULE_KEY, prefix), rootKey + project.projectPath);
    }
    List<SonarProjectMetadata> children = byPath.values().stream()
      .filter(candidate -> !candidate.skipped && !candidate.projectPath.equals(project.projectPath)
        && project.projectPath.equals(parentPath(candidate.projectPath)))
      .sorted((a, b) -> a.projectPath.compareTo(b.projectPath))
      .collect(Collectors.toList());
    if (!children.isEmpty()) {
      List<String> ids = new ArrayList<>();
      for (SonarProjectMetadata child : children) {
        ids.add(child.projectPath);
        appendIsolatedProject(child, convertKey(child.projectPath, prefix), byPath, result, rootKey, false);
      }
      result.properties.put(convertKey(SonarProperty.MODULES, prefix), String.join(",", ids));
    }
  }

  private static String parentPath(String path) {
    int lastColon = path.lastIndexOf(':');
    return lastColon <= 0 ? ":" : path.substring(0, lastColon);
  }

  private static boolean hasSkippedAncestor(String path, Map<String, SonarProjectMetadata> byPath) {
    String current = path;
    while (!current.equals(":")) {
      current = parentPath(current);
      SonarProjectMetadata parent = byPath.get(current);
      if (parent != null && parent.skipped) {
        return true;
      }
    }
    return false;
  }

  private static void addIsolatedKotlinBuildScripts(SonarProjectMetadata root, Map<String, SonarProjectMetadata> byPath,
    Map<String, Object> properties) {
    List<String> scripts = byPath.values().stream()
      .filter(p -> !p.skipped && !hasSkippedAncestor(p.projectPath, byPath))
      .map(p -> p.buildFile).filter(p -> p.endsWith(".kts"))
      .collect(Collectors.toList());
    scripts.add(Paths.get(root.projectDirectory, "settings.gradle.kts").toString());
    String previous = (String) properties.getOrDefault(SonarProperty.PROJECT_SOURCE_DIRS, "");
    properties.put(SonarProperty.PROJECT_SOURCE_DIRS,
      SonarUtils.joinCsvStringsWithoutDuplicates(previous, SonarUtils.joinAsCsv(scripts)));
  }

  private void computeSonarProperties(Project project, ComputedProperties computedProperties) {
    computeDefaultProperties(project, computedProperties, "");

    if (shouldApplyScanAll(project, computedProperties.properties)) {
      computeScanAllProperties(project, computedProperties.properties);
    }
  }

  private void computeDefaultProperties(Project project, ComputedProperties computedProperties, String prefix) {
    if (SonarUtils.isSkipped(project)) {
      return;
    }
    Map<String, Object> rawProperties = new LinkedHashMap<>();
    Set<String> userDefinedKeys = new LinkedHashSet<>();

    addGradleDefaults(project, rawProperties);

    if (isAndroidProject(project)) {
      if (AndroidConfig.usesAndroidGradlePlugin9OrGreater()) {
        androidConfigMap.get(project.getPath()).configureProperties(rawProperties);
      } else {
        LegacyAndroidConfig.configureForAndroid(project, SonarUtils.getConfiguredAndroidVariant(project), rawProperties);
      }
    }

    if (isRootProject(project)) {
      addGithubFolder(project, rawProperties);
      addKotlinBuildScriptsToSources(project, rawProperties);
    }

    overrideWithUserDefinedProperties(project, rawProperties, userDefinedKeys);

    // These empty assignments are required because modules with no `sonar.sources` or `sonar.tests` value inherit the value from their parent module.
    // This can eventually lead to a double indexing issue in the scanner-engine.
    rawProperties.putIfAbsent(SonarProperty.PROJECT_SOURCE_DIRS, "");
    rawProperties.putIfAbsent(SonarProperty.PROJECT_TEST_DIRS, "");

    if (project.equals(targetProject)) {
      rawProperties.putIfAbsent(SonarProperty.PROJECT_KEY, computeProjectKey());
    } else {
      String projectKey = (String) computedProperties.properties.get(SonarProperty.PROJECT_KEY);
      rawProperties.putIfAbsent(SonarProperty.MODULE_KEY, projectKey + project.getPath());
    }

    convertProperties(rawProperties, prefix, computedProperties.properties);
    userDefinedKeys.stream()
      .map(k -> convertKey(k, prefix))
      .forEach(computedProperties.userDefinedKeys::add);

    List<Project> enabledChildProjects = project.getChildProjects().values().stream()
      .filter(SonarUtils::notSkipped)
      .collect(Collectors.toList());

    List<Project> skippedChildProjects = project.getChildProjects().values().stream()
      .filter(SonarUtils::isSkipped)
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
      computeDefaultProperties(childProject, computedProperties, modulePrefix);
    }

    computedProperties.properties.put(convertKey(SonarProperty.MODULES, prefix), String.join(",", moduleIds));
  }

  private boolean shouldApplyScanAll(Project project, Map<String, Object> properties) {
    // when the parent module is skipped, the properties are empty thus the scan all logic is not applied
    var scanAllValue = (String) properties.getOrDefault(SonarProperty.GRADLE_SCAN_ALL, "false");
    var scanAllEnabled = "true".equalsIgnoreCase(scanAllValue.trim());

    if (scanAllEnabled) {
      LOGGER.info("Parameter sonar.gradle.scanAll is enabled. The scanner will attempt to collect additional sources.");

      // Collecting the properties configured in the Gradle build configuration
      var sonarProps = new SonarProperties(new HashMap<>());
      actionBroadcastMap.get(project.getPath()).execute(sonarProps);

      boolean sourcesOrTestsAlreadySet = Stream
        .of(getSonarSystemProperties(project), getSonarEnvironmentVariables(project), sonarProps.getProperties())
        .map(Map::keySet)
        .flatMap(Collection::stream)
        .anyMatch(k -> SonarProperty.PROJECT_SOURCE_DIRS.endsWith(k) || SonarProperty.PROJECT_TEST_DIRS.endsWith(k));

      if (sourcesOrTestsAlreadySet) {
        LOGGER.warn("Parameter sonar.gradle.scanAll is enabled but the scanner will not collect additional sources because sonar.sources or sonar.tests has been overridden.");
        return false;
      }
    }

    return scanAllEnabled;
  }

  /**
   * Get environment variables starting with SONAR. This should include all variables that are considered by
   * {@link org.sonarsource.scanner.lib.EnvironmentConfig#load(java.util.Map)}.
   */
  static Map<String, String> getSonarEnvironmentVariables(Project project) {
    try {
      return EnvironmentConfig.load(project.getProviders().environmentVariablesPrefixedBy("SONAR").get());
    } catch (NoSuchMethodError e) {
      // Fallback for Gradle versions < 7.5 which don't have environmentVariablesPrefixedBy
      return EnvironmentConfig.load();
    }
  }

  @VisibleForTesting
  static Map<String, String> getSonarSystemProperties(Project project) {
    try {
      return project.getProviders().systemPropertiesPrefixedBy(SONAR).get();
    } catch (NoSuchMethodError e) {
      // Fallback for Gradle versions < 7.5 which don't have systemPropertiesPrefixedBy
      return fallbackSystemPropertiesForOlderGradle();
    }
  }

  private static Map<String, String> fallbackSystemPropertiesForOlderGradle() {
    return System.getProperties().entrySet().stream()
      .filter(entry -> entry.getKey().toString().startsWith(SONAR))
      .collect(Collectors.toMap(entry -> entry.getKey().toString(), entry -> entry.getValue().toString()));
  }

  private static void computeScanAllProperties(Project project, Map<String, Object> properties) {
    Set<Path> skippedDirs = skippedProjects(project)
      .map(Project::getProjectDir)
      .map(File::toPath)
      .collect(Collectors.toSet());
    computeScanAllProperties(project.getProjectDir().toPath(), skippedDirs, properties);
  }

  private static void computeScanAllProperties(Path projectDir, Set<Path> skippedDirs, Map<String, Object> properties) {
    // Collecting the existing sources from all modules, i.e. 'sonar.sources' and all 'submodule.sonar.sources'
    Set<Path> allModulesExistingSourcesAndTests = properties.entrySet()
      .stream()
      .filter(e -> e.getKey().endsWith(SonarProperty.PROJECT_SOURCE_DIRS) || e.getKey().endsWith(SonarProperty.PROJECT_TEST_DIRS))
      .map(Map.Entry::getValue)
      .map(String.class::cast)
      .map(SonarUtils::splitAsCsv)
      .flatMap(Collection::stream)
      .filter(Predicate.not(String::isBlank))
      .map(Paths::get)
      .collect(Collectors.toSet());

    Set<Path> excludedFiles = computeReportPaths(properties);

    SourceCollector visitor = SourceCollector.builder()
      .setRoot(projectDir)
      .setExistingSources(allModulesExistingSourcesAndTests)
      .setExcludedFiles(excludedFiles)
      .setDirectoriesToIgnore(skippedDirs)
      .build();


    try {
      Files.walkFileTree(projectDir, visitor);
    } catch (IOException e) {
      LOGGER.error(String.valueOf(e));
    }

    Map<InputFileType, List<Path>> collectedSourceByType = visitor.getCollectedSources().stream()
      .map(Path::toAbsolutePath)
      .collect(groupingBy(path -> SonarUtils.findProjectFileType(projectDir, path)));

    List<Path> collectedMainSources = collectedSourceByType.getOrDefault(InputFileType.MAIN, List.of());
    appendAdditionalSourceFiles(properties, SonarProperty.PROJECT_SOURCE_DIRS, collectedMainSources);

    List<Path> collectedTestSources = collectedSourceByType.getOrDefault(InputFileType.TEST, List.of());
    appendAdditionalSourceFiles(properties, SonarProperty.PROJECT_TEST_DIRS, collectedTestSources);
  }

  private static void appendAdditionalSourceFiles(Map<String, Object> properties, String sourcePropertyToUpdate, List<Path> collectedSources) {
    String existingValue = (String) properties.getOrDefault(sourcePropertyToUpdate, "");
    Set<Path> existingSources = existingValue.isBlank()
      ? Collections.emptySet()
      : SonarUtils.splitAsCsv(existingValue).stream()
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

  private void overrideWithUserDefinedProperties(Project project, Map<String, Object> rawProperties, Set<String> userDefinedKeys) {
    overrideWithUserDefinedProperties(project, rawProperties, userDefinedKeys, isRootProject(project), true);
  }

  private void overrideWithUserDefinedProperties(Project project, Map<String, Object> rawProperties, Set<String> userDefinedKeys,
    boolean analysisRoot, boolean includeGlobalOverrides) {
    ActionBroadcast<SonarProperties> actionBroadcast = actionBroadcastMap.get(project.getPath());
    if (actionBroadcast != null) {
      Map<String, Object> defaultProperties = new LinkedHashMap<>(rawProperties);
      evaluateSonarPropertiesBlocks(actionBroadcast, rawProperties);
      for (Map.Entry<String, Object> entry : rawProperties.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        Object defaultValue = defaultProperties.get(key);
        if (defaultValue == null || !defaultValue.equals(value)) {
          userDefinedKeys.add(key);
        }
      }
    }
    if (analysisRoot && includeGlobalOverrides) {
      Map<String, String> environmentProperties = getSonarEnvironmentVariables(project);
      rawProperties.putAll(environmentProperties);
      userDefinedKeys.addAll(environmentProperties.keySet());

      Map<String, String> systemProperties = getSonarSystemProperties(project);
      rawProperties.putAll(systemProperties);
      userDefinedKeys.addAll(systemProperties.keySet());
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
        properties.put(SonarProperty.SOURCE_ENCODING, encoding);
      }
    });
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
      if (isReportEnabled(xmlReport) && reportDestination != null) {
        appendProp(properties, SonarProperty.JACOCO_XML_REPORT_PATHS, reportDestination);
      } else {
        LOGGER.info("JaCoCo report task detected, but XML report is not enabled or it was not produced. " +
          "Coverage for this task will not be reported.");
      }
    });
  }

  private static void configureTestReports(Test testTask, Map<String, Object> properties) {
    File testResultsDir = getDestination(testTask.getReports().getJunitXml());

    if (testResultsDir == null) {
      return;
    }

    appendProp(properties, SonarProperty.JUNIT_REPORT_PATHS, testResultsDir);
    // For backward compatibility
    appendProp(properties, SonarProperty.JUNIT_REPORTS_PATH, testResultsDir);
    appendProp(properties, SonarProperty.SUREFIRE_REPORTS_PATH, testResultsDir);
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
    setMainBinariesProps(properties, mainClassDirs, addForGroovy);

    SourceSet test = sourceSets.getAt("test");
    Collection<File> testClassDirs = getJavaOutputDirs(test);
    appendProps(properties, SonarProperty.JAVA_TEST_BINARIES, testClassDirs);
  }

  private static @Nullable Collection<File> getJavaSourceFiles(SourceSet sourceSet) {
    List<File> sourceDirectories = new ArrayList<>(sourceSet.getAllJava().getSrcDirs());

    return nonEmptyOrNull(sourceDirectories);
  }

  private static Collection<File> getJavaOutputDirs(SourceSet sourceSet) {
    return sourceSet.getOutput().getClassesDirs().getFiles();
  }

  private static @Nullable Collection<File> getKotlinSourceFiles(Object extension, String sourceSetNameSuffix) {
    try {
      Method getSourceSetsMethod = extension.getClass().getMethod("getSourceSets");
      NamedDomainObjectContainer<?> sourceSets = (NamedDomainObjectContainer) getSourceSetsMethod.invoke(extension);
      Collection<File> sourceFiles = sourceSets.stream()
        .map(InternalKotlinSourceSet::of)
        .filter(s -> s.name.toLowerCase(Locale.ROOT).endsWith(sourceSetNameSuffix))
        .flatMap(s -> s.srcDirs.stream())
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

  private void addGradleDefaults(final Project project, final Map<String, Object> properties) {
    addGradleDefaults(project, properties, project.equals(targetProject));
  }

  private static void addGradleDefaults(final Project project, final Map<String, Object> properties, boolean analysisRoot) {
    properties.put(SonarProperty.PROJECT_NAME, project.getName());
    properties.put(SonarProperty.PROJECT_DESCRIPTION, project.getDescription());
    properties.put(SonarProperty.PROJECT_VERSION, project.getVersion());
    properties.put(SonarProperty.PROJECT_BASE_DIR, project.getProjectDir());

    if (analysisRoot) {
      // Root project of the analysis
      Provider<Directory> workingDir = project.getLayout().getBuildDirectory().dir(SONAR);
      properties.put(SonarProperty.WORKING_DIRECTORY, workingDir.get().getAsFile());
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
    buildScripts.add(settingsFile);
    SonarUtils.appendSourcesProp(properties, buildScripts, false);
  }

  private static void addGithubFolder(Project project, Map<String, Object> properties) {
    File githubFolder = project.getProjectDir().toPath().resolve(".github").toFile();
    // Note that we don't check that .github exists or is a directory, because this is executed in configuration phase
    // and the folder could be created later. The task removes non-existing paths later.
    SonarUtils.appendSourcesProp(properties, List.of(githubFolder), false);
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
    // `getOutputLocation` changed return type between Gradle 7 and Gradle 8,
    // so we need to use reflection to call it.
    Method getOutputLocation = report.getClass().getMethod("getOutputLocation");
    provider = (Provider<? extends FileSystemLocation>) getOutputLocation.invoke(report);
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
      .filter(SonarUtils::isSkipped)
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
