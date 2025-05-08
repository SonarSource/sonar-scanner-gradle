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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.data.MapEntry.entry;

public class GradleTest extends AbstractGradleIT {

  @Test
  public void testSimpleJavaProject() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/java-gradle-simple", Collections.emptyMap(), "compileJava", "compileTestJava");

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

  /**
   * SONARGRADL-100
   */
  @Test
  public void testDebugModeEnabled(){
    assertThatCode(() -> runGradlewSonarSimulationModeWithEnv("/java-gradle-simple", emptyMap(), "-d")).doesNotThrowAnyException();
  }

  @Test
  public void testSetLogLevel() throws Exception {
    File output = temp.newFile("will-not-be-used-for-this-test.txt");
    String args = String.format(
      "-Dsonar.scanner.internal.dumpToFile=%s",
      output.getAbsolutePath()
    );
    RunResult runResult = runGradlewSonarWithEnv("/java-gradle-log-level", emptyMap(), args);
    // This is a debug log entry
    assertThat(runResult.getLog()).contains(":sonar");
  }

  @Ignore("TODO SCANGRADLE-159: sonar.scanner.skip does not prevent reaching to the server!")
  @Test
  public void testSkip() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("SONARQUBE_SCANNER_PARAMS", "{\"sonar.scanner.skip\" : \"true\" }");
    RunResult result = runGradlewSonarWithEnv("/java-gradle-simple", env);

