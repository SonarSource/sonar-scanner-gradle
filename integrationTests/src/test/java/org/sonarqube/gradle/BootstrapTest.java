/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2024 SonarSource SA
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

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class BootstrapTest extends AbstractGradleIT {

  @ClassRule
  public static final Orchestrator ORCHESTRATOR;

  static {
    if (getJavaVersion() < 17) {
      ORCHESTRATOR = null;
    } else {
      ORCHESTRATOR = Orchestrator.builderEnv()
        .setSonarVersion("LATEST_RELEASE")
        .useDefaultAdminCredentialsForBuilds(true)
        .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", "LATEST_RELEASE"))
        .addPlugin(FileLocation.of("../property-dump-plugin/target/property-dump-plugin-1-SNAPSHOT.jar"))
        .build();
    }
  }

  @BeforeClass
  public static void checkOrchestrator() {
    assumeTrue("Skipping Orchestrator tests as it was not possible to initialize it", ORCHESTRATOR != null);
  }

  @Test
  public void testJreProvisioning() throws Exception {
    HashMap<String, String> env = new HashMap<>();
    env.put("DUMP_SYSTEM_PROPERTIES", "java.home");
    env.put("DUMP_SENSOR_PROPERTIES", "sonar.java.jdkHome");
    String project = "/java-gradle-simple";
    RunResult result = runSonarAnalysis(project, env);

    Properties props = readDumpedProperties(project);
    // sonar.java.jdkHome should be the one used by "gradlew sonar"
    assertThat(props.getProperty("sonar.java.jdkHome")).isEqualTo(guessJavaHomeSelectedByGradle());
    assertProvisionedJreIsUsed(project, result);
  }

  @Test
  public void testSkipJreProvisioning() throws Exception {
    HashMap<String, String> env = new HashMap<>();
    env.put("DUMP_SYSTEM_PROPERTIES", "java.home");
    env.put("DUMP_SENSOR_PROPERTIES", "sonar.java.jdkHome");
    env.put("SONAR_SCANNER_SKIP_JRE_PROVISIONING", "true");
    String project = "/java-gradle-simple";
    RunResult result = runSonarAnalysis(project, env);
    assertProvisionedJreIsNotUsed(project, result);
  }

  @Test
  public void testSkipJreProvisioningInBuildFile() throws Exception {
    HashMap<String, String> env = new HashMap<>();
    env.put("DUMP_SYSTEM_PROPERTIES", "java.home");
    env.put("DUMP_SENSOR_PROPERTIES", "sonar.java.jdkHome");
    String project = "/java-gradle-simple-skip-jre-prov";
    RunResult result = runSonarAnalysis(project, env);
    assertProvisionedJreIsNotUsed(project, result);
  }

  @Test
  public void testUnsupportedOs() throws Exception {
    HashMap<String, String> env = new HashMap<>();
    env.put("DUMP_SYSTEM_PROPERTIES", "java.home");
    env.put("DUMP_SENSOR_PROPERTIES", "sonar.java.jdkHome");
    String unsupportedOS = "Windows2";
    env.put("SONAR_SCANNER_OS", unsupportedOS);
    String arch = "amd64";
    env.put("SONAR_SCANNER_ARCH", arch);
    RunResult result = runSonarAnalysis("/java-gradle-simple", env);
    String url = ORCHESTRATOR.getServer().getUrl() + String.format("/api/v2/analysis/jres?os=%s&arch=%s", unsupportedOS, arch);
    String expectedLog = String.format("Error status returned by url [%s]: 400", url);
    assertThat(result.getLog()).contains(expectedLog);
  }

  @Test
  public void testBootstrappingUsesProvidedJre() throws Exception {
    String javaHome = guessJavaHomeSelectedByGradle();
    String project = "/java-gradle-simple";
    HashMap<String, String> env = new HashMap<>();
    env.put("SONAR_SCANNER_JAVA_EXE_PATH", javaHome + File.separator + "bin" + File.separator + "java");
    env.put("DUMP_SYSTEM_PROPERTIES", "java.home");
    env.put("DUMP_SENSOR_PROPERTIES", "sonar.java.jdkHome");
    RunResult result = runSonarAnalysis(project, env);
    assertProvisionedJreIsNotUsed(project, result);
    assertThat(result.getLog()).contains("Using the configured java executable");
  }

  @Test
  public void analysis_failure_makes_the_gradle_task_fail() throws Exception {
    HashMap<String, String> env = new HashMap<>();
    env.put("FAIL_ANALYSIS", "true");
    String project = "/java-gradle-simple";
    RunResult result = runSonarAnalysis(project, env);
    assertThat(result.getExitValue()).isOne();
    assertThat(result.getLog())
      .containsOnlyOnce("Analysis failed as requested!")
      .containsOnlyOnce("The analysis has failed! See the logs for more details.");
  }

  private void assertProvisionedJreIsUsed(String projectName, RunResult result) throws IOException {
    assertProvisionedJreShouldBeUsed(projectName, result, true);
  }

  private void assertProvisionedJreIsNotUsed(String projectName, RunResult result) throws IOException {
    assertProvisionedJreShouldBeUsed(projectName, result, false);
  }

  private void assertProvisionedJreShouldBeUsed(String projectName, RunResult result, boolean shouldBeUsed) throws IOException {
    Properties props = readDumpedProperties(projectName);
    SoftAssertions softly = new SoftAssertions();
    if (shouldBeUsed) {
      softly.assertThat(result.getLog()).contains("JRE provisioning:");
      softly.assertThat(props.getProperty("java.home"))
        .isNotEmpty()
        .isNotEqualTo(System.getProperty("java.home"))
        .isNotEqualTo(guessJavaHomeSelectedByGradle())
        .contains(".sonar" + File.separator + "cache");
    } else {
      softly.assertThat(result.getLog()).doesNotContain("JRE provisioning:");
      softly.assertThat(props.getProperty("java.home"))
        .isNotEmpty()
        .isEqualTo(System.getProperty("java.home"))
        .isEqualTo(guessJavaHomeSelectedByGradle())
        .doesNotContain(".sonar" + File.separator + "cache");
    }

  }

  private RunResult runSonarAnalysis(String project, Map<String, String> env, String... args) throws Exception {
    env.put("SONAR_HOST_URL", ORCHESTRATOR.getServer().getUrl());
    File projectBaseDir = new File(this.getClass().getResource(project).toURI());
    String projectDir = project.startsWith("/") ? "." + project : project;
    File tempProjectDir = new File(temp.getRoot(), projectDir);
    if (!tempProjectDir.exists()) {
      tempProjectDir = temp.newFolder(projectDir);
    }
    File outputFile = temp.newFile();
    FileUtils.copyDirectory(projectBaseDir, tempProjectDir);
    List<String> command = getGradlewCommand();
    command.add(getSQTokenArg());
    command.addAll(Arrays.asList("sonar", "--no-daemon", "--info"));
    command.addAll(Arrays.asList(args));
    File exeDir = tempProjectDir;
    ProcessBuilder pb = new ProcessBuilder(command)
      .directory(exeDir)
      .redirectOutput(outputFile)
      .redirectErrorStream(true);
    if (getJavaVersion() > 8) {
      // Fix jacoco java 17 compatibility
      pb.environment().put("GRADLE_OPTS", "-Xmx1024m --add-opens=java.prefs/java.util.prefs=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED");
    } else {
      pb.environment().put("GRADLE_OPTS", "-Xmx1024m");
    }
    pb.environment().putAll(env);
    Process p = pb.start();
    p.waitFor();

    String output = FileUtils.readFileToString(outputFile, StandardCharsets.UTF_8);

    return new RunResult(output, p.exitValue(), getDumpedProperties(command));
  }

  private static String guessJavaHomeSelectedByGradle() throws IOException {
    // By default maven uses JAVA_HOME if it exists, otherwise we don't know, we hope it uses the one that is currently running
    String javaHome = System.getenv("JAVA_HOME");
    if (javaHome == null) {
      javaHome = System.getProperty("java.home");
    }
    return new File(javaHome).getCanonicalPath();
  }

  private Properties readDumpedProperties(String projectName) throws IOException {
    if (projectName.startsWith("/")) {
      projectName = projectName.substring(1);
    }
    Path propertiesFile = temp.getRoot().toPath().resolve(projectName + "/build/sonar/dumpSensor.system.properties");
    Properties props = new Properties();
    props.load(Files.newInputStream(propertiesFile));
    return props;
  }

  private String getSQTokenArg() {
    return "-Dsonar.token=" + ORCHESTRATOR.getDefaultAdminToken();
  }

}
