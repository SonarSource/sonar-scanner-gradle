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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.util.GradleVersion;

/**
 * Applies {@link SonarQubePlugin} to every project of the build, before its build script is evaluated.
 * <p>
 * This is the only application mode that is compatible with Gradle Isolated Projects without editing every build
 * script, because a project cannot register tasks or extensions in another project.
 */
public class SonarSettingsPlugin implements Plugin<Settings> {
  private static final String LIFECYCLE_API_GRADLE_VERSION = "8.8";

  @Override
  public void apply(Settings settings) {
    if (GradleVersion.current().compareTo(GradleVersion.version(LIFECYCLE_API_GRADLE_VERSION)) >= 0) {
      Gradle8dot8Utils.applyToEveryProject(settings.getGradle());
    } else {
      settings.getGradle().beforeProject(SonarSettingsPlugin::applyProjectPlugin);
    }
  }

  /**
   * Without Isolated Projects, the root project keeps configuring its subprojects, exactly as when the plugin is applied
   * in the root build script. Gradle runs this action lazily, when the root first accesses a subproject, so applying
   * the plugin to that subproject as well would register its extensions twice.
   */
  static void applyProjectPlugin(Project project) {
    if (project.getPath().equals(":") || IsolatedProjects.isEnabled(project.getGradle())) {
      project.getPluginManager().apply(SonarQubePlugin.class);
    }
  }
}
