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
package org.sonarqube.gradle.support;

import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.Semver.SemverType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;

public final class GradleRuntime {
  private static final String NOT_AVAILABLE = "NOT_AVAILABLE";
  private static final String GRADLE_VERSION_RESOURCE = "/gradleversion.txt";
  private static final String ANDROID_GRADLE_VERSION_RESOURCE = "/androidgradleversion.txt";
  private static final Semver GRADLE_VERSION = loadGradleVersion();
  private static final @Nullable Semver ANDROID_GRADLE_VERSION = loadAndroidGradleVersion();

  private GradleRuntime() {
  }

  static Semver gradleVersion() {
    return GRADLE_VERSION;
  }

  static @Nullable Semver androidGradleVersion() {
    return ANDROID_GRADLE_VERSION;
  }

  static boolean isWindows() {
    return System.getProperty("os.name").startsWith("Windows");
  }

  static int javaVersion() {
    String version = System.getProperty("java.version");
    String normalized = version.startsWith("1.") ? version.substring(2, 3) : version.split("\\.")[0];
    return Integer.parseInt(normalized);
  }

  private static Semver loadGradleVersion() {
    try {
      return new Semver(IOUtils.toString(GradleRuntime.class.getResource(GRADLE_VERSION_RESOURCE), StandardCharsets.UTF_8), SemverType.LOOSE);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load " + GRADLE_VERSION_RESOURCE, e);
    }
  }

  private static @Nullable Semver loadAndroidGradleVersion() {
    try {
      String value = IOUtils.toString(GradleRuntime.class.getResource(ANDROID_GRADLE_VERSION_RESOURCE), StandardCharsets.UTF_8);
      return NOT_AVAILABLE.equals(value) ? null : new Semver(value, SemverType.LOOSE);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load " + ANDROID_GRADLE_VERSION_RESOURCE, e);
    }
  }
}
