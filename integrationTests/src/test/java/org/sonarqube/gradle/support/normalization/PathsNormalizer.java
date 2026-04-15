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
import java.util.Optional;
import java.util.stream.Collectors;

public class PathsNormalizer {

  public static final String BINARIES_SUFFIX = "binaries";
  public static final String LIBRARIES_SUFFIX = "libraries";

  private static final String PROJECT_BASE_DIR_PROPERTY = "sonar.projectBaseDir";
  private static final String PROJECT_BASE_DIR_PLACEHOLDER = "${PROJECT_BASE_DIR}";

  private static final List<String> PROPERTIES_SUFFIX_TO_NORMALIZE = List.of(
    "reportPaths", "reportsPath", "projectBaseDir", "sonar.sources", "sonar.tests", "gradleProjectRoot", "sonar.working.directory", "sonar.coverage.jacoco.xmlReportPaths"
  );

  private PathsNormalizer() {
    // Utility class: contains only static methods and is not intended to be instantiated.
  }

  public static Map<String, String> normalize(Map<String, String> snapshot) {
    Optional<String> projectBaseDir = Optional.ofNullable(snapshot.get(PROJECT_BASE_DIR_PROPERTY)).map(PathsNormalizer::normalizeWindowsPath);
    Map<String, String> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : snapshot.entrySet()) {
      var property = entry.getKey();
      var value = entry.getValue();
      if (property.endsWith(BINARIES_SUFFIX) || property.endsWith(LIBRARIES_SUFFIX)) {
        value = takeOnlyFileNames(value);
      }
      if (PROPERTIES_SUFFIX_TO_NORMALIZE.stream().anyMatch(property::endsWith)) {
        value = normalizeWindowsPath(value);
        if (projectBaseDir.isPresent()) {
          value = value.replace(projectBaseDir.get(), PROJECT_BASE_DIR_PLACEHOLDER);
        }
      }
      normalized.put(
        property, value
      );
    }
    return normalized;
  }

  private static String takeOnlyFileNames(String paths) {
    return Arrays.stream(paths.split(","))
      .map(PathsNormalizer::normalizePath)
      .distinct()
      .sorted()
      .collect(Collectors.joining(","));
  }

  private static String normalizePath(String path) {
    path = normalizeWindowsPath(path);
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private static String normalizeWindowsPath(String value) {
    return value.replace("\\", "/");
  }

}
