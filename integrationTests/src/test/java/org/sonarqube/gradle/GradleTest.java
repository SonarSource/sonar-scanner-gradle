package org.sonarqube.gradle;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.Assume.assumeTrue;

public class GradleTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testSimpleJavaProject() throws Exception {
    File out = temp.newFile();
    File projectBaseDir = new File(this.getClass().getResource("/java-gradle-simple").toURI());
    ProcessBuilder pb = new ProcessBuilder("/bin/bash", "gradlew", "--stacktrace", "sonarqube", "-DsonarRunner.dumpToFile=" + out.getAbsolutePath())
      .directory(projectBaseDir)
      .inheritIO();
    Process p = pb.start();
    p.waitFor();

    Properties props = new Properties();
    try (FileReader fr = new FileReader(out)) {
      props.load(fr);
    }

    assertThat(props).contains(
      entry("sonar.projectKey", "org.codehaus.sonar:example-java-gradle"));
    assertThat(props.get("sonar.sources").toString()).contains("src/main/java");
    assertThat(props.get("sonar.tests").toString()).contains("src/test/java");
    assertThat(props.get("sonar.java.binaries").toString()).contains("java-gradle-simple/build/classes/main");
    assertThat(props.get("sonar.java.test.binaries").toString()).contains("java-gradle-simple/build/classes/test");
    assertThat(props.get("sonar.java.libraries").toString()).contains("commons-io-2.5.jar");
    assertThat(props.get("sonar.java.libraries").toString()).doesNotContain("junit-4.10.jar");
    assertThat(props.get("sonar.java.test.libraries").toString()).contains("junit-4.10.jar");
    assertThat(props.get("sonar.java.test.libraries").toString()).contains("commons-io-2.5.jar");
  }

  @Test
  public void testCompileOnly() throws Exception {
    // compileOnly introduced with Gradle 2.12
    String gradleVersion = System.getProperty("gradle.version");
    assumeTrue(
      gradleVersion.startsWith("2.12") || gradleVersion.startsWith("2.13") || gradleVersion.startsWith("2.14") || gradleVersion.startsWith("3.") || gradleVersion.startsWith("4."));
    File out = temp.newFile();
    File projectBaseDir = new File(this.getClass().getResource("/java-compile-only").toURI());
    ProcessBuilder pb = new ProcessBuilder("/bin/bash", "gradlew", "--stacktrace", "sonarqube", "-DsonarRunner.dumpToFile=" + out.getAbsolutePath())
      .directory(projectBaseDir)
      .inheritIO();
    Process p = pb.start();
    p.waitFor();

    Properties props = new Properties();
    try (FileReader fr = new FileReader(out)) {
      props.load(fr);
    }

    assertThat(props.get("sonar.java.libraries").toString()).contains("commons-io-2.5.jar");
    assertThat(props.get("sonar.java.libraries").toString()).contains("commons-lang-2.6.jar");
    assertThat(props.get("sonar.java.libraries").toString()).doesNotContain("junit-4.10.jar");
    assertThat(props.get("sonar.java.test.libraries").toString()).contains("junit-4.10.jar");
    assertThat(props.get("sonar.java.test.libraries").toString()).contains("commons-io-2.5.jar");
    // Seems like compileOnly are not included into test classpath
    assertThat(props.get("sonar.java.test.libraries").toString()).doesNotContain("commons-lang-2.6.jar");
  }

  @Test
  public void mixJavaGroovyProject() throws Exception {
    File out = temp.newFile();
    File projectBaseDir = new File(this.getClass().getResource("/java-groovy-tests-gradle").toURI());
    ProcessBuilder pb = new ProcessBuilder("/bin/bash", "gradlew", "--stacktrace", "sonarqube", "-DsonarRunner.dumpToFile=" + out.getAbsolutePath())
      .directory(projectBaseDir)
      .inheritIO();
    Process p = pb.start();
    p.waitFor();

    Properties props = new Properties();
    try (FileReader fr = new FileReader(out)) {
      props.load(fr);
    }

    assertThat(props).contains(
      entry("sonar.projectKey", "groovy-java-gradle-mixed-tests"));
    assertThat(props.get("sonar.sources").toString()).contains("src/main/groovy");
    assertThat(props.get("sonar.tests").toString()).contains("src/test/groovy");

    assertThat(props.get("sonar.junit.reportsPath").toString()).contains("java-groovy-tests-gradle/build/test-results");
    assertThat(props.get("sonar.groovy.jacoco.reportPath").toString()).contains("java-groovy-tests-gradle/build/jacoco/test.exec");
    assertThat(props.get("sonar.jacoco.reportPath").toString()).contains("java-groovy-tests-gradle/build/jacoco/test.exec");
  }

  @Test
  public void loadSonarScannerPropertiesEnv() throws Exception {
    File out = temp.newFile();
    File projectBaseDir = new File(this.getClass().getResource("/java-gradle-simple").toURI());
    ProcessBuilder pb = new ProcessBuilder("/bin/bash", "gradlew", "--stacktrace", "sonarqube", "-DsonarRunner.dumpToFile=" + out.getAbsolutePath())
      .directory(projectBaseDir)
      .inheritIO();
    pb.environment().put("SONARQUBE_SCANNER_PARAMS", "{\"sonar.host.url\" : \"myhost\" }");
    Process p = pb.start();
    p.waitFor();

    Properties props = new Properties();
    try (FileReader fr = new FileReader(out)) {
      props.load(fr);
    }

    assertThat(props).contains(entry("sonar.projectKey", "org.codehaus.sonar:example-java-gradle"));
    assertThat(props).contains(entry("sonar.host.url", "myhost"));
    assertThat(props.get("sonar.sources").toString()).contains("src/main/java");
    assertThat(props.get("sonar.tests").toString()).contains("src/test/java");
  }

  @Test
  public void module_inclusion_duplicate_key() throws Exception {
    File out = temp.newFile();
    File projectBaseDir = new File(this.getClass().getResource("/module-inclusion").toURI());
    ProcessBuilder pb = new ProcessBuilder("/bin/bash", "gradlew", "--stacktrace", "sonarqube", "-DsonarRunner.dumpToFile=" + out.getAbsolutePath())
      .directory(projectBaseDir)
      .inheritIO();
    Process p = pb.start();
    p.waitFor();

    Properties props = new Properties();
    try (FileReader fr = new FileReader(out)) {
      props.load(fr);
    }

    assertThat(props).contains(entry("sonar.projectKey", "com.mygroup:root_project"));
    assertThat(props.get("sonar.modules").toString().split(",")).containsOnly(":toplevel1", ":toplevel2");

    assertThat(props).contains(entry(":toplevel1.sonar.moduleKey", "com.mygroup:root_project:toplevel1"));
    assertThat(props).contains(entry(":toplevel2.sonar.moduleKey", "com.mygroup:root_project:toplevel2"));

    assertThat(props).contains(entry(":toplevel1.sonar.modules", ":toplevel1:plugins"));
    assertThat(props).contains(entry(":toplevel2.sonar.modules", ":toplevel2:plugins"));

    assertThat(props).contains(entry(":toplevel1.:toplevel1:plugins.sonar.moduleKey", "com.mygroup:root_project:toplevel1:plugins"));
    assertThat(props).contains(entry(":toplevel2.:toplevel2:plugins.sonar.moduleKey", "com.mygroup:root_project:toplevel2:plugins"));
  }
}
