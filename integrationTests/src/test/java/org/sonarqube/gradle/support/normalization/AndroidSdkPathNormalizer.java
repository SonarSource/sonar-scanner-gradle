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
import org.jspecify.annotations.Nullable;

public final class AndroidSdkPathNormalizer {
  private static final String ANDROID_SDK_PLACEHOLDER = "${ANDROID_SDK}";
  private static final List<String> DEFAULT_ROOTS = List.of("/usr/local/lib/android/sdk", "C:/Android/Sdk", "C:\\Android\\Sdk");

  private AndroidSdkPathNormalizer() {
    // Utility class: contains only static methods and is not intended to be instantiated.
  }

  public static String normalize(String value) {
    String result = value;
    for (String root : knownRoots()) result = ComparableProperties.replacePrefix(result, root, ANDROID_SDK_PLACEHOLDER);
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
