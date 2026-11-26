/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2025 SonarSource
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarqube.gradle;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.util.GradleVersion;

/** Detects whether the current build runs with Gradle Isolated Projects. */
final class IsolatedProjects {

  static final String MINIMUM_GRADLE_VERSION = "9.7";

  private IsolatedProjects() {
    // Utility class
  }

  /** Gradle 9.7 exposes this option through StartParameterInternal, but not through the public StartParameter API. */
  static boolean isEnabled(Gradle gradle) {
    if (GradleVersion.current().compareTo(GradleVersion.version(MINIMUM_GRADLE_VERSION)) < 0) {
      return false;
    }
    return Boolean.TRUE.equals(((StartParameterInternal) gradle.getStartParameter()).getIsolatedProjects().get());
  }
}
