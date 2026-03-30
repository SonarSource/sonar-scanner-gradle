/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
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
