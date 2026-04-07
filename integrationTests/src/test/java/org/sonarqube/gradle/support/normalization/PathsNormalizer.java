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
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public class PathsNormalizer {
  private static final Pattern WINDOWS_DRIVE_MATCHER = Pattern.compile("(?i)(?<=^|,|\\s)[a-z]:(?=[/\\\\])");
  private static final Pattern HASHES_MATCHER = Pattern.compile("\\b([0-9a-f]{32}|[0-9a-f]{40}|junit\\d{19,20})\\b");
  private static final Pattern GRADLE_CACHE_MATCHER = Pattern.compile("(?i)[^,]*?\\.gradle/caches/[^/]+/");
  private static final Pattern M2_CACHE_MATCHER = Pattern.compile("(?i)[^,]*?\\.m2/repository/");
  private static final String HASH_PLACEHOLDER = "{HASH}";
  private static final String GRADLE_CACHE_PLACEHOLDER = "{GRADLE_CACHE}/";
  private static final String M2_CACHE_PLACEHOLDER = "{M2_REPOSITORY}/";
  private static final Map<Pattern, String> REGEX_REPLACEMENT = Map.of(
    HASHES_MATCHER, HASH_PLACEHOLDER,
    GRADLE_CACHE_MATCHER, GRADLE_CACHE_PLACEHOLDER,
    M2_CACHE_MATCHER, M2_CACHE_PLACEHOLDER
  );

  private static final Map<String, String> PROPERTIES_REPLACEMENT = Map.of(
    "sonar.projectBaseDir", "${PROJECT_BASE_DIR}"
  );

  private static final List<String> JDK_ROOTS = listOfPaths(
    "/usr/lib/jvm",
    "/Program Files/Java",
    System.getenv("JAVA_HOME"),
    System.getenv("JDK_HOME")
  );
  private static final List<String> ANDROID_SDK_ROOTS = listOfPaths(
    "/usr/local/lib/android/sdk",
    "/Android/Sdk",
    System.getenv("ANDROID_HOME"),
    System.getenv("ANDROID_SDK_ROOT")
  );
  private static final String ANDROID_SDK_PLACEHOLDER = "${ANDROID_SDK}";
  public static final String JDK_PLACEHOLDER = "${JDK}";

  private PathsNormalizer() {
    // Utility class: contains only static methods and is not intended to be instantiated.
  }

  public static Map<String, String> normalize(Map<String, String> snapshot) {
    var replacements = computeStringReplacements(snapshot);
    Map<String, String> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : snapshot.entrySet()) {
      normalized.put(entry.getKey(), normalizeEntry(entry.getValue(), replacements));
    }
    return normalized;
  }

  private static String normalizeEntry(String value, Map<String, String> replacements) {
    String result = normalizeWindowsPath(value);
    for (Map.Entry<String, String> entry : replacements.entrySet()) {
      result = result.replace(entry.getKey(), entry.getValue());
    }
    result = applyRegexReplacements(result);
    return result;
  }

  private static String applyRegexReplacements(String value) {
    String result = value;
    for (Map.Entry<Pattern, String> entry : REGEX_REPLACEMENT.entrySet()) {
      result = entry.getKey().matcher(result).replaceAll(entry.getValue());
    }
    return result;
  }

  private static Map<String, String> computeStringReplacements(Map<String, String> snapshot) {
    Map<String, String> replacements = new LinkedHashMap<>();
    putReplacements(ANDROID_SDK_ROOTS, ANDROID_SDK_PLACEHOLDER, replacements);
    putReplacements(JDK_ROOTS, JDK_PLACEHOLDER, replacements);
    for (Map.Entry<String, String> entry : PROPERTIES_REPLACEMENT.entrySet()) {
      var key = entry.getKey();
      var placeHolder = entry.getValue();
      var value = snapshot.get(key);
      Optional.ofNullable(value).ifPresent(v -> replacements.put(normalizeWindowsPath(v), placeHolder));
    }
    return replacements;
  }

  private static void putReplacements(List<String> entries, String placeholder, Map<String, String> replacements) {
    entries.forEach(entry -> replacements.put(entry, placeholder));
  }

  private static List<String> listOfPaths(@Nullable String... entries) {
    return Arrays.stream(entries).filter(Objects::nonNull).map(PathsNormalizer::normalizeWindowsPath).collect(Collectors.toList());
  }

  private static String normalizeWindowsPath(String value) {
    return WINDOWS_DRIVE_MATCHER
      .matcher(value).replaceAll("") // drop drive letter
      .replace("\\", "/");
  }
}
