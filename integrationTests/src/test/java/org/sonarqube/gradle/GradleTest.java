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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

public class GradleTest extends AbstractGradleIT {

  @Test
  public void testSimpleJavaProject() throws Exception {
    Properties props = runGradlewSonarQubeSimulationMode("/java-gradle-simple");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.codehaus.sonar:example-java-gradle"));
    assertThat(Paths.get(props.getProperty("sonar.sources"))).isEqualTo(baseDir.resolve("src/main/java"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/classes/java/main"));
    assertThat(Paths.get(props.getProperty("sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("build/classes/java/test"));

    assertThat(props.getProperty("sonar.java.libraries")).contains("commons-io-2.5.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.10.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.10.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("commons-io-2.5.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains(baseDir.resolve("build/classes/java/main").toString());
  }

  @Test
  public void testSkip() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("SONARQUBE_SCANNER_PARAMS", "{\"sonar.scanner.skip\" : \"true\" }");
    RunResult result = runGradlewSonarQubeWithEnv("/java-gradle-simple", env);

    System.out.println(result.getLog());
    assertThat(result.getExitValue()).isEqualTo(0);
    assertThat(result.getLog()).contains("SonarQube Scanner analysis skipped");
  }

  @Test
  public void testHostUrlInEnv() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("SONAR_HOST_URL", "http://host-in-env");
    RunResult result = runGradlewSonarQubeWithEnvQuietly("/java-gradle-simple", env);

    System.out.println(result.getLog());
    assertThat(result.getExitValue()).isEqualTo(1);
    assertThat(result.getLog()).contains("java.net.UnknownHostException");
    assertThat(result.getLog()).contains("SonarQube server [http://host-in-env] can not be reached");

  }

  @Test
  public void testCompileOnly() throws Exception {
    Properties props = runGradlewSonarQubeSimulationMode("/java-compile-only");

    assertThat(props.getProperty("sonar.java.libraries")).contains("commons-io-2.5.jar", "commons-lang-2.6.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.10.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.10.jar", "commons-io-2.5.jar");
    // compileOnly are not included into test classpath
    assertThat(props.getProperty("sonar.java.test.libraries")).doesNotContain("commons-lang-2.6.jar");
  }

  // SONARGRADL-23
  @Test
  public void testCustomConfiguration() throws Exception {

    Properties props = runGradlewSonarQubeSimulationMode("/java-gradle-custom-config");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .containsOnly(baseDir.resolve("src/main/java"), baseDir.resolve("src/main/foo.js"));
    assertThat(stream(props.getProperty("sonar.tests").split(",")).map(Paths::get))
      .containsOnly(baseDir.resolve("src/test/java"), baseDir.resolve("src/test/fooTest.js"));
    assertThat(stream(props.getProperty("sonar.java.binaries").split(",")).map(Paths::get))
      .containsOnly(baseDir.resolve("build/classes/java/main"), baseDir.resolve("build/extraBinaries"));
    assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(baseDir.resolve("build/classes/java/test"), baseDir.resolve("build/extraTestBinaries"));
  }

  @Test
  public void mixJavaGroovyProject() throws Exception {
    Properties props = runGradlewSonarQubeSimulationModeWithEnv("/java-groovy-tests-gradle", emptyMap(), "test");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "groovy-java-gradle-mixed-tests"));
    assertThat(Paths.get(props.getProperty("sonar.sources"))).isEqualTo(baseDir.resolve("src/main/groovy"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/groovy"));
    assertThat(Paths.get(props.getProperty("sonar.junit.reportsPath"))).isEqualTo(baseDir.resolve("build/test-results/test"));
    assertThat(Paths.get(props.getProperty("sonar.junit.reportPaths"))).isEqualTo(baseDir.resolve("build/test-results/test"));
    assertThat(Paths.get(props.getProperty("sonar.groovy.jacoco.reportPath"))).isEqualTo(baseDir.resolve("build/jacoco/test.exec"));
    assertThat(Paths.get(props.getProperty("sonar.jacoco.reportPath"))).isEqualTo(baseDir.resolve("build/jacoco/test.exec"));
  }

  @Test
  public void loadSonarScannerPropertiesEnv() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("SONARQUBE_SCANNER_PARAMS", "{\"sonar.host.url\" : \"myhost\" }");
    Properties props = runGradlewSonarQubeSimulationModeWithEnv("/java-gradle-simple", env);

    assertThat(props).contains(entry("sonar.host.url", "myhost"));
  }

  @Test
  public void module_inclusion_duplicate_key() throws Exception {
    Properties props = runGradlewSonarQubeSimulationMode("/module-inclusion");

    assertThat(props).contains(entry("sonar.projectKey", "com.mygroup:root_project"));
    assertThat(props.get("sonar.modules").toString().split(",")).containsOnly(":toplevel1", ":toplevel2");

    assertThat(props).contains(entry(":toplevel1.sonar.moduleKey", "com.mygroup:root_project:toplevel1"));
    assertThat(props).contains(entry(":toplevel2.sonar.moduleKey", "com.mygroup:root_project:toplevel2"));

    assertThat(props).contains(entry(":toplevel1.sonar.modules", ":toplevel1:plugins"));
    assertThat(props).contains(entry(":toplevel2.sonar.modules", ":toplevel2:plugins"));

    assertThat(props).contains(entry(":toplevel1.:toplevel1:plugins.sonar.moduleKey", "com.mygroup:root_project:toplevel1:plugins"));
    assertThat(props).contains(entry(":toplevel2.:toplevel2:plugins.sonar.moduleKey", "com.mygroup:root_project:toplevel2:plugins"));
  }

  // SONARGRADL-5
  @Test
  public void testMultimoduleProjectWithSourceInRoot() throws Exception {
    Properties props = runGradlewSonarQubeSimulationMode("/multi-module-source-in-root");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(Paths.get(props.getProperty("sonar.sources"))).isEqualTo(baseDir.resolve("src/main/java"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/classes/java/main"));
    assertThat(Paths.get(props.getProperty("sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("build/classes/java/test"));
    assertThat(props.getProperty("sonar.java.libraries")).contains("commons-io-2.5.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.10.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.10.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("commons-io-2.5.jar");

    Path moduleA = baseDir.resolve("moduleA");
    assertThat(Paths.get(props.getProperty(":moduleA.sonar.sources"))).isEqualTo(moduleA.resolve("src/main/java"));
    assertThat(Paths.get(props.getProperty(":moduleA.sonar.tests"))).isEqualTo(moduleA.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty(":moduleA.sonar.java.binaries"))).isEqualTo(moduleA.resolve("build/classes/java/main"));
    assertThat(Paths.get(props.getProperty(":moduleA.sonar.java.test.binaries"))).isEqualTo(moduleA.resolve("build/classes/java/test"));
    assertThat(props.getProperty(":moduleA.sonar.java.libraries")).contains("commons-io-2.5.jar");
    assertThat(props.getProperty(":moduleA.sonar.java.libraries")).doesNotContain("junit-4.10.jar");
    assertThat(props.getProperty(":moduleA.sonar.java.test.libraries")).contains("junit-4.10.jar");
    assertThat(props.getProperty(":moduleA.sonar.java.test.libraries")).contains("commons-io-2.5.jar");

  }

  /**
   * SONARGRADL-48
   */
  @Test
  public void testFlatProjectStructure() throws Exception {
    Properties props = runGradlewSonarQubeSimulationModeWithEnv("/multi-module-flat", "build", emptyMap());
    assertThat(Paths.get(props.getProperty("sonar.projectBaseDir")).getFileName().toString()).isEqualTo("multi-module-flat");
  }

  @Test
  public void testJavaProjectWithoutTestsDoesNotSetCustomReportsPath() throws Exception {
    Properties props = runGradlewSonarQubeSimulationMode("/java-gradle-no-tests");
    Path testResultsDir = Paths.get(props.getProperty("sonar.projectBaseDir")).resolve("build/test-results");

    assertThat(testResultsDir).doesNotExist();
    assertThat(props.getProperty("sonar.junit.reportsPath")).isNull();
    assertThat(props.getProperty("sonar.junit.reportPaths")).isNull();
  }

  @Test
  public void testJavaProjectWithoutRealTestsDoesNotSetCustomReportsPath() throws Exception {
    Properties props = runGradlewSonarQubeSimulationModeWithEnv("/java-gradle-no-real-tests", emptyMap(), "test");
    Path testResultsDir = Paths.get(props.getProperty("sonar.projectBaseDir")).resolve("build/test-results");

    assertThat(testResultsDir).exists();
    assertThat(props.getProperty("sonar.junit.reportsPath")).isNull();
    assertThat(props.getProperty("sonar.junit.reportPaths")).isNull();
  }

  @Test
  public void testJaCoCoProperties() throws Exception {
    String project;
    if (getGradleVersion().isLowerThan("7")) {
      // Use report.enabled
      project = "/java-gradle-jacoco-before-7";
    } else {
      // Use report.required
      project = "/java-gradle-jacoco-after-7";
    }
    Properties props = runGradlewSonarQubeSimulationModeWithEnv(project, emptyMap(), "test", "jacocoTestReport");
    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));
    assertThat(props.getProperty("sonar.jacoco.reportPaths")).contains(baseDir.resolve("build/jacoco/test.exec").toString());
    assertThat(props.getProperty("sonar.coverage.jacoco.xmlReportPaths")).contains(baseDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml").toString());
  }
}
