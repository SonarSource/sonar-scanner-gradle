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
package org.sonarqube.gradle.snapshot;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarqube.gradle.support.normalization.AndroidPathNormalizer;

public final class SnapshotUtils {
  private static final String JAVA_VERSION_PLACEHOLDER = "${JAVA_VERSION}";
  private static final String JAVA_SOURCE_PLACEHOLDER = "${JAVA_SOURCE}";
  private static final String JAVA_TARGET_PLACEHOLDER = "${JAVA_TARGET}";
  private static final Set<String> ORDER_INSENSITIVE_KEYS = Set.of("sonar.modules", "sonar.libraries", "sonar.java.libraries", "sonar.java.test.libraries", "sonar.java.test.binaries");
  private static final List<String> ORDER_INSENSITIVE_SUFFIXES = List.of(".sonar.modules", ".sonar.libraries", ".sonar.java.libraries", ".sonar.java.test.libraries", ".sonar.java.test.binaries");
  private static final List<String> IGNORABLE_FRAGMENTS = List.of("/build/intermediates/compile_r_class_jar/", "/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/", "/build/intermediates/app_classes/", "/build/intermediates/runtime_app_classes_jar/");
  private static final String IGNORABLE_CLASSES_PATH = "/build/intermediates/classes";

  private SnapshotUtils() {
    // Utility class: contains only static methods and is not intended to be instantiated.
  }

  public static Map<String, String> sanitize(Map<String, String> properties, Set<String> ignoredKeys) {
    Map<String, String> sanitized = new LinkedHashMap<>(properties);
    ignoredKeys.forEach(sanitized::remove);
    sanitized.replaceAll(SnapshotUtils::normalizeComparableValue);
    return sanitized;
  }

  public static Map<String, String> expand(Map<String, String> expected, Map<String, String> actual) {
    Map<String, String> expanded = new LinkedHashMap<>();
    expected.forEach((key, value) -> expanded.put(key, expandValue(value, actual.get(key))));
    return expanded;
  }

  public static Map<String, String> canonicalize(Map<String, String> properties) {
    Map<String, String> canonicalized = new LinkedHashMap<>();
    properties.forEach((key, value) -> canonicalized.put(key, canonicalizeValue(key, value)));
    return canonicalized;
  }

  private static String normalizeComparableValue(String key, String value) {
    if (value == null || !isOrderInsensitive(key)) return value;
    return Arrays.stream(value.split(",")).map(String::trim).filter(entry -> !entry.isEmpty()).filter(entry -> !isIgnorable(entry)).distinct().sorted(Comparator.naturalOrder()).collect(Collectors.joining(","));
  }

  private static String expandValue(String value, String actualValue) {
    if (value == null) return null;
    String expanded = actualValue == null ? value : value.replace(JAVA_VERSION_PLACEHOLDER, actualValue).replace(JAVA_SOURCE_PLACEHOLDER, actualValue).replace(JAVA_TARGET_PLACEHOLDER, actualValue);
    return AndroidPathNormalizer.normalize(expanded);
  }

  private static String canonicalizeValue(String key, String value) {
    if (value == null) return null;
    if (isJavaVersionKey(key)) {
      return key.endsWith(".source") ? JAVA_SOURCE_PLACEHOLDER : JAVA_TARGET_PLACEHOLDER;
    }
    return AndroidPathNormalizer.normalize(value);
  }

  private static boolean isOrderInsensitive(String key) {
    return ORDER_INSENSITIVE_KEYS.contains(key) || ORDER_INSENSITIVE_SUFFIXES.stream().anyMatch(key::endsWith);
  }

  private static boolean isIgnorable(String entry) {
    return entry.endsWith(IGNORABLE_CLASSES_PATH) || IGNORABLE_FRAGMENTS.stream().anyMatch(entry::contains);
  }

  private static boolean isJavaVersionKey(String key) {
    return "sonar.java.source".equals(key) || "sonar.java.target".equals(key) || key.endsWith(".sonar.java.source") || key.endsWith(".sonar.java.target");
  }
}
