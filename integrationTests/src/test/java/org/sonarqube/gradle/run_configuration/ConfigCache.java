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
package org.sonarqube.gradle.run_configuration;


import java.util.List;
import org.sonarqube.gradle.AbstractGradleIT;

/**
 * Enable gradle configuration cache during run.
 */
public class ConfigCache implements RunConfiguration {
  @Override
  public void updateProcessArgument(List<String> arguments) {
    arguments.add("--configuration-cache");
    arguments.add("--info");
  }

  @Override
  public void checkOutput(AbstractGradleIT.RunResult result) {
    // do nothing sonar task not compatible
  }
}
