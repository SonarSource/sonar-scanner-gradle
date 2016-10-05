package org.sonarqube.gradle;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.junit.Test;

import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.Assume.assumeTrue;

public class AndroidTest extends AbstractGradleIT {

  @Test
  public void testAndroidProject2_1_3() throws Exception {
    // android plugin 2.1.3 requires Gradle 2.14.1 but fails with Gradle 3
    assumeTrue(getGradleVersion().startsWith("2.14"));

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.1.3");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .contains(baseDir.resolve("src/main/java"), baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/debug"));
    assertThat(Paths.get(props.getProperty("sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/test/debug"));
    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.source")).isEqualTo("1.7");
    assertThat(props.getProperty("sonar.java.target")).isEqualTo("1.7");
  }

  @Test
  public void testAndroidProjectGradle3() throws Exception {
    assumeGradle2_14_1_or_more();

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.2.0");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .contains(baseDir.resolve("src/main/java"), baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/debug"));
    assertThat(Paths.get(props.getProperty("sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/test/debug"));
    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.source")).isEqualTo("1.8");
    assertThat(props.getProperty("sonar.java.target")).isEqualTo("1.8");
  }

  @Test
  public void testUsingDefaultVariant() throws Exception {
    assumeGradle2_14_1_or_more();

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.2.0-default-variant");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .contains(baseDir.resolve("src/main/java"), baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/demo/debug"));
    assertThat(Paths.get(props.getProperty("sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/test/demo/debug"));
    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
  }

  @Test
  public void testSpecifyVariant() throws Exception {
    assumeGradle2_14_1_or_more();

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.2.0-nondefault-variant");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .contains(baseDir.resolve("src/main/java"), baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/full/release"));
    assertThat(Paths.get(props.getProperty("sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/test/full/release"));
    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
  }

  @Test
  public void testMultiModule() throws Exception {
    assumeGradle2_14_1_or_more();

    Properties props = runGradlewSonarQubeSimulationMode("/multi-module-android-studio");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props.getProperty(":app.sonar.moduleKey")).isEqualTo("com.test.app:multi-module-android-studio:app");
    assertThat(stream(props.getProperty(":app.sonar.sources").split(",")).map(Paths::get))
      .contains(baseDir.resolve("app/src/main/java"), baseDir.resolve("app/src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty(":app.sonar.tests"))).isEqualTo(baseDir.resolve("app/src/test/java"));
    assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries"))).isEqualTo(baseDir.resolve("app/build/intermediates/classes/debug"));
    assertThat(Paths.get(props.getProperty(":app.sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("app/build/intermediates/classes/test/debug"));
    assertThat(props.getProperty(":app.sonar.java.libraries")).contains("android.jar");
    assertThat(props.getProperty(":app.sonar.java.libraries")).doesNotContain("hamcrest-core-1.3.jar");
    assertThat(props.getProperty(":app.sonar.java.test.libraries")).contains("hamcrest-core-1.3.jar");
    assertThat(props.getProperty(":app.sonar.java.source")).isEqualTo("1.7");
    assertThat(props.getProperty(":app.sonar.java.target")).isEqualTo("1.7");
  }

  private void assumeGradle2_14_1_or_more() {
    // android plugin 2.2.x requires Gradle 2.14.1
    String gradleVersion = getGradleVersion();
    assumeTrue(gradleVersion.startsWith("2.14") || gradleVersion.startsWith("3.") || gradleVersion.startsWith("4."));
  }
}
