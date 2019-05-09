/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2019 SonarSource SA
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
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public abstract class AbstractGradleIT {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private static String gradleVersion;
  static {
    try {
      gradleVersion = IOUtils.toString(AbstractGradleIT.class.getResource("/gradleversion.txt"), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  protected static String getGradleVersion() {
    return gradleVersion;
  }

  protected Properties runGradlewSonarQubeSimulationMode(String project) throws Exception {
    return runGradlewSonarQubeSimulationModeWithEnv(project, Collections.emptyMap());
  }

  protected Properties runGradlewSonarQubeSimulationModeWithEnv(String project, Map<String, String> env, String... args) throws Exception {
    File out = temp.newFile();
    String[] newArgs = Stream.concat(
      Stream.of("-Dsonar.scanner.dumpToFile=" + out.getAbsolutePath()),
      Arrays.stream(args))
      .toArray(String[]::new);
    runGradlewSonarQubeWithEnv(project, env, newArgs);

    Properties props = new Properties();
    try (FileReader fr = new FileReader(out)) {
      props.load(fr);
    }
    return props;
  }

  protected RunResult runGradlewSonarQubeWithEnv(String project, Map<String, String> env, String... args) throws Exception {
    RunResult result = runGradlewSonarQubeWithEnvQuietly(project, env, args);
    if (result.exitValue != 0) {
      throw new RuntimeException(result.log);
    }
    return result;
  }

  protected RunResult runGradlewSonarQubeWithEnvQuietly(String project, Map<String, String> env, String... args) throws Exception {
    List<String> newArgs = new ArrayList<>(args.length + 1);
    newArgs.addAll(Arrays.asList(args));
    newArgs.add("sonarqube");
    return runGradlewWithEnvQuietly(project, env, newArgs.toArray(new String[args.length + 1]));
  }

  protected RunResult runGradlewWithEnvQuietly(String project, Map<String, String> env, String... args) throws Exception {
    File projectBaseDir = new File(this.getClass().getResource(project).toURI());
    File tempProjectDir = temp.newFolder(project);
    File outputFile = temp.newFile();
    FileUtils.copyDirectory(projectBaseDir, tempProjectDir);
    List<String> command = new ArrayList<>();
    if (System.getProperty("os.name").startsWith("Windows")) {
      command.addAll(Arrays.asList("cmd.exe", "/C"));
    } else {
      command.add("/bin/bash");
    }
    command.addAll(Arrays.asList("gradlew", "--stacktrace", "--no-daemon"));
    command.addAll(Arrays.asList(args));
    ProcessBuilder pb = new ProcessBuilder(command)
        .directory(tempProjectDir)
        .redirectOutput(outputFile)
        .redirectErrorStream(true);
    pb.environment().put("GRADLE_OPTS", "-Xmx1024m");
    pb.environment().putAll(env);
    Process p = pb.start();
    p.waitFor();

    String output = FileUtils.readFileToString(outputFile, StandardCharsets.UTF_8);

    return new RunResult(output, p.exitValue());
  }

  protected static class RunResult {
    private String log;
    private int exitValue;

    RunResult(String log, int exitValue) {
      this.log = log;
      this.exitValue = exitValue;
    }

    public String getLog() {
      return log;
    }

    public int getExitValue() {
      return exitValue;
    }
  }
}
