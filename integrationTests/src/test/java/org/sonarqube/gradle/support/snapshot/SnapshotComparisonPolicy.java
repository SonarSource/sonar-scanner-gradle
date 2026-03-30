/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support.snapshot;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SnapshotComparisonPolicy {
  private static final Set<String> ORDER_INSENSITIVE_KEYS = Set.of("sonar.modules", "sonar.libraries", "sonar.java.libraries", "sonar.java.test.libraries", "sonar.java.test.binaries");
  private static final List<String> ORDER_INSENSITIVE_SUFFIXES = List.of(".sonar.modules", ".sonar.libraries", ".sonar.java.libraries", ".sonar.java.test.libraries", ".sonar.java.test.binaries");
  private static final List<String> IGNORABLE_FRAGMENTS = List.of("/build/intermediates/compile_r_class_jar/", "/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/", "/build/intermediates/app_classes/", "/build/intermediates/runtime_app_classes_jar/");
  private static final String IGNORABLE_CLASSES_PATH = "/build/intermediates/classes";

  public Map<String, String> sanitize(Map<String, String> properties, Set<String> ignoredKeys) {
    Map<String, String> sanitized = new LinkedHashMap<>(properties);
    ignoredKeys.forEach(sanitized::remove);
    sanitized.replaceAll(this::normalizeValue);
    return sanitized;
  }

  private String normalizeValue(String key, String value) {
    if (value == null || !isOrderInsensitive(key)) {
      return value;
    }
    return Arrays.stream(value.split(",")).map(String::trim).filter(entry -> !entry.isEmpty()).filter(entry -> !isIgnorable(entry)).distinct()
      .sorted(Comparator.naturalOrder()).collect(Collectors.joining(","));
  }

  private boolean isOrderInsensitive(String key) {
    return ORDER_INSENSITIVE_KEYS.contains(key) || ORDER_INSENSITIVE_SUFFIXES.stream().anyMatch(key::endsWith);
  }

  private boolean isIgnorable(String entry) {
    return entry.endsWith(IGNORABLE_CLASSES_PATH) || IGNORABLE_FRAGMENTS.stream().anyMatch(entry::contains);
  }
}
