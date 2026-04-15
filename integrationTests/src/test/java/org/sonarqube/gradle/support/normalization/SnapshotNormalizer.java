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
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class SnapshotNormalizer {


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
      .forEach(entry ->
        IgnoredPropertiesNormalizer
          .normalize(entry.getKey(), entry.getValue())
          .ifPresent(result ->
            normalized.put(entry.getKey(), result)
          )
      );
    return normalized;
  }
}
