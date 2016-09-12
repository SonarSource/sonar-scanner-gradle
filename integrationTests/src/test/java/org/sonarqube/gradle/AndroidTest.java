package org.sonarqube.gradle;

import java.util.Properties;
import org.junit.Ignore;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.Assume.assumeTrue;

public class AndroidTest extends AbstractGradleIT {

  @Test
  public void testAndroidProject2_1_3() throws Exception {
    // android plugin 2.1.3 requires Gradle 2.14.1 but fails with Gradle 3
    String gradleVersion = System.getProperty("gradle.version");
    assumeTrue(gradleVersion.startsWith("2.14"));

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.1.3");

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(props.get("sonar.sources").toString()).contains("src/main/java", "src/main/AndroidManifest.xml");
    assertThat(props.get("sonar.tests").toString()).contains("src/test/java");
    assertThat(props.get("sonar.java.binaries").toString()).contains("android-gradle-2.1.3/build/intermediates/classes/debug");
    assertThat(props.get("sonar.java.test.binaries").toString()).contains("android-gradle-2.1.3/build/intermediates/classes/test/debug");
    assertThat(props.get("sonar.java.libraries").toString()).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.get("sonar.java.libraries").toString()).doesNotContain("junit-4.12.jar");
    assertThat(props.get("sonar.java.test.libraries").toString()).contains("junit-4.12.jar");
    assertThat(props.get("sonar.java.source").toString()).isEqualTo("1.7");
    assertThat(props.get("sonar.java.target").toString()).isEqualTo("1.7");
  }

  @Ignore
  @Test
  public void testAndroidProjectGradle3() throws Exception {
    assumeGradle2_14_1();

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.2.0");

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(props.get("sonar.sources").toString()).contains("src/main/java", "src/main/AndroidManifest.xml");
    assertThat(props.get("sonar.tests").toString()).contains("src/test/java");
    assertThat(props.get("sonar.java.binaries").toString()).contains("android-gradle-2.2.0/build/intermediates/classes/debug");
    assertThat(props.get("sonar.java.test.binaries").toString()).contains("android-gradle-2.2.0/build/intermediates/classes/test/debug");
    assertThat(props.get("sonar.java.libraries").toString()).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.get("sonar.java.libraries").toString()).doesNotContain("junit-4.12.jar");
    assertThat(props.get("sonar.java.test.libraries").toString()).contains("junit-4.12.jar");
    assertThat(props.get("sonar.java.source").toString()).isEqualTo("1.8");
    assertThat(props.get("sonar.java.target").toString()).isEqualTo("1.8");
  }

  @Ignore
  @Test
  public void testUsingDefaultVariant() throws Exception {
    assumeGradle2_14_1();

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.2.0-default-variant");

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(props.get("sonar.sources").toString()).contains("src/main/java", "src/main/AndroidManifest.xml");
    assertThat(props.get("sonar.tests").toString()).contains("src/test/java");
    assertThat(props.get("sonar.java.binaries").toString()).contains("android-gradle-2.2.0-default-variant/build/intermediates/classes/demo/debug");
    assertThat(props.get("sonar.java.test.binaries").toString()).contains("android-gradle-2.2.0-default-variant/build/intermediates/classes/test/demo/debug");
    assertThat(props.get("sonar.java.libraries").toString()).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.get("sonar.java.libraries").toString()).doesNotContain("junit-4.12.jar");
    assertThat(props.get("sonar.java.test.libraries").toString()).contains("junit-4.12.jar");
  }

  @Ignore
  @Test
  public void testSpecifyVariant() throws Exception {
    assumeGradle2_14_1();

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.2.0-nondefault-variant");

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(props.get("sonar.sources").toString()).contains("src/main/java", "src/main/AndroidManifest.xml");
    assertThat(props.get("sonar.tests").toString()).contains("src/test/java");
    assertThat(props.get("sonar.java.binaries").toString()).contains("android-gradle-2.2.0-nondefault-variant/build/intermediates/classes/full/release");
    assertThat(props.get("sonar.java.test.binaries").toString()).contains("android-gradle-2.2.0-nondefault-variant/build/intermediates/classes/test/full/release");
    assertThat(props.get("sonar.java.libraries").toString()).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.get("sonar.java.libraries").toString()).doesNotContain("junit-4.12.jar");
    assertThat(props.get("sonar.java.test.libraries").toString()).contains("junit-4.12.jar");
  }

  @Ignore
  @Test
  public void testMultiModule() throws Exception {
    assumeGradle2_14_1();

    Properties props = runGradlewSonarQubeSimulationMode("/multi-module-android-studio");

    assertThat(props.get(":app.sonar.moduleKey").toString()).isEqualTo("com.test.app:multi-module-android-studio:app");
    assertThat(props.get(":app.sonar.sources").toString()).contains("multi-module-android-studio/app/src/main/java",
      "multi-module-android-studio/app/src/main/AndroidManifest.xml");
    assertThat(props.get(":app.sonar.tests").toString()).contains("multi-module-android-studio/app/src/test/java");
    assertThat(props.get(":app.sonar.java.binaries").toString()).contains("multi-module-android-studio/app/build/intermediates/classes/debug");
    assertThat(props.get(":app.sonar.java.test.binaries").toString()).contains("multi-module-android-studio/app/build/intermediates/classes/test/debug");
    assertThat(props.get(":app.sonar.java.libraries").toString()).contains("android.jar");
    assertThat(props.get(":app.sonar.java.libraries").toString()).doesNotContain("hamcrest-core-1.3.jar");
    assertThat(props.get(":app.sonar.java.test.libraries").toString()).contains("hamcrest-core-1.3.jar");
    assertThat(props.get(":app.sonar.java.source").toString()).isEqualTo("1.7");
    assertThat(props.get(":app.sonar.java.target").toString()).isEqualTo("1.7");
  }

  private void assumeGradle2_14_1() {
    // android plugin 2.2.x requires Gradle 2.14.1
    String gradleVersion = System.getProperty("gradle.version");
    assumeTrue(gradleVersion.startsWith("2.14") || gradleVersion.startsWith("3.") || gradleVersion.startsWith("4."));
  }
}
