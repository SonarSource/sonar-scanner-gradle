/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support.normalization;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.sonarqube.gradle.support.PrefixReplacements;

public final class AndroidSdkPathNormalizer {
  private static final String ANDROID_SDK_PLACEHOLDER = "${ANDROID_SDK}";
  private static final List<String> DEFAULT_ROOTS = List.of("/usr/local/lib/android/sdk", "C:/Android/Sdk", "C:\\Android\\Sdk");

  private AndroidSdkPathNormalizer() {
  }

  public static String normalize(String value) {
    String result = value;
    for (String root : knownRoots()) {
      result = PrefixReplacements.replacePrefix(result, root, ANDROID_SDK_PLACEHOLDER);
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
    String normalized = candidate.replace('\\', '/');
    roots.add(normalized);
    Path path = Paths.get(normalized);
    if (Files.exists(path)) {
      roots.add(path.toAbsolutePath().normalize().toString().replace('\\', '/'));
    }
  }
}
