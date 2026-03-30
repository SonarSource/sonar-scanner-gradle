/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 */
package org.sonarqube.gradle.support;

import com.vdurmont.semver4j.Semver;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.gradle.support.normalization.ComparableProperties;
import org.sonarqube.gradle.support.snapshot.SnapshotPlaceholders;
import org.sonarqube.gradle.run_configuration.RunConfiguration;

public abstract class AbstractGradleIT {
  @Rule
  public final TemporaryFolder temp = TemporaryFolder.builder().build();

  public static Semver getGradleVersion() {
    return GradleTestVersions.gradleVersion();
  }

  public static @Nullable Semver getAndroidGradleVersion() {
    return GradleTestVersions.androidGradleVersion();
  }

  protected static int getJavaVersion() {
    return GradleTestEnvironment.javaVersion();
  }

  protected static void ignoreThisTestIfGradleVersionIsLessThan(String version) {
    Assume.assumeTrue(getGradleVersion().isGreaterThanOrEqualTo(version));
  }

  protected static void ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo(String version) {
    Assume.assumeTrue(getGradleVersion().isLowerThan(version));
  }

  protected static void ignoreThisTestIfGradleVersionIsNotBetween(String minVersionIncluded, String maxVersionExcluded) {
    ignoreThisTestIfGradleVersionIsLessThan(minVersionIncluded);
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo(maxVersionExcluded);
  }

  public static Map<String, String> extractComparableProperties(Properties properties) {
    return ComparableProperties.extract(properties);
  }

  public static Map<String, String> expandSnapshotPlaceholders(Map<String, String> expected, Map<String, String> actual) {
    return SnapshotPlaceholders.expand(expected, actual);
  }

  protected static @Nullable Properties getDumpedProperties(List<String> command) throws IOException {
    return GradleExecution.getDumpedProperties(command);
  }

  protected Properties runGradlewSonarSimulationModeWithEnv(String project, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    return runGradlewSonarSimulationModeWithEnv(project, null, env, config, args);
  }

  public Properties runGradlewSonarSimulationModeWithEnv(String project, @Nullable String subdir, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    return GradleExecution.runSonarSimulation(temp, project, subdir, env, config, args);
  }

  protected RunResult runGradlewSonarWithEnv(String project, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    return runGradlewSonarWithEnv(project, null, env, config, args);
  }

  protected RunResult runGradlewSonarWithEnv(String project, @Nullable String subdir, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    return GradleExecution.runSonar(temp, project, subdir, env, config, args);
  }

  protected RunResult runGradlewSonarWithEnvQuietly(String project, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    return runGradlewSonarWithEnvQuietly(project, null, env, config, args);
  }

  protected RunResult runGradlewSonarWithEnvQuietly(String project, @Nullable String subdir, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    return GradleExecution.runSonarQuietly(temp, project, subdir, env, config, args);
  }

  protected RunResult runGradlewWithEnvQuietly(String project, @Nullable String subdir, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    return GradleExecution.runQuietly(temp, project, subdir, env, config, args);
  }

  protected List<String> getGradlewCommand() {
    return GradleExecution.gradlewCommand();
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
