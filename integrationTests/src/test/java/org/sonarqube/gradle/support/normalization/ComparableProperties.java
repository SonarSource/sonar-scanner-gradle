/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support.normalization;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.sonarqube.gradle.support.GithubWorkspace;
import org.sonarqube.gradle.support.GradleTestEnvironment;
import org.sonarqube.gradle.support.PrefixReplacements;

public final class ComparableProperties {
  private static final String SONAR_PROJECT_BASE_DIR = "sonar.projectBaseDir";
  private static final String SONAR_TOKEN = "sonar.token";
  private static final String HIDDEN_VALUE = "<hidden>";
  private static final String PARENT_BASE_DIR_PLACEHOLDER = "${parentBaseDir}";
  private static final String CURRENT_WORKING_DIR_PLACEHOLDER = "${currentWorkingDir}";
  private static final String HOME_PLACEHOLDER = "${HOME}";
  private static final String M2_PLACEHOLDER = "${M2}";
  private static final String SCANNER_PREFIX = "sonar.scanner.";
  private static final Set<String> HIDDEN_KEYS = Set.of(
    "sonar.java.jdkHome", "sonar.scanner.arch", "sonar.scanner.os", "sonar.scanner.opts", SONAR_TOKEN,
    "sonar.scanner.internal.dumpToFile", "sonar.scanner.appVersion");
  private static final Set<String> COMPARABLE_SCANNER_KEYS = Set.of(
    "sonar.scanner.apiBaseUrl", "sonar.scanner.app", "sonar.scanner.appVersion", "sonar.scanner.arch",
    "sonar.scanner.internal.dumpToFile", "sonar.scanner.os", "sonar.scanner.wasEngineCacheHit");

  private ComparableProperties() {
  }

  public static Map<String, String> extract(Properties properties) {
    Map<String, String> result = new LinkedHashMap<>();
    Map<String, String> replacements = replacements(requiredProjectBaseDir(properties));
    properties.stringPropertyNames().stream().sorted().filter(ComparableProperties::isComparableKey)
      .forEach(key -> result.put(key, normalizeValue(key, properties.getProperty(key), replacements)));
    return result;
  }

  private static String requiredProjectBaseDir(Properties properties) {
    String value = properties.getProperty(SONAR_PROJECT_BASE_DIR);
    if (value == null) {
      throw new IllegalStateException(SONAR_PROJECT_BASE_DIR + " is null");
    }
    return value;
  }

  private static Map<String, String> replacements(String projectBaseDir) {
    Map<String, String> replacements = new LinkedHashMap<>();
    replacements.put(PARENT_BASE_DIR_PLACEHOLDER, Paths.get(projectBaseDir).getParent().toString());
    replacements.put(CURRENT_WORKING_DIR_PLACEHOLDER, GradleTestEnvironment.userDir());
    replacements.put(HOME_PLACEHOLDER, GithubWorkspace.homeEquivalent());
    return replacements;
  }

  private static boolean isComparableKey(String key) {
    return !SONAR_TOKEN.equals(key) && (!key.startsWith(SCANNER_PREFIX) || COMPARABLE_SCANNER_KEYS.contains(key));
  }

  private static String normalizeValue(String key, String value, Map<String, String> replacements) {
    String hidden = HIDDEN_KEYS.contains(key) || key.endsWith(".sonar.java.jdkHome") ? HIDDEN_VALUE : value;
    String prefixed = PrefixReplacements.apply(hidden, replacements);
    String normalized = AndroidPathNormalizer.normalize(AndroidSdkPathNormalizer.normalize(prefixed.replace('\\', '/')));
    return GradleCachePathNormalizer.normalize(PrefixReplacements.replaceM2(normalized, replacements.keySet(), M2_PLACEHOLDER));
  }
}
