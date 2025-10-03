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
package org.sonarqube.gradle.resolver;

import org.gradle.StartParameter;
import org.sonarqube.gradle.SonarResolverTask;

@SuppressWarnings("java:S6813")
public class StartParameterBasedTask extends SonarResolverTask {
  @Override
  public boolean configurationCacheIsDisabled() {
    StartParameter startParameter = getProject().getGradle().getStartParameter();
    return startParameter.isConfigurationCacheRequested();
  }
}
