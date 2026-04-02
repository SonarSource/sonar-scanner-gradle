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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GradleCachePathNormalizer {
  private static final Pattern DEPENDENCY = Pattern.compile(
    "(?<=,|^)[^,]+/.gradle/caches/modules-\\d+/files-[0-9.]+/(?<groupId>[^/,]+?)/(?<artifactId>[^/,]+?)/(?<version>[^/,]+?)/[0-9a-f]{40}/\\k<artifactId>-\\k<version>\\.jar(?=,|$)");
  private static final Pattern GRADLE_TRANSFORMS = Pattern.compile("(?<=,|^)(?<prefix>[^,]*/\\.gradle/caches/transforms-\\d+/)[0-9a-f]+(?<suffix>/transformed/[^,]+)(?=,|$)");
  private static final Pattern PROJECT_TRANSFORMS = Pattern.compile("(?<=,|^)(?<prefix>[^,]*/build/\\.transforms/)[0-9a-f]+(?<suffix>/transformed/[^,]+)(?=,|$)");

  private GradleCachePathNormalizer() {
    // Utility class: contains only static methods and is not intended to be instantiated.
  }

  static String normalize(String value) {
    String dependencies = normalizeDependencies(value);
    String gradleTransforms = GRADLE_TRANSFORMS.matcher(dependencies).replaceAll("${prefix}<hash>${suffix}");
    return PROJECT_TRANSFORMS.matcher(gradleTransforms).replaceAll("${prefix}<hash>${suffix}");
  }

  private static String normalizeDependencies(String value) {
    Matcher matcher = DEPENDENCY.matcher(value);
    StringBuilder buffer = new StringBuilder();
    while (matcher.find()) {
      String replacement = "${M2}/repository/"
        + matcher.group("groupId").replace(".", "/")
        + "/"
        + matcher.group("artifactId")
        + "/"
        + matcher.group("version")
        + "/"
        + matcher.group("artifactId")
        + "-"
        + matcher.group("version")
        + ".jar";
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }
}
