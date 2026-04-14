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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public class PathsNormalizer {
  private static final Pattern WINDOWS_DRIVE_MATCHER = Pattern.compile("(?i)(?<=^|,|\\s)[a-z]:(?=[/\\\\])");
  private static final Pattern HASHES_MATCHER = Pattern.compile("\\b([0-9a-f]{32}|[0-9a-f]{40}|junit\\d{19,20})/");
  private static final Pattern GRADLE_METADATA_CLEANER = Pattern.compile("files-2.1/([^/]+)/");
  private static final Pattern GRADLE_CACHE_MATCHER = Pattern.compile("(?i)[^,]*?\\.gradle/caches/[^/]+/");
  private static final Pattern M2_CACHE_MATCHER = Pattern.compile("(?i)[^,]*?\\.m2/repository/");
  private static final Pattern HAMCREST_MATCHER = Pattern.compile("\\{M2_GRADLE_CACHE\\}/[^,]*hamcrest[^,]*\\.jar");
  private static final Pattern BUILDTOOL_MATCHER = Pattern.compile("\\{ANDROID_SDK\\}/build-tools/\\d+\\.\\d+\\.\\d+");
  private static final Pattern JETIFIED_MATCHER = Pattern.compile("[^,\"]*?\\/transformed\\/jetified-[^,\"]*?\\.jar\\s?,?");
  private static final String HASH_PLACEHOLDER = "";
  private static final String GRADLE_METADATA_PLACEHOLDER = "$1/";
  private static final String M2_GRADLE_CACHE_PLACEHOLDER = "{M2_GRADLE_CACHE}/";
  private static final String HAMCREST_PLACEHOLDER = "{HAMCREST}";
  private static final String BUILDTOOL_PLACEHOLDER = "{BUILDTOOL}";
  private static final Map<Pattern, String> REGEX_REPLACEMENT = linkedHashMapOf(
    GRADLE_CACHE_MATCHER, M2_GRADLE_CACHE_PLACEHOLDER,
    M2_CACHE_MATCHER, M2_GRADLE_CACHE_PLACEHOLDER,
    GRADLE_METADATA_CLEANER, GRADLE_METADATA_PLACEHOLDER,
    HASHES_MATCHER, HASH_PLACEHOLDER,
    HAMCREST_MATCHER, HAMCREST_PLACEHOLDER,
    BUILDTOOL_MATCHER, BUILDTOOL_PLACEHOLDER,
    JETIFIED_MATCHER, ""
  );

  private static final Map<String, String> STRING_REPLACEMENT = linkedHashMapOf(
    "org.junit.", "org/junit/",
    "org.junit", "org/junit",
    "org.apiguardian", "org/apiguardian",
    "org.opentest4j", "org/opentest4j",
    "junit-jupiter-api", "junit-platform-commons",
    "compileDebugJavaWithJavac/", "",
    "compileDemoMinApi23DebugUnitTestJavaWithJavac/", "",
    "compileDemoMinApi23DebugJavaWithJavac/", "",
    "processDemoMinApi23DebugResources/", "",
    "processFullMinApi23ReleaseResources/", "",
    "processDebugResources/", "",
    "transforms/", "",
    "compile_app_classes_jar/", "app_classes/",
    "bundleDebugClassesToCompileJar/", "",
    "compileFullMinApi23ReleaseJavaWithJavac/", "",
    "compileFlavor1DebugJavaWithJavac/", "",
    "processFlavor1DebugResources/", "",
    "bundleLibCompileToJarDebug/", "",
    "compileReleaseJavaWithJavac/", "",
    "generateDebugRFile/", "",
    "bundleFlavor1DebugClassesToCompileJar/", "",
    "processReleaseResources/", "",
    "generateDebugUnitTestStubRFile/", "",
    "compileDebugAndroidTestJavaWithJavac/", "",
    "bundleDemoMinApi23DebugClassesToCompileJar/", "",
    "processDemoMinApi23DebugAndroidTestResources/", "",
    "compileReleaseUnitTestJavaWithJavac/", "",
    "compileDebugUnitTestJavaWithJavac/", "",
    "processDebugAndroidTestResources/", "",
    "processFlavor1DebugAndroidTestResources/", "",
    "compileFlavor1DebugUnitTestJavaWithJavac/", "",
    "compileFlavor1DebugAndroidTestJavaWithJavac/", "",
    "compileFullMinApi23ReleaseUnitTestJavaWithJavac/", "",
    "bundleFullMinApi23ReleaseClassesToCompileJar/", "",
    "bundleReleaseClassesToCompileJar/", ""
  );

  private static final String ANDROID_SDK_PLACEHOLDER = "${ANDROID_SDK}";
  public static final String SONAR_JDK_PLACEHOLDER = "${SONAR_JDK}";

  private static final Map<String, String> PROPERTIES_REPLACEMENT = linkedHashMapOf(
    "sonar.projectBaseDir", "${PROJECT_BASE_DIR}"
  );

  private static final List<String> ANDROID_SDK_ROOTS = listOfPaths(
    "/usr/local/lib/android/sdk",
    "/Android/Sdk",
    System.getenv("ANDROID_HOME"),
    System.getenv("ANDROID_SDK_ROOT")
  );


  private PathsNormalizer() {
    // Utility class: contains only static methods and is not intended to be instantiated.
  }

  public static Map<String, String> normalize(Map<String, String> snapshot, Set<String> excludedPaths) {
    snapshot = normalizeWindowsPaths(snapshot);
    var replacements = computeStringReplacements(snapshot);
    Map<String, String> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : snapshot.entrySet()) {
      normalized.put(
        entry.getKey(),
        normalizeEntry(entry.getValue(), replacements, excludedPaths)
      );
    }
    return normalized;
  }

  private static String normalizeEntry(String value, Map<String, String> replacements, Set<String> excludedPaths) {
    String result = value;
    for (Map.Entry<String, String> entry : replacements.entrySet()) {
      result = result.replace(entry.getKey(), entry.getValue());
    }
    result = applyRegexReplacements(result);
    return filterAndSort(result, excludedPaths);
  }

  private static String applyRegexReplacements(String value) {
    String result = value;
    for (Map.Entry<Pattern, String> entry : REGEX_REPLACEMENT.entrySet()) {
      result = entry.getKey().matcher(result).replaceAll(entry.getValue());
    }

    for (Map.Entry<String, String> entry : STRING_REPLACEMENT.entrySet()) {
      result = result.replace(entry.getKey(), entry.getValue());
    }

    return result;
  }

  private static Map<String, String> computeStringReplacements(Map<String, String> snapshot) {
    Map<String, String> replacements = new LinkedHashMap<>();
    ANDROID_SDK_ROOTS.forEach(entry -> replacements.put(entry, ANDROID_SDK_PLACEHOLDER));
    jdkRoot(snapshot).ifPresent(jdkRoot -> replacements.put(jdkRoot, SONAR_JDK_PLACEHOLDER));
    for (Map.Entry<String, String> entry : PROPERTIES_REPLACEMENT.entrySet()) {
      var key = entry.getKey();
      var placeHolder = entry.getValue();
      var value = snapshot.get(key);
      Optional.ofNullable(value).ifPresent(v -> replacements.put(v, placeHolder));
    }
    return replacements;
  }

  private static Optional<String> jdkRoot(Map<String, String> snapshot) {
    for (Map.Entry<String, String> entry : snapshot.entrySet()) {
      var key = entry.getKey();
      if (key.endsWith("sonar.java.jdkHome")) {
        return Optional.of(entry.getValue());
      }
    }
    return Optional.empty();
  }

  private static List<String> listOfPaths(@Nullable String... entries) {
    return Arrays.stream(entries).filter(Objects::nonNull).map(PathsNormalizer::normalizeWindowsPath).collect(Collectors.toList());
  }

  private static Map<String, String> normalizeWindowsPaths(Map<String, String> snapshot) {
    Map<String, String> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : snapshot.entrySet()) {
      normalized.put(entry.getKey(), normalizeWindowsPath(entry.getValue()));
    }
    return normalized;
  }

  private static String normalizeWindowsPath(String value) {
    return WINDOWS_DRIVE_MATCHER
      .matcher(value).replaceAll("") // drop drive letter
      .replace("\\", "/");
  }

  private static <K, V> Map<K, V> linkedHashMapOf(K key, V value, Object... moreEntries) {
    Map<K, V> map = new LinkedHashMap<>();
    map.put(key, value);
    for (int i = 0; i < moreEntries.length; i += 2) {
      @SuppressWarnings("unchecked")
      K k = (K) moreEntries[i];
      @SuppressWarnings("unchecked")
      V v = (V) moreEntries[i + 1];
      map.put(k, v);
    }
    return map;
  }

  private static String filterAndSort(String value, Set<String> excludedPaths) {
    return Arrays.stream(value.split(","))
      .filter(entry -> !excludedPaths.contains(entry))
      .sorted()
      .collect(Collectors.joining(","));
  }
}
