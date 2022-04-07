/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2022 SonarSource SA
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

import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.Semver.SemverType;
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

  private static Semver gradleVersion;
  private static Semver androidGradleVersion;

  static {
    try {
      gradleVersion = new Semver(IOUtils.toString(AbstractGradleIT.class.getResource("/gradleversion.txt"), StandardCharsets.UTF_8), SemverType.LOOSE);
      androidGradleVersion = new Semver(IOUtils.toString(AbstractGradleIT.class.getResource("/androidgradleversion.txt"), StandardCharsets.UTF_8), SemverType.LOOSE);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  protected static Semver getGradleVersion() {
    return gradleVersion;
  }

  protected static Semver getAndroidGradleVersion() {
    return androidGradleVersion;
  }

  protected Properties runGradlewSonarQubeSimulationMode(String project) throws Exception {
    return runGradlewSonarQubeSimulationModeWithEnv(project, Collections.emptyMap());
  }

  protected Properties runGradlewSonarQubeSimulationModeWithEnv(String project, Map<String, String> env, String... args) throws Exception {
    return runGradlewSonarQubeSimulationModeWithEnv(project, null, env, args);
  }

  protected Properties runGradlewSonarQubeSimulationModeWithEnv(String project, String exeRelativePath, Map<String, String> env, String... args) throws Exception {
    File out = temp.newFile();
    String[] newArgs = Stream.concat(
      Stream.of("-Dsonar.scanner.dumpToFile=" + out.getAbsolutePath()),
      Arrays.stream(args))
      .toArray(String[]::new);
    runGradlewSonarQubeWithEnv(project, exeRelativePath, env, newArgs);

    Properties props = new Properties();
    try (FileReader fr = new FileReader(out)) {
      props.load(fr);
    }
    return props;
  }

  protected RunResult runGradlewSonarQubeWithEnv(String project, Map<String, String> env, String... args) throws Exception {
    return runGradlewSonarQubeWithEnv(project, null, env, args);
  }

  protected RunResult runGradlewSonarQubeWithEnv(String project, String exeRelativePath, Map<String, String> env, String... args) throws Exception {
    RunResult result = runGradlewSonarQubeWithEnvQuietly(project, exeRelativePath, env, args);
    System.out.println(result.getLog());
    if (result.exitValue != 0) {
      throw new RuntimeException(result.log);
    }
    return result;
  }

  protected RunResult runGradlewSonarQubeWithEnvQuietly(String project, Map<String, String> env, String... args) throws Exception {
    return runGradlewSonarQubeWithEnvQuietly(project, null, env, args);
  }

  protected RunResult runGradlewSonarQubeWithEnvQuietly(String project, String exeRelativePath, Map<String, String> env, String... args) throws Exception {
    List<String> newArgs = new ArrayList<>(args.length + 1);
    newArgs.addAll(Arrays.asList(args));
    newArgs.add("sonarqube");
    return runGradlewWithEnvQuietly(project, exeRelativePath, env, newArgs.toArray(new String[args.length + 1]));
  }

  protected RunResult runGradlewWithEnvQuietly(String project, String exeRelativePath, Map<String, String> env, String... args) throws Exception {
    File projectBaseDir = new File(this.getClass().getResource(project).toURI());
    String projectDir = project.startsWith("/") ? "." + project : project;
    File tempProjectDir = temp.newFolder(projectDir);
    File outputFile = temp.newFile();
    FileUtils.copyDirectory(projectBaseDir, tempProjectDir);
    List<String> command = new ArrayList<>();
    if (System.getProperty("os.name").startsWith("Windows")) {
      command.addAll(Arrays.asList("cmd.exe", "/C"));
    } else {
      command.add("/bin/bash");
    }
    command.addAll(Arrays.asList("gradlew", "--stacktrace", "--no-daemon", "--warning-mode", "all"));
    command.addAll(Arrays.asList(args));
    File exeDir = tempProjectDir;
    if (exeRelativePath != null) {
      exeDir = new File(exeDir, exeRelativePath);
    }
    ProcessBuilder pb = new ProcessBuilder(command)
      .directory(exeDir)
      .redirectOutput(outputFile)
      .redirectErrorStream(true);
    if (getJavaVersion() > 8) {
      // Fix jacoco java 17 compatibility
      pb.environment().put("GRADLE_OPTS", "-Xmx1024m --add-opens=java.prefs/java.util.prefs=ALL-UNNAMED");
    } else {
      pb.environment().put("GRADLE_OPTS", "-Xmx1024m");
    }
    pb.environment().putAll(env);
    Process p = pb.start();
    p.waitFor();

    String output = FileUtils.readFileToString(outputFile, StandardCharsets.UTF_8);

    return new RunResult(output, p.exitValue());
  }

  private static int getJavaVersion() {
    String version = System.getProperty("java.version");
    if (version.startsWith("1.")) {
      version = version.substring(2, 3);
    } else {
      int dot = version.indexOf(".");
      if (dot != -1) {
        version = version.substring(0, dot);
      }
    }
    return Integer.parseInt(version);
  }

  protected static class RunResult {
    private final String log;
    private final int exitValue;

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
