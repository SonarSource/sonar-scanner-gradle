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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public final class SnapshotNormalizer {
  private static final Set<String> ORDER_INSENSITIVE_KEYS = Set.of(
    "sonar.modules",
    "sonar.libraries",
    "sonar.java.libraries",
    "sonar.java.test.libraries",
    "sonar.java.test.binaries"
  );
  private static final List<String> ORDER_INSENSITIVE_SUFFIXES = List.of(
    ".sonar.modules",
    ".sonar.libraries",
    ".sonar.java.libraries",
    ".sonar.java.test.libraries",
    ".sonar.java.test.binaries"
  );

  private SnapshotNormalizer() {
    // Utility class: contains only static methods and is not intended to be instantiated.
  }

  public static Map<String, String> normalize(Properties properties) {
    Map<String, String> propertiesMap = new LinkedHashMap<>();
    properties.forEach((key, value) -> propertiesMap.put(key.toString(), value.toString()));
    return normalize(propertiesMap);
  }

  public static Map<String, String> normalize(Map<String, String> snapshot) {
    Map<String, String> normalized = new LinkedHashMap<>();
    PathsNormalizer.normalize(snapshot).entrySet().stream()
      .sorted(Map.Entry.comparingByKey())
      .forEach(entry -> normalized.put(entry.getKey(), normalizeEntry(entry.getKey(), entry.getValue())));
    return normalized;
  }

  private static String normalizeEntry(String key, String value) {
    String result = IgnoredPropertiesNormalizer.normalize(key, value);
    result = reorderIfNeeded(key, result);
    return result;
  }

  private static String reorderIfNeeded(String key, String value) {
    if (ORDER_INSENSITIVE_KEYS.contains(key) || ORDER_INSENSITIVE_SUFFIXES.stream().anyMatch(key::endsWith)) {
      return Arrays.stream(value.split(",")).sorted().collect(Collectors.joining(","));
    }
    return value;
  }
}