    System.out.println(result.getLog());
    assertThat(result.getExitValue()).isZero();
    assertThat(result.getLog()).contains("Sonar Scanner analysis skipped");
  }

  @Test
  public void testHostUrlInEnvErrorIncludingExecutionContextInformation() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("SONAR_HOST_URL", "http://host-in-env");
    RunResult result = runGradlewSonarWithEnvQuietly("/java-gradle-simple", env, "--info");

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
  public void testCompileOnly() throws Exception {
    Properties props = runGradlewSonarSimulationMode("/java-compile-only");

    assertThat(props.getProperty("sonar.java.libraries")).contains("commons-io-2.5.jar", "commons-lang-2.6.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.10.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.10.jar", "commons-io-2.5.jar");
    // compileOnly are not included into test classpath
    assertThat(props.getProperty("sonar.java.test.libraries")).doesNotContain("commons-lang-2.6.jar");
  }

  // SONARGRADL-23
  @Test
  public void testCustomConfiguration() throws Exception {

    Properties props = runGradlewSonarSimulationModeWithEnv("/java-gradle-custom-config", emptyMap(), "compileJava", "compileTestJava");

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
    Properties props = runGradlewSonarSimulationModeWithEnv("/java-groovy-tests-gradle", emptyMap(), "build");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "groovy-java-gradle-mixed-tests"));
    assertThat(Paths.get(props.getProperty("sonar.sources"))).isEqualTo(baseDir.resolve("src/main/groovy"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/groovy"));
    assertThat(Paths.get(props.getProperty("sonar.junit.reportsPath"))).isEqualTo(baseDir.resolve("build/test-results/test"));
    assertThat(Paths.get(props.getProperty("sonar.junit.reportPaths"))).isEqualTo(baseDir.resolve("build/test-results/test"));
    assertThat(props)
      .doesNotContainKey("sonar.groovy.jacoco.reportPath")
      .doesNotContainKey("sonar.jacoco.reportPath");
  }

  @Test
  public void loadSonarScannerPropertiesEnv() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("SONARQUBE_SCANNER_PARAMS", "{\"sonar.host.url\" : \"myhost\" }");
    Properties props = runGradlewSonarSimulationModeWithEnv("/java-gradle-simple", env);

    assertThat(props).contains(entry("sonar.host.url", "myhost"));
  }

  @Test
  public void module_inclusion_duplicate_key() throws Exception {
    Properties props = runGradlewSonarSimulationMode("/module-inclusion");

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
    Properties props = runGradlewSonarSimulationModeWithEnv("/multi-module-source-in-root", emptyMap(), "compileJava", "compileTestJava");

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
    Properties props = runGradlewSonarSimulationModeWithEnv("/multi-module-flat", "build", emptyMap());
    assertThat(Paths.get(props.getProperty("sonar.projectBaseDir")).getFileName()).hasToString("multi-module-flat");
  }

  @Test
  public void testJavaProjectWithoutTestsDoesNotSetCustomReportsPath() throws Exception {
    Properties props = runGradlewSonarSimulationMode("/java-gradle-no-tests");
    Path testResultsDir = Paths.get(props.getProperty("sonar.projectBaseDir")).resolve("build/test-results");

    assertThat(testResultsDir).doesNotExist();
    assertThat(props.getProperty("sonar.junit.reportsPath")).isNull();
    assertThat(props.getProperty("sonar.junit.reportPaths")).isNull();
  }

  @Test
  public void testJavaProjectWithoutRealTestsDoesNotSetCustomReportsPath() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/java-gradle-no-real-tests", emptyMap(), "test");
    Path testResultsDir = Paths.get(props.getProperty("sonar.projectBaseDir")).resolve("build/test-results");

    assertThat(testResultsDir).exists();
    assertThat(props.getProperty("sonar.junit.reportsPath")).isNull();
    assertThat(props.getProperty("sonar.junit.reportPaths")).isNull();
  }

  @Test
  public void testLazyConfiguration() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/java-gradle-lazy-configuration", emptyMap(), "test");
    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("org.codehaus.sonar:example-java-gradle");
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
    Properties props = runGradlewSonarSimulationModeWithEnv(project, emptyMap(), "processResources", "processTestResources", "test", "jacocoTestReport");
    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));
    assertThat(props).doesNotContainKey("sonar.jacoco.reportPaths");
    assertThat(props.getProperty("sonar.coverage.jacoco.xmlReportPaths")).contains(baseDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml").toString());
  }

  @Test
  public void testProjectWithConfigurationCacheDoubleExecutionsShouldWork() throws Exception {
    Assume.assumeTrue("Tests only applies to version 6.5.0 or greater", getGradleVersion().isGreaterThanOrEqualTo("6.5.0"));

    String dumpProperty = String.format("-Dsonar.scanner.internal.dumpToFile=%s", temp.newFile().getAbsolutePath());

    runGradlewSonarWithEnv("/java-gradle-simple", emptyMap(), dumpProperty, "--configuration-cache");
    RunResult runResult = runGradlewSonarWithEnv("/java-gradle-simple", emptyMap(), dumpProperty, "--configuration-cache");

    assertThat(runResult.getLog()).doesNotContain("no properties configured, was it skipped in all projects?");
    assertThat(runResult.getLog()).contains("BUILD SUCCESSFUL");
  }

  @Test
  public void testKotlinMultiplatformProject() throws Exception {
    Assume.assumeTrue("Tests only applies to version 6.8.3 or greater", getGradleVersion().isGreaterThanOrEqualTo("6.8.3"));
    Properties props = runGradlewSonarSimulationModeWithEnv("/kotlin-multiplatform", emptyMap(), "compileCommonMainKotlinMetadata", "compileKotlinJvm", "compileKotlinMetadata", "compileTestKotlinJvm");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(baseDir.getFileName().toString()).hasToString("kotlin-multiplatform");

    String[] binaries = props.getProperty("sonar.java.binaries").split(",");
    String[] sources = props.getProperty("sonar.sources").split(",");

    assertThat(binaries).containsExactlyInAnyOrder(
      baseDir.resolve("build/classes/kotlin/jvm/main").toString(),
      baseDir.resolve("build/classes/java/main").toString());

    assertThat(sources).containsExactlyInAnyOrder(
      baseDir.resolve("src/commonMain/kotlin").toString(),
      baseDir.resolve("src/jvmMain/kotlin").toString(),
      baseDir.resolve("src/jvmMain/java").toString());
  }

  @Test
  public void testKotlinMultiplatformWithSubmoduleProject() throws Exception {
    Assume.assumeTrue("Tests only applies to version 6.8.3 or greater", getGradleVersion().isGreaterThanOrEqualTo("6.8.3"));
    Properties props = runGradlewSonarSimulationModeWithEnv("/kotlin-multiplatform-with-submodule", emptyMap(), "compileCommonMainKotlinMetadata", "compileKotlinJvm", "compileKotlinMetadata", "compileTestKotlinJvm");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(baseDir.getFileName().toString()).hasToString("kotlin-multiplatform-with-submodule");

    String[] binaries = props.getProperty(":submodule.sonar.java.binaries").split(",");
    String[] sources = props.getProperty(":submodule.sonar.sources").split(",");

    assertThat(binaries).containsExactlyInAnyOrder(
      baseDir.resolve("submodule/build/classes/kotlin/jvm/main").toString(),
      baseDir.resolve("submodule/build/classes/java/main").toString());

    assertThat(sources).containsExactlyInAnyOrder(
      baseDir.resolve("submodule/src/commonMain/kotlin").toString(),
      baseDir.resolve("submodule/src/jvmMain/kotlin").toString(),
      baseDir.resolve("submodule/src/jvmMain/java").toString());
  }

  @Test
  public void testKotlinJvmProject() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/kotlin-jvm", emptyMap(), "compileKotlin", "compileTestKotlin");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(baseDir.getFileName().toString()).hasToString("kotlin-jvm");

    String[] binaries = props.getProperty("sonar.java.binaries").split(",");
    String[] sources = props.getProperty("sonar.sources").split(",");

    assertThat(binaries).containsExactly(baseDir.resolve("build/classes/kotlin/main").toString());
    assertThat(sources).containsExactly(baseDir.resolve("src/main/kotlin").toString());
  }

  @Test
  public void testKotlinJvmWithSubmoduleProject() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/kotlin-jvm-submodule", emptyMap(), "compileKotlin", "compileTestKotlin");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(baseDir.getFileName().toString()).hasToString("kotlin-jvm-submodule");

    String[] binaries = props.getProperty(":submodule.sonar.java.binaries").split(",");
    String[] sources = props.getProperty(":submodule.sonar.sources").split(",");

    assertThat(binaries).containsExactly(baseDir.resolve("submodule/build/classes/kotlin/main").toString());
    assertThat(sources).containsExactly(baseDir.resolve("submodule/src/main/kotlin").toString());
  }

  @Test
  public void testScanAllOnMultiModuleWithSubModulesProjectCollectsTheExpectedSources() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/multi-module-with-submodules", emptyMap(), "compileJava", "compileTestJava");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));
    assertThat(baseDir.getFileName().toString()).hasToString("multi-module-with-submodules");

    String scanAll = props.getProperty("sonar.gradle.scanAll");
    assertThat(scanAll).isEqualTo("true");

    // checking that the skippedModules are not included in the sonar.modules property, while the other non-skipped nested modules are
    assertThat(props.getProperty("sonar.modules")).isEqualTo(":module");
    assertThat(props.getProperty(":module.sonar.modules")).isEqualTo(":module:submodule");

    // checking that the expected sources are collected when the scanAll property is set to true
    String[] sources = props.getProperty("sonar.sources").split(",");
    assertThat(sources)
      .containsExactlyInAnyOrder(
        baseDir.resolve("build.gradle.kts").toString(),
        baseDir.resolve("settings.gradle.kts").toString(),
        baseDir.resolve("module/build.gradle.kts").toString(),
        baseDir.resolve("module/submodule/build.gradle.kts").toString(),
        baseDir.resolve("module/submodule/submoduleScript.sh").toString(),
        baseDir.resolve("gradlew").toString(),
        baseDir.resolve("gradlew.bat").toString(),
        baseDir.resolve("gradle/wrapper/gradle-wrapper.properties").toString(),
        baseDir.resolve(".hiddenDir/file.txt").toString(),
        baseDir.resolve(".hiddenConfig").toString()
      ).doesNotContain(
        baseDir.resolve("skippedModule/build.gradle.kts").toString(),
        baseDir.resolve("skippedModule/skippedSubmodule/skippedSubmoduleScript.sh").toString(),
        baseDir.resolve("skippedModule/skippedSubmodule/build.gradle.kts").toString(),
        baseDir.resolve(".hiddenDir/script.py").toString()
      );

    assertThat(props.getProperty(":module.sonar.sources")).isEqualTo(baseDir.resolve("module/src/main/java").toString());
    assertThat(props.getProperty(":module.:module:submodule.sonar.sources")).isEqualTo(baseDir.resolve("module/submodule/src/main/java").toString());

    assertThat(props.getProperty(":skippedModule.sonar.sources")).isNull();
    assertThat(props.getProperty(":skippedModule.:skippedModule:skippedSubmodule.sonar.sources")).isNull();
  }
}
