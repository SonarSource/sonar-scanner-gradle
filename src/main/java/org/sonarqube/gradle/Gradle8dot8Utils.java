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

import org.gradle.api.IsolatedAction;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;

/**
 * Only access this class if runtime is Gradle 8.8+, which introduced {@code gradle.lifecycle}.
 */
final class Gradle8dot8Utils {

  private Gradle8dot8Utils() {
    // Utility class
  }

  static void applyToEveryProject(Gradle gradle) {
    gradle.getLifecycle().beforeProject(new ApplySonarProjectPlugin());
  }

  /** Runs in the private context of each project, which is what Isolated Projects requires. */
  private static final class ApplySonarProjectPlugin implements IsolatedAction<Project> {
    @Override
    public void execute(Project project) {
      SonarSettingsPlugin.applyProjectPlugin(project);
    }
  }
}
