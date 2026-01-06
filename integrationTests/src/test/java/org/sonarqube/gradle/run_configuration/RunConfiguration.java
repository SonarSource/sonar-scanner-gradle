/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2026 SonarSource SA
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
 * Configure some process arguments and check to perform on the output for an integration test.
 */
public interface RunConfiguration {

  class CheckException extends RuntimeException {

    public CheckException(String message) {
      super(message);
    }
  }

  /**
   * Modify the process argument of the run. Several run configurations can be applied; the application order is important.
   *
   * @param arguments the current process arguments
   */
  void updateProcessArgument(List<String> arguments);

  /**
   * If the check fail return a {@code CheckException}
   *
   * @param result result of the execution of the run.
   */
  void checkOutput(AbstractGradleIT.RunResult result);
}
