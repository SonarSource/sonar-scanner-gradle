/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support;

import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.Semver.SemverType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;

final class GradleTestVersions {
  private static final String NOT_AVAILABLE = "NOT_AVAILABLE";
  private static final Semver GRADLE_VERSION = load("/gradleversion.txt");
  private static final @Nullable Semver ANDROID_GRADLE_VERSION = loadOptional("/androidgradleversion.txt");

  private GradleTestVersions() {
  }

  static Semver gradleVersion() {
    return GRADLE_VERSION;
  }

  static @Nullable Semver androidGradleVersion() {
    return ANDROID_GRADLE_VERSION;
  }

  private static Semver load(String resource) {
    try {
      return new Semver(IOUtils.toString(GradleTestVersions.class.getResource(resource), StandardCharsets.UTF_8), SemverType.LOOSE);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load " + resource, e);
    }
  }

  private static @Nullable Semver loadOptional(String resource) {
    try {
      String value = IOUtils.toString(GradleTestVersions.class.getResource(resource), StandardCharsets.UTF_8);
      return NOT_AVAILABLE.equals(value) ? null : new Semver(value, SemverType.LOOSE);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load " + resource, e);
    }
  }
}
