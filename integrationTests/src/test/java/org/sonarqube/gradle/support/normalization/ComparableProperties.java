/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarqube.gradle.support.normalization;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarqube.gradle.support.GradleRuntime;

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
  private static final String SONAR_JAVA_JDKHOME_SUFFIX = ".sonar.java.jdkHome";

  private ComparableProperties() {
    // Utility class: contains only static methods and is not intended to be instantiated.
  }

  public static Map<String, String> extract(Properties properties) {
    Map<String, String> replacements = replacements(requiredProjectBaseDir(properties));
    return properties
      .stringPropertyNames()
      .stream()
      .sorted()
      .filter(ComparableProperties::isComparableKey)
      .map(key -> Map.entry(key, normalizeValue(key, properties.getProperty(key), replacements)))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static String requiredProjectBaseDir(Properties properties) {
    return Optional.ofNullable(properties.getProperty(SONAR_PROJECT_BASE_DIR))
      .orElseThrow(() -> new IllegalStateException(SONAR_PROJECT_BASE_DIR + " is null"));
  }

  private static Map<String, String> replacements(String projectBaseDir) {
    // Do not use Map.of() here: its iteration order is unspecified, and some replacement prefixes can overlap
    // (for example ${parentBaseDir} can also be under ${HOME} on Windows CI). We use LinkedHashMap so replacements
    // always run in the declared order, with the more specific prefix applied first, which keeps snapshot tests stable.
    Map<String, String> replacements = new LinkedHashMap<>();
    replacements.put(PARENT_BASE_DIR_PLACEHOLDER, Paths.get(projectBaseDir).getParent().toString());
    replacements.put(CURRENT_WORKING_DIR_PLACEHOLDER, GradleRuntime.userDir());
    replacements.put(HOME_PLACEHOLDER, GradleRuntime.comparableHome());
    return replacements;
  }

  private static boolean isComparableKey(String key) {
    return !SONAR_TOKEN.equals(key) && (!key.startsWith(SCANNER_PREFIX) || COMPARABLE_SCANNER_KEYS.contains(key));
  }

  private static boolean isHiddenKey(String key) {
    return HIDDEN_KEYS.contains(key) || key.endsWith(SONAR_JAVA_JDKHOME_SUFFIX);
  }

  private static String normalizeValue(String key, String value, Map<String, String> replacements) {
    String normalizedValue = value;
    normalizedValue = isHiddenKey(key) ? HIDDEN_VALUE : normalizedValue;
    normalizedValue = replacePrefixes(normalizedValue, replacements);
    normalizedValue = AndroidPathNormalizer.normalize(AndroidSdkPathNormalizer.normalize(normalizedValue.replace('\\', '/')));
    normalizedValue = GradleCachePathNormalizer.normalize(replaceM2(normalizedValue, replacements.keySet()));
    return normalizedValue;
  }

  static String replacePrefix(String value, String oldPrefix, String newPrefix) {
    String result = value;
    String prefixed = "," + oldPrefix;
    int index = result.lastIndexOf(prefixed);
    while (index != -1) {
      result = result.substring(0, index + 1) + newPrefix + result.substring(index + prefixed.length());
      index = result.lastIndexOf(prefixed);
    }
    return result.startsWith(oldPrefix) ? newPrefix + result.substring(oldPrefix.length()) : result;
  }

  private static String replacePrefixes(String value, Map<String, String> replacements) {
    String result = value;
    for (Map.Entry<String, String> entry : replacements.entrySet()) {
      result = replacePrefix(result, entry.getValue(), entry.getKey());
    }
    return result;
  }

  private static String replaceM2(String value, Set<String> placeholders) {
    String result = value;
    for (String placeholder : placeholders) {
      result = replacePrefix(result, placeholder + "/.m2", M2_PLACEHOLDER);
    }
    return result;
  }
}
