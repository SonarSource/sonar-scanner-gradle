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
package org.sonarqube.gradle;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;
import org.sonarqube.gradle.run_configuration.RunConfigurationList;
import org.sonarqube.gradle.support.AbstractGradleIT;

import static org.assertj.core.api.Assertions.assertThat;

/** Gradle 9.7 contract tests for project isolation, run by the matching QA job. */
public class IsolatedProjectsTest extends AbstractGradleIT {
  private static final String ISOLATION = "--isolated-projects";

  @Test
  public void projectMetadataArtifactsCrossIsolationBoundary() throws Exception {
    ignoreThisTestIfGradleVersionIsLessThan("9.7.0");
    Map<String, String> env = Map.of("GRADLE_USER_HOME", temp.newFolder("isolated-gradle-home").getAbsolutePath());

    RunResult first = runGradlewWithEnvQuietly("/isolated-metadata-aggregation", env, noExtraConfiguration(),
      "aggregateSonarMetadata", ISOLATION);
    RunResult second = runGradlewWithEnvQuietly("/isolated-metadata-aggregation", env, noExtraConfiguration(),
      "aggregateSonarMetadata", ISOLATION);

    assertThat(first.getExitValue()).as(first.getLog()).isZero();
    assertThat(first.getLog()).contains("SONAR_METADATA=:,:child", "> Task :child:writeSonarMetadata");
    assertThat(second.getExitValue()).isZero();
    assertThat(second.getLog()).contains("Reusing configuration cache", "SONAR_METADATA=:,:child");
  }

  @Test
  public void scannerAggregatesChildPropertiesUnderIsolation() throws Exception {
    ignoreThisTestIfGradleVersionIsLessThan("9.7.0");
    Map<String, String> env = Map.of("GRADLE_USER_HOME", temp.newFolder("isolated-gradle-home").getAbsolutePath());

    String dumpArg = "-Dsonar.scanner.internal.dumpToFile=" + temp.newFile().getAbsolutePath();
    RunResult first = runGradlewWithEnvQuietly("/isolated-java-projects", env, noExtraConfiguration(),
      ":child:classes", ":sonar", ISOLATION, dumpArg);
    RunResult second = runGradlewWithEnvQuietly("/isolated-java-projects", env, noExtraConfiguration(),
      ":child:classes", ":sonar", ISOLATION, dumpArg);

    assertThat(first.getExitValue()).as(first.getLog()).isZero();
    assertThat(first.getLog()).contains("> Task :child:sonarResolver", "> Task :sonar");
    assertThat(second.getExitValue()).isZero();
    assertThat(second.getLog()).contains("Reusing configuration cache");

    Properties properties = second.getDumpedProperties().orElseThrow();
    assertThat(properties).containsEntry("sonar.projectKey", "isolated-java-root")
      .containsEntry("sonar.modules", ":child")
      .containsEntry(":child.sonar.projectName", "Isolated child")
      .containsEntry(":child.sonar.moduleKey", "isolated-java-root:child");
    assertThat(properties.getProperty(":child.sonar.sources").replace('\\', '/')).contains("src/main/java");
    assertThat(properties.getProperty(":child.sonar.java.binaries")).contains("classes");
  }

  private static RunConfigurationList noExtraConfiguration() {
    return new RunConfigurationList(List.of());
  }
}
