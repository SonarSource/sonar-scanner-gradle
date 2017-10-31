package org.sonarqube.gradle;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;

import static java.util.Arrays.stream;
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
    if (getGradleVersion().startsWith("2.") || getGradleVersion().startsWith("3.")) {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/classes/main"));
      assertThat(Paths.get(props.getProperty("sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("build/classes/test"));
    } else {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/classes/java/main"));
      assertThat(Paths.get(props.getProperty("sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("build/classes/java/test"));
    }
    assertThat(props.getProperty("sonar.java.libraries")).contains("commons-io-2.5.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.10.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.10.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("commons-io-2.5.jar");
    // SONARGRADL-39
    if (getGradleVersion().startsWith("2.") || getGradleVersion().startsWith("3.")) {
      assertThat(props.getProperty("sonar.java.test.libraries")).contains(baseDir.resolve("build/classes/main").toString());
    } else {
      assertThat(props.getProperty("sonar.java.test.libraries")).contains(baseDir.resolve("build/classes/java/main").toString());
    }
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
    if (getGradleVersion().startsWith("2.") || getGradleVersion().startsWith("3.")) {
      assertThat(stream(props.getProperty("sonar.java.binaries").split(",")).map(Paths::get))
        .containsOnly(baseDir.resolve("build/classes/main"), baseDir.resolve("build/extraBinaries"));
      assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(baseDir.resolve("build/classes/test"), baseDir.resolve("build/extraTestBinaries"));
    } else {
      assertThat(stream(props.getProperty("sonar.java.binaries").split(",")).map(Paths::get))
        .containsOnly(baseDir.resolve("build/classes/java/main"), baseDir.resolve("build/extraBinaries"));
      assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(baseDir.resolve("build/classes/java/test"), baseDir.resolve("build/extraTestBinaries"));
    }
  }

  @Test
  public void mixJavaGroovyProject() throws Exception {
    Properties props = runGradlewSonarQubeSimulationMode("/java-groovy-tests-gradle");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "groovy-java-gradle-mixed-tests"));
    assertThat(Paths.get(props.getProperty("sonar.sources"))).isEqualTo(baseDir.resolve("src/main/groovy"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/groovy"));

    if (getGradleVersion().startsWith("3.") || getGradleVersion().startsWith("4.")) {
      assertThat(Paths.get(props.getProperty("sonar.junit.reportsPath"))).isEqualTo(baseDir.resolve("build/test-results/test"));
      assertThat(Paths.get(props.getProperty("sonar.junit.reportPaths"))).isEqualTo(baseDir.resolve("build/test-results/test"));
    } else {
      assertThat(Paths.get(props.getProperty("sonar.junit.reportsPath"))).isEqualTo(baseDir.resolve("build/test-results"));
      assertThat(Paths.get(props.getProperty("sonar.junit.reportPaths"))).isEqualTo(baseDir.resolve("build/test-results"));
    }
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
    if (getGradleVersion().startsWith("2.") || getGradleVersion().startsWith("3.")) {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/classes/main"));
      assertThat(Paths.get(props.getProperty("sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("build/classes/test"));
    } else {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/classes/java/main"));
      assertThat(Paths.get(props.getProperty("sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("build/classes/java/test"));
    }
    assertThat(props.getProperty("sonar.java.libraries")).contains("commons-io-2.5.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.10.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.10.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("commons-io-2.5.jar");

    Path moduleA = baseDir.resolve("moduleA");
    assertThat(Paths.get(props.getProperty(":moduleA.sonar.sources"))).isEqualTo(moduleA.resolve("src/main/java"));
    assertThat(Paths.get(props.getProperty(":moduleA.sonar.tests"))).isEqualTo(moduleA.resolve("src/test/java"));
    if (getGradleVersion().startsWith("2.") || getGradleVersion().startsWith("3.")) {
      assertThat(Paths.get(props.getProperty(":moduleA.sonar.java.binaries"))).isEqualTo(moduleA.resolve("build/classes/main"));
      assertThat(Paths.get(props.getProperty(":moduleA.sonar.java.test.binaries"))).isEqualTo(moduleA.resolve("build/classes/test"));
    } else {
      assertThat(Paths.get(props.getProperty(":moduleA.sonar.java.binaries"))).isEqualTo(moduleA.resolve("build/classes/java/main"));
      assertThat(Paths.get(props.getProperty(":moduleA.sonar.java.test.binaries"))).isEqualTo(moduleA.resolve("build/classes/java/test"));
    }

    assertThat(props.getProperty(":moduleA.sonar.java.libraries")).contains("commons-io-2.5.jar");
    assertThat(props.getProperty(":moduleA.sonar.java.libraries")).doesNotContain("junit-4.10.jar");
    assertThat(props.getProperty(":moduleA.sonar.java.test.libraries")).contains("junit-4.10.jar");
    assertThat(props.getProperty(":moduleA.sonar.java.test.libraries")).contains("commons-io-2.5.jar");

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
    Properties props = runGradlewSonarQubeSimulationMode("/java-gradle-no-real-tests");
    Path testResultsDir = Paths.get(props.getProperty("sonar.projectBaseDir")).resolve("build/test-results");

    assertThat(testResultsDir).exists();
    assertThat(props.getProperty("sonar.junit.reportsPath")).isNull();
    assertThat(props.getProperty("sonar.junit.reportPaths")).isNull();
  }
}
