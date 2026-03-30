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

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.Ignore;
import org.junit.Test;
import org.sonarqube.gradle.run_configuration.DefaultRunConfiguration;
import org.sonarqube.gradle.run_configuration.RunConfigurationList;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class GradleTest extends AbstractGradleIT {

  /**
   * SONARGRADL-100
   */
  @Test
  public void testDebugModeEnabled() {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    assertThatCode(() -> runGradlewSonarSimulationModeWithEnv("/java-gradle-simple", emptyMap(), new DefaultRunConfiguration(), "-d")).doesNotThrowAnyException();
  }

  @Test
  public void testSetLogLevel() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    File output = temp.newFile("will-not-be-used-for-this-test.txt");
    String args = String.format("-Dsonar.scanner.internal.dumpToFile=%s", output.getAbsolutePath());
    RunResult runResult = runGradlewSonarWithEnv("/java-gradle-log-level", emptyMap(), new DefaultRunConfiguration(), args);
    assertThat(runResult.getLog()).contains(":sonar");
  }

  @Ignore("TODO SCANGRADLE-159: sonar.scanner.skip does not prevent reaching to the server!")
  @Test
  public void testSkip() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("SONARQUBE_SCANNER_PARAMS", "{\"sonar.scanner.skip\" : \"true\" }");
    RunResult result = runGradlewSonarWithEnv("/java-gradle-simple", env, new DefaultRunConfiguration());

    System.out.println(result.getLog());
    assertThat(result.getExitValue()).isZero();
    assertThat(result.getLog()).contains("Sonar Scanner analysis skipped");
  }

  @Test
  public void testHostUrlInEnvErrorIncludingExecutionContextInformation() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    Map<String, String> env = new HashMap<>();
    env.put("SONAR_HOST_URL", "http://host-in-env");
    RunResult result = runGradlewSonarWithEnvQuietly("/java-gradle-simple", env, new DefaultRunConfiguration(), "--info");

    System.out.println(result.getLog());
    assertThat(result.getExitValue()).isEqualTo(1);
    assertThat(result.getLog())
      .containsPattern("org\\.sonarqube Gradle plugin \\d+\\.\\d+")
      .containsPattern("Java \\d+")
      .contains(" (64-bit)")
      .contains("GRADLE_OPTS=-Xmx1024m")
      .contains("org.sonarqube.gradle.AnalysisException")
      .contains("Call to URL [http://host-in-env/api/v2/analysis/version] failed");
  }

  @Test
  public void loadSonarScannerPropertiesEnv() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    Map<String, String> env = new HashMap<>();
    env.put("SONARQUBE_SCANNER_PARAMS", "{\"sonar.host.url\" : \"myhost\" }");
    Properties props = runGradlewSonarSimulationModeWithEnv("/java-gradle-simple", env, new DefaultRunConfiguration());

    assertThat(props).containsEntry("sonar.host.url", "myhost");
  }

  @Test
  public void testProjectWithConfigurationCacheDoubleExecutionsShouldWork() throws Exception {
    ignoreThisTestIfGradleVersionIsNotBetween("6.5.0", "9.0.0");

    String dumpProperty = String.format("-Dsonar.scanner.internal.dumpToFile=%s", temp.newFile().getAbsolutePath());

    runGradlewSonarWithEnv("/java-gradle-simple", emptyMap(), new RunConfigurationList(List.of()), dumpProperty, "--configuration-cache");
    RunResult runResult = runGradlewSonarWithEnv("/java-gradle-simple", emptyMap(), new RunConfigurationList(List.of()), dumpProperty, "--configuration-cache");

    assertThat(runResult.getLog()).contains("Reusing configuration cache.");
  }

  /**
   * SCANGRADLE-293
   *
   * <a href="file://../../../../resources/java-gradle-classpath-dependency/build.gradle.kts#L23">WriteToResources</a>
   * affects the classpath, SonarResolverTask depends on the classpath.
   * <p>
   * Verify that gradle do not report a conflict.
   */
  @Test
  public void testClasspathDependency() throws Exception {
    String propertyPath = this.temp.newFile().getAbsolutePath();
    RunResult result = runGradlewSonarWithEnvQuietly("/java-gradle-classpath-dependency",
      new HashMap<>(),
      new DefaultRunConfiguration(),
      "-Dsonar.scanner.internal.dumpToFile=" + propertyPath);

    assertThat(result.getExitValue()).isZero();
    assertThat(result.getLog()).doesNotContain("/resources/main");
  }
}
