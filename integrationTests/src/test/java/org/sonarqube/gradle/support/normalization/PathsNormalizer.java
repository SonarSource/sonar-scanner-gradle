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
import java.util.stream.Collectors;

public class PathsNormalizer {
  private static final String DELIMITER = ",";

  public static final String BINARIES_SUFFIX = "binaries";
  public static final String LIBRARIES_SUFFIX = "libraries";

  private static final String PROJECT_BASE_DIR_PROPERTY = "sonar.projectBaseDir";
  private static final String PROJECT_BASE_DIR_PLACEHOLDER = "${PROJECT_BASE_DIR}";
  private static final List<String> PROPERTIES_SUFFIX_TO_NORMALIZE = List.of(
    "reportPaths",
    "sonar.tests",
    "reportsPath",
    "sonar.modules",
    "sonar.sources",
    "projectBaseDir",
    "gradleProjectRoot",
    "sonar.working.directory",
    "sonar.coverage.jacoco.xmlReportPaths"
  );

  private static final List<String> IGNORED_PATHS = List.of(
    "main", "R.jar", "classes.jar", "classes"
  );

  private PathsNormalizer() {
    // Utility class: contains only static methods and is not intended to be instantiated.
  }

  public static Map<String, String> normalize(Map<String, String> snapshot) {
    String projectBaseDir = normalizeWindowsPath(snapshot.get(PROJECT_BASE_DIR_PROPERTY));

    Map<String, String> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : snapshot.entrySet()) {
      var property = entry.getKey();
      var value = entry.getValue();
      var normalizedValue = normalizeEntry(property, value, projectBaseDir);
      if (!normalizedValue.isEmpty()) {
        normalized.put(
          property, normalizedValue
        );
      }
    }

    return normalized;
  }

  private static String normalizeEntry(String property, String value, String projectBaseDir) {
    var paths = Arrays.stream(value.split(DELIMITER));
    if (property.endsWith(BINARIES_SUFFIX) || property.endsWith(LIBRARIES_SUFFIX)) {
      paths = paths
        .map(PathsNormalizer::normalizeWindowsPath)
        .map(PathsNormalizer::takeOnlyFileName);
    }
    if (PROPERTIES_SUFFIX_TO_NORMALIZE.stream().anyMatch(property::endsWith)) {
      paths = paths.map(PathsNormalizer::normalizeWindowsPath);
      if (projectBaseDir != null) {
        paths = paths.map(path ->
          path.replace(projectBaseDir, PROJECT_BASE_DIR_PLACEHOLDER)
        );
      }
    }
    return paths
      .filter(path -> !IGNORED_PATHS.contains(path))
      .distinct()
      .sorted()
      .collect(Collectors.joining(DELIMITER));
  }

  private static String takeOnlyFileName(String path) {
    path = normalizeWindowsPath(path);
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private static String normalizeWindowsPath(String value) {
    return value.replace("\\", "/");
  }
}
