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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.Ignore;
import org.junit.Test;
import org.sonarqube.gradle.run_configuration.DefaultRunConfiguration;
import org.sonarqube.gradle.run_configuration.RunConfigurationList;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.data.MapEntry.entry;

public class GradleTest extends AbstractGradleIT {

  @Test
  public void testSimpleJavaProject() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    Properties props = runGradlewSonarSimulationModeWithEnv("/java-gradle-simple", Collections.emptyMap(), new DefaultRunConfiguration(), "compileJava", "compileTestJava");

    Path baseDir = getRequiredPathProperty(props, "sonar.projectBaseDir");

    assertThat(props).contains(entry("sonar.projectKey", "org.codehaus.sonar:example-java-gradle"));
    assertPathProperty(props, "sonar.sources", baseDir.resolve("src/main/java"));
    assertPathProperty(props, "sonar.tests", baseDir.resolve("src/test/java"));
    assertPathProperty(props, "sonar.java.binaries", baseDir.resolve("build/classes/java/main"));
    assertPathProperty(props, "sonar.java.test.binaries", baseDir.resolve("build/classes/java/test"));

    assertPropertyContains(props, "sonar.java.libraries", "commons-io-2.5.jar");
    assertPropertyDoesNotContain(props, "sonar.java.libraries", "junit-4.10.jar");
    assertPropertyContains(props, "sonar.java.test.libraries", "junit-4.10.jar", "commons-io-2.5.jar", baseDir.resolve("build/classes/java/main").toString());
  }

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
    String args = String.format(
      "-Dsonar.scanner.internal.dumpToFile=%s",
      output.getAbsolutePath()
    );
    RunResult runResult = runGradlewSonarWithEnv("/java-gradle-log-level", emptyMap(), new DefaultRunConfiguration(), args);
    // This is a debug log entry
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
  public void testCompileOnlyDependenciesAreNotIncludeInTestClassPath() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    Properties props = runGradlewSonarSimulationMode("/java-compile-only");

    assertThat(props.getProperty("sonar.java.libraries"))
            .contains("commons-io-2.5.jar", "commons-lang-2.6.jar")
            .doesNotContain("junit-4.10.jar");

    // compileOnly dependencies are not included into test classpath
    assertThat(props.getProperty("sonar.java.test.libraries"))
            .contains("junit-4.10.jar", "commons-io-2.5.jar")
            .doesNotContain("commons-lang-2.6.jar");
  }

  // SONARGRADL-23
  @Test
  public void testCustomConfiguration() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");

    Properties props = runGradlewSonarSimulationModeWithEnv("/java-gradle-custom-config", emptyMap(), new DefaultRunConfiguration(), "compileJava", "compileTestJava");

    Path baseDir = getRequiredPathProperty(props, "sonar.projectBaseDir");

    assertPathListPropertyContainsOnly(props, "sonar.sources", baseDir.resolve("src/main/java"), baseDir.resolve("src/main/foo.js"));
    assertPathListPropertyContainsOnly(props, "sonar.tests", baseDir.resolve("src/test/java"), baseDir.resolve("src/test/fooTest.js"));
    assertPathListPropertyContainsOnly(props, "sonar.java.binaries", baseDir.resolve("build/classes/java/main"), baseDir.resolve("build/extraBinaries"));
    assertPathListPropertyContainsOnly(props, "sonar.java.test.binaries", baseDir.resolve("build/classes/java/test"), baseDir.resolve("build/extraTestBinaries"));
  }

  @Test
  public void testUserDefinedProperties() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/java-gradle-user-properties", emptyMap(), new DefaultRunConfiguration(), "compileJava", "compileTestJava");

    assertThat(props.getProperty("sonar.projectName")).isEqualTo("Simple Java Gradle Project");
    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("org.codehaus.sonar:example-java-gradle");
    assertThat(props.getProperty("sonar.projectDescription")).isEqualTo("A simple Java Gradle project to test the proper handling of user defined properties");
    assertThat(props.getProperty("sonar.projectVersion")).isEqualTo("0.1-SNAPSHOT");
    assertThat(props.getProperty("sonar.sources").split(",")).containsOnly("src/main/java");
    assertThat(props.getProperty("sonar.tests").split(",")).containsOnly("src/test/java");
    assertThat(props.getProperty("sonar.coverage.jacoco.xmlReportPaths").split(",")).containsOnly("reports/nonexisting.xml", "**/*.xml");
  }

  @Test
  public void mixJavaGroovyProject() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    Properties props = runGradlewSonarSimulationModeWithEnv("/java-groovy-tests-gradle", emptyMap(), new DefaultRunConfiguration(), "build");

    Path baseDir = getRequiredPathProperty(props, "sonar.projectBaseDir");

    assertThat(props).contains(entry("sonar.projectKey", "groovy-java-gradle-mixed-tests"));
    assertPathProperty(props, "sonar.sources", baseDir.resolve("src/main/groovy"));
    assertPathProperty(props, "sonar.tests", baseDir.resolve("src/test/groovy"));
    assertPathProperty(props, "sonar.junit.reportsPath", baseDir.resolve("build/test-results/test"));
    assertPathProperty(props, "sonar.junit.reportPaths", baseDir.resolve("build/test-results/test"));
    assertThat(props)
      .doesNotContainKey("sonar.groovy.jacoco.reportPath")
      .doesNotContainKey("sonar.jacoco.reportPath");
  }

  @Test
  public void loadSonarScannerPropertiesEnv() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    Map<String, String> env = new HashMap<>();
    env.put("SONARQUBE_SCANNER_PARAMS", "{\"sonar.host.url\" : \"myhost\" }");
    Properties props = runGradlewSonarSimulationModeWithEnv("/java-gradle-simple", env, new DefaultRunConfiguration());

    assertThat(props).contains(entry("sonar.host.url", "myhost"));
  }

  @Test
  public void module_inclusion_duplicate_key() throws Exception {
    Properties props = runGradlewSonarSimulationMode("/module-inclusion");

    assertThat(props).contains(entry("sonar.projectKey", "com.mygroup:root_project"));
    assertThat(props.get("sonar.modules").toString().split(",")).containsOnly(":toplevel1", ":toplevel2");

    assertThat(props)
      .contains(entry(":toplevel1.sonar.moduleKey", "com.mygroup:root_project:toplevel1"))
      .contains(entry(":toplevel2.sonar.moduleKey", "com.mygroup:root_project:toplevel2"))
      .contains(entry(":toplevel1.sonar.modules", ":toplevel1:plugins"))
      .contains(entry(":toplevel2.sonar.modules", ":toplevel2:plugins"))
      .contains(entry(":toplevel1.:toplevel1:plugins.sonar.moduleKey", "com.mygroup:root_project:toplevel1:plugins"))
      .contains(entry(":toplevel2.:toplevel2:plugins.sonar.moduleKey", "com.mygroup:root_project:toplevel2:plugins"));
  }

  // SONARGRADL-5
  @Test
  public void testMultimoduleProjectWithSourceInRoot() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    Properties props = runGradlewSonarSimulationModeWithEnv("/multi-module-source-in-root", emptyMap(), new DefaultRunConfiguration(), "compileJava", "compileTestJava");

    Path baseDir = getRequiredPathProperty(props, "sonar.projectBaseDir");

    assertPathProperty(props, "sonar.sources", baseDir.resolve("src/main/java"));
    assertPathProperty(props, "sonar.tests", baseDir.resolve("src/test/java"));
    assertPathProperty(props, "sonar.java.binaries", baseDir.resolve("build/classes/java/main"));
    assertPathProperty(props, "sonar.java.test.binaries", baseDir.resolve("build/classes/java/test"));
    assertPropertyContains(props, "sonar.java.libraries", "commons-io-2.5.jar");
    assertPropertyDoesNotContain(props, "sonar.java.libraries", "junit-4.10.jar");
    assertPropertyContains(props, "sonar.java.test.libraries", "junit-4.10.jar", "commons-io-2.5.jar");

    Path moduleA = baseDir.resolve("moduleA");
    assertPathProperty(props, ":moduleA.sonar.sources", moduleA.resolve("src/main/java"));
    assertPathProperty(props, ":moduleA.sonar.tests", moduleA.resolve("src/test/java"));
    assertPathProperty(props, ":moduleA.sonar.java.binaries", moduleA.resolve("build/classes/java/main"));
    assertPathProperty(props, ":moduleA.sonar.java.test.binaries", moduleA.resolve("build/classes/java/test"));
    assertPropertyContains(props, ":moduleA.sonar.java.libraries", "commons-io-2.5.jar");
    assertPropertyDoesNotContain(props, ":moduleA.sonar.java.libraries", "junit-4.10.jar");
    assertPropertyContains(props, ":moduleA.sonar.java.test.libraries", "junit-4.10.jar", "commons-io-2.5.jar");

  }

  /**
   * SONARGRADL-48
   */
  @Test
  public void testFlatProjectStructure() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    Properties props = runGradlewSonarSimulationModeWithEnv("/multi-module-flat", "build", emptyMap(), new DefaultRunConfiguration());
    assertThat(getRequiredPathProperty(props, "sonar.projectBaseDir").getFileName()).hasToString("multi-module-flat");
  }

  @Test
  public void testJavaProjectWithoutTestsDoesNotSetCustomReportsPath() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    Properties props = runGradlewSonarSimulationMode("/java-gradle-no-tests");
    Path testResultsDir = getRequiredPathProperty(props, "sonar.projectBaseDir").resolve("build/test-results");

    assertThat(testResultsDir).doesNotExist();
    assertThat(props.getProperty("sonar.junit.reportsPath")).isNull();
    assertThat(props.getProperty("sonar.junit.reportPaths")).isNull();
  }

  @Test
  public void testJavaProjectWithoutRealTestsDoesNotSetCustomReportsPath() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    Properties props = runGradlewSonarSimulationModeWithEnv("/java-gradle-no-real-tests", emptyMap(), new DefaultRunConfiguration(), "test");
    Path testResultsDir = getRequiredPathProperty(props, "sonar.projectBaseDir").resolve("build/test-results");

    assertThat(testResultsDir).exists();
    assertThat(props.getProperty("sonar.junit.reportsPath")).isNull();
    assertThat(props.getProperty("sonar.junit.reportPaths")).isNull();
  }

  @Test
  public void testLazyConfiguration() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    Properties props = runGradlewSonarSimulationModeWithEnv("/java-gradle-lazy-configuration", emptyMap(), new DefaultRunConfiguration(), "test");
    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("org.codehaus.sonar:example-java-gradle");
  }

  @Test
  public void testJaCoCoProperties() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    String project;
    if (getGradleVersion().isLowerThan("7")) {
      // Use report.enabled
      project = "/java-gradle-jacoco-before-7";
    } else {
      // Use report.required
      project = "/java-gradle-jacoco-after-7";
    }
    Properties props = runGradlewSonarSimulationModeWithEnv(project, emptyMap(), new DefaultRunConfiguration(), "processResources", "processTestResources", "test",
      "jacocoTestReport");
    Path baseDir = getRequiredPathProperty(props, "sonar.projectBaseDir");
    assertThat(props).doesNotContainKey("sonar.jacoco.reportPaths");
    assertThat(props.getProperty("sonar.coverage.jacoco.xmlReportPaths")).contains(baseDir.resolve("build/reports/jacoco/test/jacocoTestReport.xml").toString());
  }

  @Test
  public void testProjectWithConfigurationCacheDoubleExecutionsShouldWork() throws Exception {
    ignoreThisTestIfGradleVersionIsNotBetween("6.5.0", "9.0.0");

    String dumpProperty = String.format("-Dsonar.scanner.internal.dumpToFile=%s", temp.newFile().getAbsolutePath());


    runGradlewSonarWithEnv("/java-gradle-simple",
      emptyMap(),
      new RunConfigurationList(List.of()),
      dumpProperty,
      "--configuration-cache");
    // in the second execution we expect to reuse the configuration cache
    RunResult runResult = runGradlewSonarWithEnv("/java-gradle-simple",
      emptyMap(),
      new RunConfigurationList(List.of()),
      dumpProperty,
      "--configuration-cache");

    assertThat(runResult.getLog()).contains("Reusing configuration cache.");
  }

  @Test
  public void testKotlinMultiplatformProject() throws Exception {
    ignoreThisTestIfGradleVersionIsNotBetween("6.8.3", "9.0.0");
    Properties props = runGradlewSonarSimulationModeWithEnv("/kotlin-multiplatform", emptyMap(), new DefaultRunConfiguration(),
      "compileKotlinJvm", "compileKotlinMetadata", "compileTestKotlinJvm");

    Path baseDir = getRequiredPathProperty(props, "sonar.projectBaseDir");

    assertThat(baseDir.getFileName().toString()).hasToString("kotlin-multiplatform");

    assertPathListPropertyContainsExactlyInAnyOrder(props, "sonar.java.binaries",
      baseDir.resolve("build/classes/kotlin/jvm/main"),
      baseDir.resolve("build/classes/java/main"));

    assertPathListPropertyContainsExactlyInAnyOrder(props, "sonar.sources",
      baseDir.resolve("src/commonMain/kotlin"),
      baseDir.resolve("src/jvmMain/kotlin"),
      baseDir.resolve("src/jvmMain/java"));
  }

  @Test
  public void testKotlinMultiplatformWithSubmoduleProject() throws Exception {
    ignoreThisTestIfGradleVersionIsNotBetween("6.8.3", "9.0.0");
    Properties props = runGradlewSonarSimulationModeWithEnv("/kotlin-multiplatform-with-submodule", emptyMap(), new DefaultRunConfiguration(),
      "compileKotlinJvm", "compileKotlinMetadata", "compileTestKotlinJvm");

    Path baseDir = getRequiredPathProperty(props, "sonar.projectBaseDir");

    assertThat(baseDir.getFileName().toString()).hasToString("kotlin-multiplatform-with-submodule");

    assertPathListPropertyContainsExactlyInAnyOrder(props, ":submodule.sonar.java.binaries",
      baseDir.resolve("submodule/build/classes/kotlin/jvm/main"),
      baseDir.resolve("submodule/build/classes/java/main"));

    assertPathListPropertyContainsExactlyInAnyOrder(props, ":submodule.sonar.sources",
      baseDir.resolve("submodule/src/commonMain/kotlin"),
      baseDir.resolve("submodule/src/jvmMain/kotlin"),
      baseDir.resolve("submodule/src/jvmMain/java"));
  }

  @Test
  public void testKotlinJvmProject() throws Exception {
    ignoreThisTestIfGradleVersionIsNotBetween("6.8.3", "9.0.0");

    Properties props = runGradlewSonarSimulationModeWithEnv("/kotlin-jvm", emptyMap(), new DefaultRunConfiguration(), "compileKotlin", "compileTestKotlin");

    Path baseDir = getRequiredPathProperty(props, "sonar.projectBaseDir");

    assertThat(baseDir.getFileName().toString()).hasToString("kotlin-jvm");

    assertPathListPropertyContainsExactlyInAnyOrder(props, "sonar.java.binaries", baseDir.resolve("build/classes/kotlin/main"));
    assertPathListPropertyContainsExactlyInAnyOrder(props, "sonar.sources", baseDir.resolve("src/main/kotlin"));
  }

  @Test
  public void testKotlinJvmWithSubmoduleProject() throws Exception {
    ignoreThisTestIfGradleVersionIsNotBetween("6.8.3", "9.0.0");

    Properties props = runGradlewSonarSimulationModeWithEnv("/kotlin-jvm-submodule", emptyMap(), new DefaultRunConfiguration(), "compileKotlin", "compileTestKotlin");

    Path baseDir = getRequiredPathProperty(props, "sonar.projectBaseDir");

    assertThat(baseDir.getFileName().toString()).hasToString("kotlin-jvm-submodule");

    assertPathListPropertyContainsExactlyInAnyOrder(props, ":submodule.sonar.java.binaries", baseDir.resolve("submodule/build/classes/kotlin/main"));
    assertPathListPropertyContainsExactlyInAnyOrder(props, ":submodule.sonar.sources", baseDir.resolve("submodule/src/main/kotlin"));
  }

  @Test
  public void testScanAllOnMultiModuleWithSubModulesProjectCollectsTheExpectedSources() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/multi-module-with-submodules", emptyMap(), new DefaultRunConfiguration(), "compileJava", "compileTestJava", "--info");

    Path baseDir = getRequiredPathProperty(props, "sonar.projectBaseDir");
    assertThat(baseDir.getFileName().toString()).hasToString("multi-module-with-submodules");

    String scanAll = props.getProperty("sonar.gradle.scanAll");
    assertThat(scanAll).isEqualTo("true");

    // checking that the skippedModules are not included in the sonar.modules property, while the other non-skipped nested modules are
    assertThat(props.getProperty("sonar.modules")).isEqualTo(":module");
    assertThat(props.getProperty(":module.sonar.modules")).isEqualTo(":module:submodule");

    // checking that the expected sources are collected when the scanAll property is set to true
    assertPathListPropertyContainsExactlyInAnyOrder(props, "sonar.sources",
      baseDir.resolve("build.gradle.kts"),
      baseDir.resolve("settings.gradle.kts"),
      baseDir.resolve("module/build.gradle.kts"),
      baseDir.resolve("module/submodule/build.gradle.kts"),
      baseDir.resolve("module/submodule/submoduleScript.sh"),
      baseDir.resolve("gradlew"),
      baseDir.resolve("gradlew.bat"),
      baseDir.resolve("gradle/wrapper/gradle-wrapper.properties"),
      baseDir.resolve(".hiddenDir/file.txt"),
      baseDir.resolve(".hiddenConfig"));
    assertThat(getPathListProperty(props, "sonar.sources"))
      .doesNotContain(
        baseDir.resolve("skippedModule/build.gradle.kts").toAbsolutePath().normalize(),
        baseDir.resolve("skippedModule/skippedSubmodule/skippedSubmoduleScript.sh").toAbsolutePath().normalize(),
        baseDir.resolve("skippedModule/skippedSubmodule/build.gradle.kts").toAbsolutePath().normalize(),
        baseDir.resolve(".hiddenDir/script.py").toAbsolutePath().normalize()
      );

    assertPathProperty(props, ":module.sonar.sources", baseDir.resolve("module/src/main/java"));
    assertPathProperty(props, ":module.:module:submodule.sonar.sources", baseDir.resolve("module/submodule/src/main/java"));

    assertThat(props.getProperty(":skippedModule.sonar.sources")).isNull();
    assertThat(props.getProperty(":skippedModule.:skippedModule:skippedSubmodule.sonar.sources")).isNull();
  }

  @Test
  public void testSimpleJavaProjectWithGithubFolder() throws Exception {
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo("9.0.0");
    Properties props = runGradlewSonarSimulationModeWithEnv("/java-gradle-simple-with-github", Collections.emptyMap(), new DefaultRunConfiguration(), "compileJava",
      "compileTestJava");

    Path baseDir = getRequiredPathProperty(props, "sonar.projectBaseDir");

    assertThat(props).contains(entry("sonar.projectKey", "org.codehaus.sonar:example-java-gradle"));

    assertPathListPropertyContainsExactlyInAnyOrder(props, "sonar.sources",
      baseDir.resolve("src/main/java"),
      baseDir.resolve(".github"));
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


    // verify that gradle does not report a conflict with writeToResources
    assertThat(result.getExitValue()).isZero();
    // the conflict happens on "/resources/main", if we have a message containing "/resources/main" it surely impliy we have the error message
    assertThat(result.getLog()).doesNotContain("/resources/main");
  }
}
