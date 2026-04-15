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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class SnapshotNormalizer {
  private static final String IGNORED_PROPERTY_PLACEHOLDER = "<ignored>";

  private static final List<String> IGNORED_KEYS = List.of(
    "sonar.token"
  );

  private static final List<String> VALUE_INSENSITIVE_KEYS = List.of(
    "sonar.scanner.os",
    "sonar.scanner.arch",
    "sonar.scanner.internal.dumpToFile",
    "sonar.scanner.appVersion"
  );

  private static final List<String> VALUE_INSENSITIVE_SUFFIXES = List.of(
    // as we have QA J11 in the matrix so we can't assume a specific Java version for these properties
    "sonar.java.source",
    "sonar.java.target",
    "jdkHome"
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
      .filter(entry -> !IGNORED_KEYS.contains(entry.getKey()))
      .forEach(entry -> {
        var key = entry.getKey();
        if (VALUE_INSENSITIVE_KEYS.contains(key) || VALUE_INSENSITIVE_SUFFIXES.stream().anyMatch(key::endsWith)) {
          normalized.put(key, IGNORED_PROPERTY_PLACEHOLDER);
        } else {
          normalized.put(key, entry.getValue());
        }
      }
    );
    return normalized;
  }
}
