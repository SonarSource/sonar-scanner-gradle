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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class AndroidPathNormalizer {
  private static final String HOME_PLACEHOLDER = "${HOME}";
  private static final Pattern BUILD_TOOLS = Pattern.compile("/build-tools/\\d+\\.\\d+\\.\\d+/core-lambda-stubs\\.jar");
  private static final Pattern VERSIONED_TRANSFORMS = Pattern.compile("\\.gradle/caches/\\d+\\.\\d+/transforms/[a-f0-9]+");
  private static final Pattern STABLE_TRANSFORMS = Pattern.compile("\\.gradle/caches/transforms-3/[a-f0-9]+");
  private static final Pattern PROJECT_TRANSFORMS = Pattern.compile("/build/\\.transforms/[a-f0-9]+");
  private static final Pattern COMPILE_CLASSES = Pattern.compile("/compile[a-zA-Z0-9]+JavaWithJavac/classes");
  private static final Pattern JAVAC_CLASSES = Pattern.compile("/javac/[a-zA-Z0-9]+/classes");
  private static final Pattern R_JAR = Pattern.compile("/(process|generate)[a-zA-Z0-9]+(Resources|RFile|StubRFile)/R\\.jar");
  private static final Pattern CLASSES_JAR = Pattern.compile("/bundle[a-zA-Z0-9]+(ClassesToCompileJar|CompileToJar[a-zA-Z0-9]+)/classes\\.jar");
  private static final Pattern ENTRY = Pattern.compile("(?<=,|^)([^,]+)(?=,|$)");
  private static final Pattern JETIFIED = Pattern.compile(".*/(?:\\.gradle/caches/transforms-\\d+|build/\\.transforms)/<hash>/transformed/(?:instrumented_)?(jetified-[^,/]+\\" +
    ".jar)");
  private static final String ANDROID_SDK_PLACEHOLDER = "${ANDROID_SDK}";
  private static final List<String> DEFAULT_ROOTS = List.of("/usr/local/lib/android/sdk", "C:/Android/Sdk", "C:\\Android\\Sdk");

  private AndroidPathNormalizer() {
    // Utility class: contains only static methods and is not intended to be instantiated.
  }

  public static String normalize(String value) {
    String result = normalizeWindowsPath(value);
    result = normalizeAndroidSDK(result);
    result = BUILD_TOOLS.matcher(result).replaceAll("/{CORE_LAMBDA_STUBS_JAR}");
    result = VERSIONED_TRANSFORMS.matcher(result).replaceAll(".gradle/caches/transforms-3/<hash>");
    result = STABLE_TRANSFORMS.matcher(result).replaceAll(".gradle/caches/transforms-3/<hash>");
    result = PROJECT_TRANSFORMS.matcher(result).replaceAll("/build/.transforms/<hash>");
    result = COMPILE_CLASSES.matcher(result).replaceAll("/classes");
    result = JAVAC_CLASSES.matcher(result).replaceAll("/classes");
    result = R_JAR.matcher(result).replaceAll("/R.jar");
    result = CLASSES_JAR.matcher(result).replaceAll("/classes.jar");
    result = result.replace("/compile_app_classes_jar/", "/app_classes/").replace("/compile_library_classes_jar/", "/library_classes/");
    return normalizeJetifiedEntries(result);
  }

  private static String normalizeJetifiedEntries(String value) {
    Matcher matcher = ENTRY.matcher(value);
    StringBuilder buffer = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(buffer, normalizeJetifiedEntry(matcher.group(1)));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private static String normalizeJetifiedEntry(String entry) {
    Matcher jarMatcher = JETIFIED.matcher(entry);
    if (!jarMatcher.matches()) {
      return Matcher.quoteReplacement(entry);
    }
    return Matcher.quoteReplacement(HOME_PLACEHOLDER + "/.gradle/caches/transforms-3/<hash>/transformed/" + jarMatcher.group(1));
  }


  private static String normalizeAndroidSDK(String value) {
    String result = value;
    for (String root : knownRoots()) {
      result = ComparableProperties.replacePrefix(result, root, ANDROID_SDK_PLACEHOLDER);
    }
    return result;
  }

  private static List<String> knownRoots() {
    List<String> roots = new ArrayList<>();
    add(roots, System.getenv("ANDROID_HOME"));
    add(roots, System.getenv("ANDROID_SDK_ROOT"));
    DEFAULT_ROOTS.forEach(root -> add(roots, root));
    return roots;
  }

  private static void add(List<String> roots, @Nullable String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return;
    }
    String normalized = normalizeWindowsPath(candidate);
    roots.add(normalized);
    Path path = Paths.get(normalized);
    if (Files.exists(path)) {
      roots.add(normalizeWindowsPath(path.toAbsolutePath().normalize().toString()));
    }
  }

  private static String normalizeWindowsPath(String value) {
    return value.replace('\\', '/');
  }
}
