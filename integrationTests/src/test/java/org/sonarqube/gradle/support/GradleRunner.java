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

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.gradle.run_configuration.RunConfiguration;

public final class GradleRunner {
  private static final String DUMP_PROPERTY = "-Dsonar.scanner.internal.dumpToFile=";
  private static final String SONAR_TASK = "sonar";
  private static final List<String> BASE_ARGS = List.of("--stacktrace", "--no-daemon", "--warning-mode", "all");
  private static final String BASE_GRADLE_OPTS = "-Xmx1024m";
  private static final String JAVA_9_OPTS = " --add-opens=java.prefs/java.util.prefs=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED";

  private GradleRunner() {
  }

  static AbstractGradleIT.RunResult runSonar(TemporaryFolder temp, String project, @Nullable String subdir, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    AbstractGradleIT.RunResult result = runSonarQuietly(temp, project, subdir, env, config, args);
    System.out.println(result.getLog());
    if (result.getExitValue() != 0) throw new RuntimeException(result.getLog());
    return result;
  }

  static Properties runSonarSimulation(TemporaryFolder temp, String project, @Nullable String subdir, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    File dumpFile = temp.newFile();
    String[] allArgs = new String[args.length + 1];
    allArgs[0] = DUMP_PROPERTY + dumpFile.getAbsolutePath();
    System.arraycopy(args, 0, allArgs, 1, args.length);
    return runSonar(temp, project, subdir, env, config, allArgs).getDumpedProperties().orElseThrow(() -> new IllegalStateException("Expected dumped properties for " + project));
  }

  static AbstractGradleIT.RunResult runSonarQuietly(TemporaryFolder temp, String project, @Nullable String subdir, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    List<String> sonarArgs = new ArrayList<>(Arrays.asList(args));
    sonarArgs.add(SONAR_TASK);
    return runQuietly(temp, project, subdir, env, config, sonarArgs.toArray(String[]::new));
  }

  static AbstractGradleIT.RunResult runQuietly(TemporaryFolder temp, String project, @Nullable String subdir, Map<String, String> env, RunConfiguration config, String... args) throws Exception {
    File executionDir = prepareExecutionDir(temp, project, subdir);
    File outputFile = temp.newFile();
    List<String> command = command(executionDir, config, args);
    ProcessBuilder builder = new ProcessBuilder(command).directory(executionDir).redirectOutput(outputFile).redirectErrorStream(true);
    builder.environment().put("GRADLE_OPTS", GradleRuntime.javaVersion() > 8 ? BASE_GRADLE_OPTS + JAVA_9_OPTS : BASE_GRADLE_OPTS);
    builder.environment().putAll(env);
    Process process = builder.start();
    process.waitFor();
    String output = FileUtils.readFileToString(outputFile, StandardCharsets.UTF_8);
    GradleDeprecationChecker.assertNoUnexpectedWarnings(output);
    AbstractGradleIT.RunResult result = new AbstractGradleIT.RunResult(output, process.exitValue(), getDumpedProperties(command));
    config.checkOutput(result);
    return result;
  }

  static List<String> gradlewCommand() {
    return GradleRuntime.isWindows() ? new ArrayList<>(List.of("cmd.exe", "/C", "gradlew.bat")) : new ArrayList<>(List.of("/bin/bash", "gradlew"));
  }

  static @Nullable Properties getDumpedProperties(List<String> command) {
    return command.stream().filter(arg -> arg.startsWith(DUMP_PROPERTY)).findFirst().map(arg -> arg.substring(DUMP_PROPERTY.length())).map(GradleRunner::loadProperties).orElse(null);
  }

  static File prepareExecutionDir(TemporaryFolder temp, String project, @Nullable String subdir) throws Exception {
    File sourceDir = new File(GradleRunner.class.getResource(project).toURI());
    String copyName = project.startsWith("/") ? "." + project : project;
    File projectDir = new File(temp.getRoot(), copyName);
    if (!projectDir.exists()) projectDir = temp.newFolder(copyName);
    FileUtils.copyDirectory(sourceDir, projectDir);
    return subdir == null ? projectDir : new File(projectDir, subdir);
  }

  static List<String> command(File executionDir, RunConfiguration config, String... args) {
    List<String> command = GradleRuntime.isWindows() ? new ArrayList<>(List.of("cmd.exe", "/C", new File(executionDir, "gradlew.bat").getAbsolutePath())) : new ArrayList<>(List.of("/bin/bash", "gradlew"));
    command.addAll(BASE_ARGS);
    config.updateProcessArgument(command);
    command.addAll(Arrays.asList(args));
    return command;
  }

  static Properties loadProperties(String path) {
    try (FileReader reader = new FileReader(path)) {
      Properties properties = new Properties();
      properties.load(reader);
      return properties;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load dumped properties from " + path, e);
    }
  }
}
