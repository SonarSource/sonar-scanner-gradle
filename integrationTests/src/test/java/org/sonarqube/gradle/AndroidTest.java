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
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/debug"));
    assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("build/intermediates/classes/test/debug"),
        baseDir.resolve("build/intermediates/classes/androidTest/debug"));
    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.source")).isEqualTo("1.7");
    assertThat(props.getProperty("sonar.java.target")).isEqualTo("1.7");
  }

  @Test
  public void testAndroidProjectJdk8Jack() throws Exception {
    assumeGradle2_14_1_or_more();

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.2.0-jack");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
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
  public void testAndroidProjectJdk8Retrolambda() throws Exception {
    assumeGradle2_14_1_or_more();

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.2.0-retrolambda");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/debug"));
    assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("build/intermediates/classes/test/debug"),
        baseDir.resolve("build/intermediates/classes/androidTest/debug"));
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
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/demo/debug"));
    assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("build/intermediates/classes/test/demo/debug"),
        baseDir.resolve("build/intermediates/classes/androidTest/demo/debug")); // WHY ???
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
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
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
      .containsOnly(
        baseDir.resolve("app/src/main/java"),
        baseDir.resolve("app/src/main/res"),
        baseDir.resolve("app/src/main/AndroidManifest.xml"));
    assertThat(stream(props.getProperty(":app.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("app/src/test/java"),
        baseDir.resolve("app/src/androidTest/java"));
    assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries"))).isEqualTo(baseDir.resolve("app/build/intermediates/classes/debug"));
    assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("app/build/intermediates/classes/test/debug"),
        baseDir.resolve("app/build/intermediates/classes/androidTest/debug"));
    assertThat(props.getProperty(":app.sonar.java.libraries")).contains("android.jar");
    assertThat(props.getProperty(":app.sonar.java.libraries")).doesNotContain("hamcrest-core-1.3.jar");
    assertThat(props.getProperty(":app.sonar.java.test.libraries")).contains("hamcrest-core-1.3.jar");
    assertThat(props.getProperty(":app.sonar.java.source")).isEqualTo("1.7");
    assertThat(props.getProperty(":app.sonar.java.target")).isEqualTo("1.7");
  }

  @Test
  public void testingBlueprint_default_flavor() throws Exception {
    assumeGradle2_14_1_or_more();

    // First flavor that is picked up seems to be the flavor2

    Properties props = runGradlewSonarQubeSimulationMode("/AndroidTestingBlueprint");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    // App module contains main code + local tests + instrumented tests
    assertThat(stream(props.getProperty(":app.sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("app/src/main/java"),
        baseDir.resolve("app/src/main/res"),
        baseDir.resolve("app/src/main/AndroidManifest.xml"),
        baseDir.resolve("app/src/flavor2/res"));
    assertThat(stream(props.getProperty(":app.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("app/src/test/java"),
        baseDir.resolve("app/src/test/resources"),
        baseDir.resolve("app/src/androidTest/java"),
        baseDir.resolve("app/src/androidTest/AndroidManifest.xml"),
        baseDir.resolve("app/src/androidTestFlavor2/java"));
    assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries"))).isEqualTo(baseDir.resolve("app/build/intermediates/classes/flavor2/debug"));
    assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("app/build/intermediates/classes/test/flavor2/debug"),
        baseDir.resolve("app/build/intermediates/classes/androidTest/flavor2/debug"));
    assertThat(props.getProperty(":app.sonar.java.source")).isEqualTo("1.7");
    assertThat(props.getProperty(":app.sonar.java.target")).isEqualTo("1.7");

    // Library module has no local tests
    assertThat(stream(props.getProperty(":module-android-library.sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-android-library/src/main/java"),
        baseDir.resolve("module-android-library/src/main/res"),
        baseDir.resolve("module-android-library/src/main/AndroidManifest.xml"));
    assertThat(stream(props.getProperty(":module-android-library.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-android-library/src/androidTest/java"));
    assertThat(Paths.get(props.getProperty(":module-android-library.sonar.java.binaries"))).isEqualTo(baseDir.resolve("module-android-library/build/intermediates/classes/debug"));
    assertThat(stream(props.getProperty(":module-android-library.sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-android-library/build/intermediates/classes/androidTest/debug"));
    assertThat(props.getProperty(":module-android-library.sonar.java.source")).isEqualTo("1.7");
    assertThat(props.getProperty(":module-android-library.sonar.java.target")).isEqualTo("1.7");

    // test only module
    assertThat(props.getProperty(":module-flavor1-androidTest-only.sonar.sources")).isEmpty();
    assertThat(stream(props.getProperty(":module-flavor1-androidTest-only.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-flavor1-androidTest-only/src/main/java"),
        baseDir.resolve("module-flavor1-androidTest-only/src/main/AndroidManifest.xml"));
    assertThat(props).doesNotContainKey(":module-flavor1-androidTest-only.sonar.java.binaries");
    assertThat(stream(props.getProperty(":module-flavor1-androidTest-only.sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-flavor1-androidTest-only/build/intermediates/classes/debug"));
    assertThat(props.getProperty(":module-flavor1-androidTest-only.sonar.java.source")).isEqualTo("1.7");
    assertThat(props.getProperty(":module-flavor1-androidTest-only.sonar.java.target")).isEqualTo("1.7");

    // regular java module
    assertThat(stream(props.getProperty(":module-plain-java.sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-plain-java/src/main/java"));
    assertThat(stream(props.getProperty(":module-plain-java.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-plain-java/src/test/java"));
    assertThat(Paths.get(props.getProperty(":module-plain-java.sonar.java.binaries"))).isEqualTo(baseDir.resolve("module-plain-java/build/classes/main"));
    assertThat(Paths.get(props.getProperty(":module-plain-java.sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("module-plain-java/build/classes/test"));
    assertThat(props.getProperty(":module-plain-java.sonar.java.source")).isEqualTo("1.7");
    assertThat(props.getProperty(":module-plain-java.sonar.java.target")).isEqualTo("1.7");
  }

  // SONARGRADL-22
  @Test
  public void noDebugVariant() throws Exception {
    assumeGradle2_14_1_or_more();

    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.2.0-no-debug");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/release"));
    assertThat(Paths.get(props.getProperty("sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/test/release"));
    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
  }

  private void assumeGradle2_14_1_or_more() {
    // android plugin 2.2.x requires Gradle 2.14.1
    String gradleVersion = getGradleVersion();
    assumeTrue(gradleVersion.startsWith("2.14") || gradleVersion.startsWith("3.") || gradleVersion.startsWith("4."));
  }
}
