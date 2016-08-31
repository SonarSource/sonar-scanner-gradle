package org.sonarqube.gradle;

import java.util.Properties;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.Assume.assumeTrue;

public class AndroidTest extends AbstractGradleIT {

  @Test
  public void testAndroidProject() throws Exception {
    // android plugin 2.1.3 requires Gradle 2.14.1 but fails with Gradle 3
    String gradleVersion = System.getProperty("gradle.version");
    assumeTrue(gradleVersion.startsWith("2.14"));

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.1.3");

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(props.get("sonar.sources").toString()).contains("src/main/java", "src/main/AndroidManifest.xml");
    assertThat(props.get("sonar.tests").toString()).contains("src/test/java");
    assertThat(props.get("sonar.java.binaries").toString()).contains("android-gradle-2.1.3/build/intermediates/classes/release");
    assertThat(props.get("sonar.java.test.binaries").toString()).contains("android-gradle-2.1.3/build/intermediates/classes/test/release");
    assertThat(props.get("sonar.java.libraries").toString()).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.get("sonar.java.libraries").toString()).doesNotContain("junit-4.12.jar");
    assertThat(props.get("sonar.java.test.libraries").toString()).contains("junit-4.12.jar");
  }

  @Test
  public void testAndroidProjectGradle3() throws Exception {
    // android plugin requires Gradle 2.14.1
    String gradleVersion = System.getProperty("gradle.version");
    assumeTrue(gradleVersion.startsWith("2.14") || gradleVersion.startsWith("3.") || gradleVersion.startsWith("4."));

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.2.0-beta2");

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(props.get("sonar.sources").toString()).contains("src/main/java", "src/main/AndroidManifest.xml");
    assertThat(props.get("sonar.tests").toString()).contains("src/test/java");
    assertThat(props.get("sonar.java.binaries").toString()).contains("android-gradle-2.2.0-beta2/build/intermediates/classes/release");
    assertThat(props.get("sonar.java.test.binaries").toString()).contains("android-gradle-2.2.0-beta2/build/intermediates/classes/test/release");
    assertThat(props.get("sonar.java.libraries").toString()).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.get("sonar.java.libraries").toString()).doesNotContain("junit-4.12.jar");
    assertThat(props.get("sonar.java.test.libraries").toString()).contains("junit-4.12.jar");
  }

  @Test
  public void testUsingDefaultVariant() throws Exception {
    // android plugin requires Gradle 2.14.1
    String gradleVersion = System.getProperty("gradle.version");
    assumeTrue(gradleVersion.startsWith("2.14") || gradleVersion.startsWith("3.") || gradleVersion.startsWith("4."));

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.2.0-beta3-default-variant");

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(props.get("sonar.sources").toString()).contains("src/main/java", "src/main/AndroidManifest.xml");
    assertThat(props.get("sonar.tests").toString()).contains("src/test/java");
    assertThat(props.get("sonar.java.binaries").toString()).contains("android-gradle-2.2.0-beta3-default-variant/build/intermediates/classes/demo/release");
    assertThat(props.get("sonar.java.test.binaries").toString()).contains("android-gradle-2.2.0-beta3-default-variant/build/intermediates/classes/test/demo/release");
    assertThat(props.get("sonar.java.libraries").toString()).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.get("sonar.java.libraries").toString()).doesNotContain("junit-4.12.jar");
    assertThat(props.get("sonar.java.test.libraries").toString()).contains("junit-4.12.jar");
  }

  @Test
  public void testSpecifyVariant() throws Exception {
    // android plugin requires Gradle 2.14.1
    String gradleVersion = System.getProperty("gradle.version");
    assumeTrue(gradleVersion.startsWith("2.14") || gradleVersion.startsWith("3.") || gradleVersion.startsWith("4."));

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.2.0-beta3-nondefault-variant");

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(props.get("sonar.sources").toString()).contains("src/main/java", "src/main/AndroidManifest.xml");
    assertThat(props.get("sonar.tests").toString()).contains("src/test/java");
    assertThat(props.get("sonar.java.binaries").toString()).contains("android-gradle-2.2.0-beta3-nondefault-variant/build/intermediates/classes/full/debug");
    assertThat(props.get("sonar.java.test.binaries").toString()).contains("android-gradle-2.2.0-beta3-nondefault-variant/build/intermediates/classes/test/full/debug");
    assertThat(props.get("sonar.java.libraries").toString()).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.get("sonar.java.libraries").toString()).doesNotContain("junit-4.12.jar");
    assertThat(props.get("sonar.java.test.libraries").toString()).contains("junit-4.12.jar");
  }
}
