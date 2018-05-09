/**
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2018 SonarSource
 * sonarqube@googlegroups.com
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarqube.gradle;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Properties;
import org.junit.BeforeClass;
import org.junit.Test;

import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.Assume.assumeFalse;

public class AndroidTest extends AbstractGradleIT {

  @BeforeClass
  public static void verifyGradleVersion() {
    assumeFalse(getGradleVersion().startsWith("2.") || getGradleVersion().startsWith("3."));
  }

  @Test
  public void testAndroid2_3_3() throws Exception {
    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-2.3.3");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/demoMinApi23/debug"));
    assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("build/intermediates/classes/test/demoMinApi23/debug"),
        baseDir.resolve("build/intermediates/classes/androidTest/demoMinApi23/debug")); // WHY ???
    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
  }

  @Test
  public void testAndroidProjectJdk8Retrolambda() throws Exception {
    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-retrolambda");

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
    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-default-variant");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/demoMinApi23/debug"));
    assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("build/intermediates/classes/test/demoMinApi23/debug"),
        baseDir.resolve("build/intermediates/classes/androidTest/demoMinApi23/debug")); // WHY ???
    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
  }

  @Test
  public void testSpecifyVariant() throws Exception {
    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-nondefault-variant");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    assertThat(Paths.get(props.getProperty("sonar.java.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/fullMinApi23/release"));
    assertThat(Paths.get(props.getProperty("sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("build/intermediates/classes/test/fullMinApi23/release"));
    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
  }

  @Test
  public void testMultiModule() throws Exception {
    Properties props = runGradlewSonarQubeSimulationMode("/multi-module-android-studio");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("com.test.app:app");
    assertThat(props.getProperty(":app.sonar.moduleKey")).isEqualTo("com.test.app:app:app");
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

    // Feature module has no local tests
    assertThat(stream(props.getProperty(":module-android-feature.sonar.sources").split(",")).map(Paths::get))
            .containsOnly(
                    baseDir.resolve("module-android-feature/src/main/java"),
                    baseDir.resolve("module-android-feature/src/main/res"),
                    baseDir.resolve("module-android-feature/src/main/AndroidManifest.xml"));
    assertThat(stream(props.getProperty(":module-android-feature.sonar.tests").split(",")).map(Paths::get))
            .containsOnly(
                    baseDir.resolve("module-android-feature/src/androidTest/java"));
    assertThat(Paths.get(props.getProperty(":module-android-feature.sonar.java.binaries"))).isEqualTo(baseDir.resolve("module-android-feature/build/intermediates/classes/debug"));
    assertThat(stream(props.getProperty(":module-android-feature.sonar.java.test.binaries").split(",")).map(Paths::get))
            .containsOnly(
                    baseDir.resolve("module-android-feature/build/intermediates/classes/androidTest/debug"));
    assertThat(props.getProperty(":module-android-feature.sonar.java.source")).isEqualTo("1.7");
    assertThat(props.getProperty(":module-android-feature.sonar.java.target")).isEqualTo("1.7");

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
    if (getGradleVersion().startsWith("2.") || getGradleVersion().startsWith("3.")) {
      assertThat(Paths.get(props.getProperty(":module-plain-java.sonar.java.binaries"))).isEqualTo(baseDir.resolve("module-plain-java/build/classes/main"));
      assertThat(Paths.get(props.getProperty(":module-plain-java.sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("module-plain-java/build/classes/test"));
    } else {
      assertThat(Paths.get(props.getProperty(":module-plain-java.sonar.java.binaries"))).isEqualTo(baseDir.resolve("module-plain-java/build/classes/java/main"));
      assertThat(Paths.get(props.getProperty(":module-plain-java.sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("module-plain-java/build/classes/java/test"));
    }
    assertThat(props.getProperty(":module-plain-java.sonar.java.source")).isEqualTo("1.7");
    assertThat(props.getProperty(":module-plain-java.sonar.java.target")).isEqualTo("1.7");
  }

  @Test
  public void testingBlueprint_task_dependencies() throws Exception {
    // First flavor that is picked up seems to be the flavor2

    RunResult result = runGradlewWithEnvQuietly("/AndroidTestingBlueprint", Collections.emptyMap(), "sonarqube", "taskTree");

    assertThat(result.getLog().split("\\r?\\n")).containsSubsequence(":sonarqube",
      "+--- :app:compileFlavor2DebugAndroidTestJavaWithJavac",
      "+--- :app:compileFlavor2DebugUnitTestJavaWithJavac",
      "+--- :module-android-library:compileDebugAndroidTestJavaWithJavac",
      "+--- :module-android-library:compileDebugUnitTestJavaWithJavac",
      "+--- :module-flavor1-androidTest-only:compileDebugJavaWithJavac",
      "\\--- :module-plain-java:test");
  }

  // SONARGRADL-22
  @Test
  public void noDebugVariant() throws Exception {
    Properties props = runGradlewSonarQubeSimulationMode("/android-gradle-no-debug");

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

}
