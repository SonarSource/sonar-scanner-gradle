/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2023 SonarSource SA
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.junit.Assume;
import org.junit.Test;
import org.junit.BeforeClass;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assume.assumeNotNull;

public class AndroidTest extends AbstractGradleIT {

  @BeforeClass
  public static void beforeAll() {
    assumeNotNull(getAndroidGradleVersion());
  }

  private boolean shouldExpectOldJavaBinariesDir() {
    return getAndroidGradleVersion().isLowerThan("3.5.0");
  }

  private boolean supportAndroidFeatureModule() {
    return getAndroidGradleVersion().isLowerThan("4.0.0");
  }

  private boolean supportObtainingAndroidMinSdkVersion() {
    return getAndroidGradleVersion().isGreaterThanOrEqualTo("4.1.0");
  }

  @Test
  public void testAndroidProjectJdk8Retrolambda() throws Exception {
    Properties props = runGradlewSonarSimulationMode("/android-gradle-retrolambda");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    if (shouldExpectOldJavaBinariesDir()) {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"));
      assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
          baseDir.resolve("build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes"));
    } else {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/debug/classes"));
      assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("build/intermediates/javac/debugUnitTest/classes"),
          baseDir.resolve("build/intermediates/javac/debugAndroidTest/classes"));
    }

    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.source")).isEqualTo("1.8");
    assertThat(props.getProperty("sonar.java.target")).isEqualTo("1.8");
  }

  @Test
  public void testUsingDefaultVariant() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/android-gradle-default-variant", emptyMap(), "test");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));

    if (shouldExpectOldJavaBinariesDir()) {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/demoMinApi23Debug/compileDemoMinApi23DebugJavaWithJavac/classes"));
      assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("build/intermediates/javac/demoMinApi23DebugUnitTest/compileDemoMinApi23DebugUnitTestJavaWithJavac/classes"),
          baseDir.resolve("build/intermediates/javac/demoMinApi23DebugAndroidTest/compileDemoMinApi23DebugAndroidTestJavaWithJavac/classes"));
    } else {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/demoMinApi23Debug/classes"));
      assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("build/intermediates/javac/demoMinApi23DebugUnitTest/classes"),
          baseDir.resolve("build/intermediates/javac/demoMinApi23DebugAndroidTest/classes"));
    }

    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
    if (getGradleVersion().isGreaterThan("6.0")) {
      assertThat(props.getProperty("sonar.junit.reportPaths")).contains(baseDir.resolve("build/test-results/testDemoMinApi23DebugUnitTest").toString());
    }
    assertThat(props.getProperty("sonar.android.detected")).contains("true");

    if (supportObtainingAndroidMinSdkVersion()) {
      assertThat(props.getProperty("sonar.android.minsdkversion.min")).contains("21");
      assertThat(props.getProperty("sonar.android.minsdkversion.max")).contains("25");
    }

  }

  @Test
  public void testAndroidDynamicFeature() throws Exception {
    Properties props = runGradlewSonarSimulationMode("/android-gradle-dynamic-feature");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle-dynamic-module"));
    assertThat(stream(props.getProperty("sonar.modules").split(",")))
      .containsOnly(":app", ":mydynamicfeature");

    assertThat(stream(props.getProperty(":app.sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("app/src/main/java"),
        baseDir.resolve("app/src/main/res"),
        baseDir.resolve("app/src/main/AndroidManifest.xml"));
    assertThat(stream(props.getProperty(":app.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("app/src/test/java"),
        baseDir.resolve("app/src/androidTest/java"));
    if (shouldExpectOldJavaBinariesDir()) {
      assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"));
      assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
          baseDir.resolve("app/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes"));
    } else {
      assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("app/build/intermediates/javac/debug/classes"));
      assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("app/build/intermediates/javac/debugUnitTest/classes"),
          baseDir.resolve("app/build/intermediates/javac/debugAndroidTest/classes"));
    }
    assertThat(props.getProperty(":app.sonar.java.libraries")).contains("android.jar");
    assertThat(props.getProperty(":app.sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty(":app.sonar.java.test.libraries")).contains("junit-4.12.jar");

    assertThat(stream(props.getProperty(":mydynamicfeature.sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("mydynamicfeature/src/main/java"),
        baseDir.resolve("mydynamicfeature/src/main/AndroidManifest.xml"));
    assertThat(stream(props.getProperty(":mydynamicfeature.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("mydynamicfeature/src/test/java"),
        baseDir.resolve("mydynamicfeature/src/androidTest/java"));
    if (shouldExpectOldJavaBinariesDir()) {
      assertThat(Paths.get(props.getProperty(":mydynamicfeature.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("mydynamicfeature/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"));
      assertThat(stream(props.getProperty(":mydynamicfeature.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("mydynamicfeature/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
          baseDir.resolve("mydynamicfeature/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes"));
    } else {
      assertThat(Paths.get(props.getProperty(":mydynamicfeature.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("mydynamicfeature/build/intermediates/javac/debug/classes"));
      assertThat(stream(props.getProperty(":mydynamicfeature.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("mydynamicfeature/build/intermediates/javac/debugUnitTest/classes"),
          baseDir.resolve("mydynamicfeature/build/intermediates/javac/debugAndroidTest/classes"));
    }
    assertThat(props.getProperty(":mydynamicfeature.sonar.java.libraries")).contains("android.jar");
    assertThat(props.getProperty(":mydynamicfeature.sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty(":mydynamicfeature.sonar.java.test.libraries")).contains("junit-4.12.jar");
    assertThat(props.getProperty(":mydynamicfeature.sonar.android.detected")).contains("true");
  }

  @Test
  public void testSpecifyVariant() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/android-gradle-nondefault-variant", emptyMap(), "test");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));
    if (shouldExpectOldJavaBinariesDir()) {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/fullMinApi23Release/compileFullMinApi23ReleaseJavaWithJavac/classes"));
      assertThat(Paths.get(props.getProperty("sonar.java.test.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/fullMinApi23ReleaseUnitTest/compileFullMinApi23ReleaseUnitTestJavaWithJavac/classes"));
    } else {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/fullMinApi23Release/classes"));
      assertThat(Paths.get(props.getProperty("sonar.java.test.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/fullMinApi23ReleaseUnitTest/classes"));
    }
    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
    assertThat(props.getProperty("sonar.android.detected")).contains("true");
    if (getGradleVersion().isGreaterThan("6.0")) {
      assertThat(props.getProperty("sonar.junit.reportPaths")).contains(baseDir.resolve("build/test-results/testFullMinApi23ReleaseUnitTest").toString());
    }
  }

  @Test
  public void testMultiModule() throws Exception {
    Properties props = runGradlewSonarSimulationMode("/multi-module-android-studio");

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

    if (shouldExpectOldJavaBinariesDir()) {
      assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"));
      assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
          baseDir.resolve("app/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes"));
    } else {
      assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("app/build/intermediates/javac/debug/classes"));
      assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("app/build/intermediates/javac/debugUnitTest/classes"),
          baseDir.resolve("app/build/intermediates/javac/debugAndroidTest/classes"));
    }

    assertThat(props.getProperty(":app.sonar.java.libraries")).contains("android.jar");
    assertThat(props.getProperty(":app.sonar.java.libraries")).doesNotContain("hamcrest-core-1.3.jar");
    assertThat(props.getProperty(":app.sonar.java.test.libraries")).contains("hamcrest-core-1.3.jar");
    assertThat(props.getProperty(":app.sonar.java.source")).isEqualTo("1.8");
    assertThat(props.getProperty(":app.sonar.java.target")).isEqualTo("1.8");
    assertThat(props.getProperty(":app.sonar.android.detected")).contains("true");
  }

  @Test
  public void testingBlueprintWithFeatureModule_default_flavor() throws Exception {
    assumeTrue("Assume that Android Gradle Plugin version supports com.android.feature modules", supportAndroidFeatureModule());

    // First flavor that is picked up seems to be the flavor2

    Properties props = runGradlewSonarSimulationMode("/AndroidTestingBlueprintWithFeatureModule");

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
    assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries")))
      .isEqualTo(baseDir.resolve("app/build/intermediates/javac/flavor2Debug/compileFlavor2DebugJavaWithJavac/classes"));
    assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("app/build/intermediates/javac/flavor2DebugUnitTest/compileFlavor2DebugUnitTestJavaWithJavac/classes"),
        baseDir.resolve("app/build/intermediates/javac/flavor2DebugAndroidTest/compileFlavor2DebugAndroidTestJavaWithJavac/classes"));
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
    assertThat(Paths.get(props.getProperty(":module-android-library.sonar.java.binaries")))
      .isEqualTo(baseDir.resolve("module-android-library/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"));
    assertThat(stream(props.getProperty(":module-android-library.sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-android-library/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes"));
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
    assertThat(Paths.get(props.getProperty(":module-android-feature.sonar.java.binaries")))
      .isEqualTo(baseDir.resolve("module-android-feature/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"));
    assertThat(stream(props.getProperty(":module-android-feature.sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-android-feature/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes"));
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
        baseDir.resolve("module-flavor1-androidTest-only/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"));
    assertThat(props.getProperty(":module-flavor1-androidTest-only.sonar.java.source")).isEqualTo("1.7");
    assertThat(props.getProperty(":module-flavor1-androidTest-only.sonar.java.target")).isEqualTo("1.7");

    // regular java module
    assertThat(stream(props.getProperty(":module-plain-java.sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-plain-java/src/main/java"));
    assertThat(stream(props.getProperty(":module-plain-java.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-plain-java/src/test/java"));
    assertThat(Paths.get(props.getProperty(":module-plain-java.sonar.java.binaries"))).isEqualTo(baseDir.resolve("module-plain-java/build/classes/java/main"));
    assertThat(Paths.get(props.getProperty(":module-plain-java.sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("module-plain-java/build/classes/java/test"));
    assertThat(props.getProperty(":module-plain-java.sonar.java.source")).isEqualTo("1.8");
    assertThat(props.getProperty(":module-plain-java.sonar.java.target")).isEqualTo("1.8");
  }

  @Test
  public void testingBlueprintWithFeatureModule_task_dependencies() throws Exception {
    assumeTrue("Assume that Android Gradle Plugin version supports com.android.feature modules", supportAndroidFeatureModule());

    // First flavor that is picked up seems to be the flavor2

    RunResult result = runGradlewWithEnvQuietly("/AndroidTestingBlueprintWithFeatureModule", null, emptyMap(), "sonar", "--dry-run", "--max-workers=1");

    assertThat(stream(result.getLog().split("\\r?\\n")).sorted()).containsSubsequence(
      ":app:compileFlavor2DebugAndroidTestJavaWithJavac SKIPPED",
      ":app:compileFlavor2DebugUnitTestJavaWithJavac SKIPPED",
      ":module-android-library:compileDebugAndroidTestJavaWithJavac SKIPPED",
      ":module-android-library:compileDebugUnitTestJavaWithJavac SKIPPED",
      ":module-flavor1-androidTest-only:compileDebugJavaWithJavac SKIPPED",
      ":module-plain-java:compileTestJava SKIPPED",
      ":sonar SKIPPED");
  }

  @Test
  public void testingBlueprintWithDynamicFeatureModule_default_flavor() throws Exception {
    assumeTrue(getAndroidGradleVersion().isGreaterThanOrEqualTo("4.1.0"));

    // First flavor that is picked up seems to be the flavor1

    Properties props = runGradlewSonarSimulationMode("/AndroidTestingBlueprintWithDynamicFeatureModule");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    // App module contains main code + local tests + instrumented tests
    assertThat(stream(props.getProperty(":app.sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("app/src/main/java"),
        baseDir.resolve("app/src/main/res"),
        baseDir.resolve("app/src/main/AndroidManifest.xml"),
        baseDir.resolve("app/src/flavor1/java"),
        baseDir.resolve("app/src/flavor1/res"));
    assertThat(stream(props.getProperty(":app.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("app/src/test/java"),
        baseDir.resolve("app/src/test/resources"),
        baseDir.resolve("app/src/androidTest/java"),
        baseDir.resolve("app/src/androidTest/AndroidManifest.xml"),
        baseDir.resolve("app/src/testFlavor1/java"));
    assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries")))
      .isEqualTo(baseDir.resolve("app/build/intermediates/javac/flavor1Debug/classes"));
    assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("app/build/intermediates/javac/flavor1DebugUnitTest/classes"),
        baseDir.resolve("app/build/intermediates/javac/flavor1DebugAndroidTest/classes"));
    assertThat(props.getProperty(":app.sonar.java.source")).isEqualTo("1.8");
    assertThat(props.getProperty(":app.sonar.java.target")).isEqualTo("1.8");

    // Library module has no local tests
    assertThat(stream(props.getProperty(":module-android-library.sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-android-library/src/main/java"),
        baseDir.resolve("module-android-library/src/main/res"),
        baseDir.resolve("module-android-library/src/main/AndroidManifest.xml"));
    assertThat(stream(props.getProperty(":module-android-library.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-android-library/src/androidTest/java"));
    assertThat(Paths.get(props.getProperty(":module-android-library.sonar.java.binaries")))
      .isEqualTo(baseDir.resolve("module-android-library/build/intermediates/javac/debug/classes"));
    assertThat(stream(props.getProperty(":module-android-library.sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-android-library/build/intermediates/javac/debugAndroidTest/classes"));
    assertThat(props.getProperty(":module-android-library.sonar.java.source")).isEqualTo("1.8");
    assertThat(props.getProperty(":module-android-library.sonar.java.target")).isEqualTo("1.8");

    // Feature module has no local tests
    assertThat(stream(props.getProperty(":module_android_feature.sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module_android_feature/src/main/java"),
        baseDir.resolve("module_android_feature/src/main/res"),
        baseDir.resolve("module_android_feature/src/main/AndroidManifest.xml"));
    assertThat(stream(props.getProperty(":module_android_feature.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module_android_feature/src/androidTest/java"));
    assertThat(Paths.get(props.getProperty(":module_android_feature.sonar.java.binaries")))
      .isEqualTo(baseDir.resolve("module_android_feature/build/intermediates/javac/flavor1Debug/classes"));
    assertThat(stream(props.getProperty(":module_android_feature.sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module_android_feature/build/intermediates/javac/flavor1DebugAndroidTest/classes"));
    assertThat(props.getProperty(":module_android_feature.sonar.java.source")).isEqualTo("1.8");
    assertThat(props.getProperty(":module_android_feature.sonar.java.target")).isEqualTo("1.8");

    // test only module
    assertThat(props.getProperty(":module-flavor1-androidTest-only.sonar.sources")).isEmpty();
    assertThat(stream(props.getProperty(":module-flavor1-androidTest-only.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-flavor1-androidTest-only/src/main/java"),
        baseDir.resolve("module-flavor1-androidTest-only/src/main/AndroidManifest.xml"));
    assertThat(props).doesNotContainKey(":module-flavor1-androidTest-only.sonar.java.binaries");
    assertThat(stream(props.getProperty(":module-flavor1-androidTest-only.sonar.java.test.binaries").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-flavor1-androidTest-only/build/intermediates/javac/debug/classes"));
    assertThat(props.getProperty(":module-flavor1-androidTest-only.sonar.java.source")).isEqualTo("1.8");
    assertThat(props.getProperty(":module-flavor1-androidTest-only.sonar.java.target")).isEqualTo("1.8");

    // regular java module
    assertThat(stream(props.getProperty(":module-plain-java.sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-plain-java/src/main/java"));
    assertThat(stream(props.getProperty(":module-plain-java.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-plain-java/src/test/java"));
    assertThat(Paths.get(props.getProperty(":module-plain-java.sonar.java.binaries"))).isEqualTo(baseDir.resolve("module-plain-java/build/classes/java/main"));
    assertThat(Paths.get(props.getProperty(":module-plain-java.sonar.java.test.binaries"))).isEqualTo(baseDir.resolve("module-plain-java/build/classes/java/test"));
    assertThat(props.getProperty(":module-plain-java.sonar.java.source")).isEqualTo("1.8");
    assertThat(props.getProperty(":module-plain-java.sonar.java.target")).isEqualTo("1.8");
  }

  @Test
  public void testingBlueprintWithDynamicFeatureModule_task_dependencies() throws Exception {
    assumeTrue(getAndroidGradleVersion().isGreaterThanOrEqualTo("4.1.0"));

    // First flavor that is picked up seems to be the flavor1

    RunResult result = runGradlewWithEnvQuietly("/AndroidTestingBlueprintWithDynamicFeatureModule", null, emptyMap(), "sonar", "--dry-run", "--max-workers=1");

    assertThat(stream(result.getLog().split("\\r?\\n")).sorted()).containsSubsequence(
      ":app:compileFlavor1DebugAndroidTestJavaWithJavac SKIPPED",
      ":app:compileFlavor1DebugUnitTestJavaWithJavac SKIPPED",
      ":module-android-library:compileDebugAndroidTestJavaWithJavac SKIPPED",
      ":module-android-library:compileDebugUnitTestJavaWithJavac SKIPPED",
      ":module-flavor1-androidTest-only:compileDebugJavaWithJavac SKIPPED",
      ":module-plain-java:compileTestJava SKIPPED",
      ":module_android_feature:compileFlavor1DebugJavaWithJavac SKIPPED",
      ":sonar SKIPPED");
  }

  // SONARGRADL-22
  @Test
  public void noDebugVariant() throws Exception {
    Properties props = runGradlewSonarSimulationMode("/android-gradle-no-debug");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));

    if (shouldExpectOldJavaBinariesDir()) {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/release/compileReleaseJavaWithJavac/classes"));
      assertThat(Paths.get(props.getProperty("sonar.java.test.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/releaseUnitTest/compileReleaseUnitTestJavaWithJavac/classes"));
    } else {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/release/classes"));
      assertThat(Paths.get(props.getProperty("sonar.java.test.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/releaseUnitTest/classes"));
    }

    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
    assertThat(props.getProperty("sonar.android.detected")).contains("true");
  }


  @Test
  public void testAndroidLintReport() throws Exception {
    Assume.assumeTrue(getAndroidGradleVersion().isGreaterThan("7.0.0"));
    Properties props = runGradlewSonarSimulationModeWithEnv("/multi-module-android-studio-lint", Collections.emptyMap(), "lint", "lintFullRelease");
    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(Paths.get(props.getProperty(":app.sonar.androidLint.reportPaths"))).isEqualTo(baseDir.resolve("app/build/reports/lint-results-debug.xml"));
    assertThat(Paths.get(props.getProperty(":app2.sonar.androidLint.reportPaths"))).isEqualTo(baseDir.resolve("app2/build/reports/lint-results-debug.xml"));
    assertThat(Paths.get(props.getProperty(":app3.sonar.androidLint.reportPaths"))).isEqualTo(baseDir.resolve("/custom/path/to/report.xml"));
    assertThat(Paths.get(props.getProperty(":app4.sonar.androidLint.reportPaths"))).isEqualTo(baseDir.resolve("app4/build/reports/lint-results-fullRelease.xml"));

  }


}
