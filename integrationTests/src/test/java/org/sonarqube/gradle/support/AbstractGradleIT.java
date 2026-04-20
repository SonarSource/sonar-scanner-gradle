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
package org.sonarqube.gradle.support;

import com.vdurmont.semver4j.Semver;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.gradle.run_configuration.RunConfiguration;

public abstract class AbstractGradleIT {
  @Rule
  public final TemporaryFolder temp = TemporaryFolder.builder().build();

  public static Semver getGradleVersion() {
    return GradleRuntime.gradleVersion();
  }

  public static @Nullable Semver getAndroidGradleVersion() {
    return GradleRuntime.androidGradleVersion();
  }

  protected static int getJavaVersion() {
    return GradleRuntime.javaVersion();
  }

  protected static void ignoreThisTestIfGradleVersionIsLessThan(String version) {
    Assume.assumeTrue(getGradleVersion().isGreaterThanOrEqualTo(version));
  }

  protected static void ignoreForAgp9Plus() {
    Assume.assumeFalse(getAndroidGradleVersion().isGreaterThanOrEqualTo("9.0.0"));
  }

  protected static void ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo(String version) {
    Assume.assumeTrue(getGradleVersion().isLowerThan(version));
  }

  protected static void ignoreThisTestIfGradleVersionIsNotBetween(String minVersionIncluded, String maxVersionExcluded) {
    ignoreThisTestIfGradleVersionIsLessThan(minVersionIncluded);
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo(maxVersionExcluded);
  }


  protected static @Nullable Properties getDumpedProperties(List<String> command) {
    return GradleRunner.getDumpedProperties(command);
  }

  protected Properties runGradlewSonarSimulationModeWithEnv(String project, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    return runGradlewSonarSimulationModeWithEnv(project, null, env, config, args);
  }

  public Properties runGradlewSonarSimulationModeWithEnv(String project, @Nullable String subdir, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    return GradleRunner.runSonarSimulation(temp, project, subdir, env, config, args);
  }

  public Properties runGradlewSonarSimulationModeWithVersions(
    String project,
    @Nullable String subdir,
    Map<String, String> env,
    RunConfiguration config,
    @Nullable String gradleVersion,
    @Nullable String androidGradleVersion,
    String... args
  ) throws Exception {
    return GradleRunner.runSonarSimulation(temp, project, subdir, env, config, gradleVersion, androidGradleVersion, args);
  }

  protected RunResult runGradlewSonarWithEnv(String project, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    return GradleRunner.runSonar(temp, project, null, env, config, args);
  }

  protected RunResult runGradlewSonarWithEnvQuietly(String project, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    return GradleRunner.runSonarQuietly(temp, project, null, env, config, args);

  }

  protected RunResult runGradlewWithEnvQuietly(String project, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    return GradleRunner.runQuietly(temp, project, null, env, config, args);
  }

  protected List<String> getGradlewCommand() {
    return GradleRunner.gradlewCommand();
  }

  public static final class RunResult {
    private final String log;
    private final int exitValue;
    private final @Nullable Properties dumpedProperties;

    public RunResult(String log, int exitValue, @Nullable Properties dumpedProperties) {
      this.log = log;
      this.exitValue = exitValue;
      this.dumpedProperties = dumpedProperties;
    }

    public String getLog() {
      return log;
    }

    public int getExitValue() {
      return exitValue;
    }

    public java.util.Optional<Properties> getDumpedProperties() {
      return java.util.Optional.ofNullable(dumpedProperties);
    }
  }
}
