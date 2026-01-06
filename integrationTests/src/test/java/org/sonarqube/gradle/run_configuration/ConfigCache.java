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
 * Enable gradle configuration cache during run.
 * We have too much problem testing configuration cache with version gradle versions lower than 8.
 * If you use the configuration cache, it is unlikely that you use a version of gradle lower than 8.
 */
public class ConfigCache implements RunConfiguration {
  @Override
  public void updateProcessArgument(List<String> arguments) {
    if (AbstractGradleIT.getGradleVersion().isLowerThan("8.0.0")) {
      return;
    }

    arguments.add("--configuration-cache");
    arguments.add("--info");
  }

  @Override
  public void checkOutput(AbstractGradleIT.RunResult result) {
    if (AbstractGradleIT.getGradleVersion().isLowerThan("8.0.0")) {
      return;
    }

    String log = result.getLog();
    boolean mustContainAtLeastOne = log.contains("0 problems were found storing the configuration cache.")
      || log.contains("Configuration cache entry stored");
    boolean mustNotContain = log.contains("Configuration cache problems found")
      || log.contains("configuration cache problem")
      || log.contains("Configuration cache entry discarded because incompatible task was found: ':sonar'");

    if (mustNotContain || !mustContainAtLeastOne
    ) {
      throw new CheckException("problem found with configuration cache:\n" + log);
    }
  }
}
