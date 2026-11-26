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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;

/**
 * Implementation of the {@code org.sonarqube} plugin id. The same id can be applied in a settings script, which applies
 * the scanner to every project of the build, or in a build script, which keeps the historical behavior.
 * <p>
 * Each target is delegated to its own plugin class, so {@code plugins.hasPlugin(SonarQubePlugin)} keeps working.
 */
public class SonarEntryPointPlugin implements Plugin<Object> {

  @Override
  public void apply(Object target) {
    if (target instanceof Settings) {
      ((Settings) target).getPluginManager().apply(SonarSettingsPlugin.class);
    } else if (target instanceof Project) {
      ((Project) target).getPluginManager().apply(SonarQubePlugin.class);
    } else {
      throw new InvalidUserDataException("The 'org.sonarqube' plugin can only be applied in a settings script or in a build script, not to "
        + target + ".");
    }
  }
}
