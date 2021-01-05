/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2021 SonarSource
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

import java.util.Optional;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.compile.CompileOptions;

/**
 * Only access this class if runtime is Gradle 6.6+
 */
public class Gradle6dot6Utils {

  private Gradle6dot6Utils() {
    // Utility class
  }

  static Optional<String> getRelease(CompileOptions options) {
    Property<Integer> release = options.getRelease();
    if (release.isPresent()) {
      return Optional.of(release.get().toString());
    } else {
      return Optional.empty();
    }
  }

}
