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

import com.vdurmont.semver4j.Semver;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.BeforeClass;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

public class AndroidTest extends AbstractGradleIT {
  private static final Semver VERSION_8_3_0 = new Semver("8.3.0", Semver.SemverType.STRICT);

  @BeforeClass
  public static void beforeAll() {
    assumeNotNull(System.getenv("JAVA_HOME"));
    assumeNotNull(getAndroidGradleVersion());
    assumeTrue(getAndroidGradleVersion().isGreaterThan("7.0.0"));
  }

  @Test
  public void testUsingDefaultVariant() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/android-gradle-default-variant", emptyMap(), "test", "compileDemoMinApi23DebugAndroidTestJavaWithJavac");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));

    if (getAndroidGradleVersion().isGreaterThanOrEqualTo(VERSION_8_3_0)) {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/demoMinApi23Debug/compileDemoMinApi23DebugJavaWithJavac/classes"));
    } else {
      assertThat(Paths.get(props.getProperty("sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("build/intermediates/javac/demoMinApi23Debug/classes"));
    }

    // For Android Gradle Plugin version 8 and greater, the debugAndroidTest artifacts are no longer present in the same folder
    if (getAndroidGradleVersion().isGreaterThanOrEqualTo(VERSION_8_3_0)) {
      assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("build/intermediates/javac/demoMinApi23DebugUnitTest/compileDemoMinApi23DebugUnitTestJavaWithJavac/classes")
        );
    } else if (getAndroidGradleVersion().isGreaterThanOrEqualTo("8.0.0")) {
      assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("build/intermediates/javac/demoMinApi23DebugUnitTest/classes")
        );
    } else {
      assertThat(stream(props.getProperty("sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("build/intermediates/javac/demoMinApi23DebugUnitTest/classes"),
          baseDir.resolve("build/intermediates/javac/demoMinApi23DebugAndroidTest/classes")
        );
    }

    assertThat(props.getProperty("sonar.java.libraries")).contains("android.jar", "joda-time-2.7.jar");
    assertThat(props.getProperty("sonar.java.libraries")).doesNotContain("junit-4.12.jar");
    assertThat(props.getProperty("sonar.java.test.libraries")).contains("junit-4.12.jar");
    assertThat(props.getProperty("sonar.junit.reportPaths")).contains(baseDir.resolve("build/test-results/testDemoMinApi23DebugUnitTest").toString());
    assertThat(props.getProperty("sonar.android.detected")).contains("true");

    assertThat(props.getProperty("sonar.android.minsdkversion.min")).contains("21");
    assertThat(props.getProperty("sonar.android.minsdkversion.max")).contains("25");
  }

  @Test
  public void testAndroidDynamicFeature() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/android-gradle-dynamic-feature", emptyMap(), "test", "compileDebugAndroidTestJavaWithJavac");

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
    if (getAndroidGradleVersion().isGreaterThanOrEqualTo(VERSION_8_3_0)) {
      assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"));
      assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
          baseDir.resolve("app/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes")
        );
    } else {
      assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("app/build/intermediates/javac/debug/classes"));
      assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("app/build/intermediates/javac/debugUnitTest/classes"),
          baseDir.resolve("app/build/intermediates/javac/debugAndroidTest/classes")
        );
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
    if (getAndroidGradleVersion().isGreaterThanOrEqualTo(VERSION_8_3_0)) {
      assertThat(Paths.get(props.getProperty(":mydynamicfeature.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("mydynamicfeature/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"));
      assertThat(stream(props.getProperty(":mydynamicfeature.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("mydynamicfeature/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
          baseDir.resolve("mydynamicfeature/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes")
        );
    } else {
      assertThat(Paths.get(props.getProperty(":mydynamicfeature.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("mydynamicfeature/build/intermediates/javac/debug/classes"));
      assertThat(stream(props.getProperty(":mydynamicfeature.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("mydynamicfeature/build/intermediates/javac/debugUnitTest/classes"),
          baseDir.resolve("mydynamicfeature/build/intermediates/javac/debugAndroidTest/classes")
        );
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
    if (getAndroidGradleVersion().isGreaterThanOrEqualTo(VERSION_8_3_0)) {
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
    assertThat(props.getProperty("sonar.junit.reportPaths")).contains(baseDir.resolve("build/test-results/testFullMinApi23ReleaseUnitTest").toString());
  }

  @Test
  public void testMultiModule() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/multi-module-android-studio", emptyMap(), "test", "compileDebugAndroidTestJavaWithJavac");

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

    if (getAndroidGradleVersion().isGreaterThanOrEqualTo(VERSION_8_3_0)) {
      assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"));
      assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
          baseDir.resolve("app/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes")
        );
    } else {
      assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("app/build/intermediates/javac/debug/classes"));

      assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("app/build/intermediates/javac/debugUnitTest/classes"),
          baseDir.resolve("app/build/intermediates/javac/debugAndroidTest/classes")
        );
    }

    assertThat(props.getProperty(":app.sonar.java.libraries")).contains("android.jar");
    assertThat(props.getProperty(":app.sonar.java.libraries")).doesNotContain("hamcrest-core-1.3.jar");
    assertThat(props.getProperty(":app.sonar.java.test.libraries")).contains("hamcrest-core-1.3.jar");
    assertThat(props.getProperty(":app.sonar.java.source")).isEqualTo("1.8");
    assertThat(props.getProperty(":app.sonar.java.target")).isEqualTo("1.8");
    assertThat(props.getProperty(":app.sonar.android.detected")).contains("true");
  }

  @Test
  public void testingBlueprintWithDynamicFeatureModule_default_flavor() throws Exception {

    // First flavor that is picked up seems to be the flavor1

    Properties props = runGradlewSonarSimulationModeWithEnv(
      "/AndroidTestingBlueprintWithDynamicFeatureModule",
      emptyMap(),
      "assembleDebug",
      "compileFlavor1DebugUnitTestJavaWithJavac",
      "compileFlavor1DebugAndroidTestJavaWithJavac",
      "compileDebugAndroidTestJavaWithJavac",
      "compileDebugUnitTestJavaWithJavac",
      "compileTestJava"
    );

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
    if (getAndroidGradleVersion().isGreaterThanOrEqualTo(VERSION_8_3_0)) {
      assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("app/build/intermediates/javac/flavor1Debug/compileFlavor1DebugJavaWithJavac/classes"));
      assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("app/build/intermediates/javac/flavor1DebugUnitTest/compileFlavor1DebugUnitTestJavaWithJavac/classes"),
          baseDir.resolve("app/build/intermediates/javac/flavor1DebugAndroidTest/compileFlavor1DebugAndroidTestJavaWithJavac/classes"));
      assertThat(Paths.get(props.getProperty(":module-android-library.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("module-android-library/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"));
    } else {
      assertThat(Paths.get(props.getProperty(":app.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("app/build/intermediates/javac/flavor1Debug/classes"));
      assertThat(stream(props.getProperty(":app.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("app/build/intermediates/javac/flavor1DebugUnitTest/classes"),
          baseDir.resolve("app/build/intermediates/javac/flavor1DebugAndroidTest/classes"));
      assertThat(Paths.get(props.getProperty(":module-android-library.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("module-android-library/build/intermediates/javac/debug/classes"));
    }
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
    if (getAndroidGradleVersion().isGreaterThanOrEqualTo(VERSION_8_3_0)) {
      assertThat(stream(props.getProperty(":module-android-library.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("module-android-library/build/intermediates/javac/debugAndroidTest/compileDebugAndroidTestJavaWithJavac/classes")
        );
    } else {
      assertThat(stream(props.getProperty(":module-android-library.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("module-android-library/build/intermediates/javac/debugAndroidTest/classes")
        );
    }
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
    if (getAndroidGradleVersion().isGreaterThanOrEqualTo(VERSION_8_3_0)) {
      assertThat(Paths.get(props.getProperty(":module_android_feature.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("module_android_feature/build/intermediates/javac/flavor1Debug/compileFlavor1DebugJavaWithJavac/classes"));
      assertThat(stream(props.getProperty(":module_android_feature.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("module_android_feature/build/intermediates/javac/flavor1DebugAndroidTest/compileFlavor1DebugAndroidTestJavaWithJavac/classes"));
    } else {
      assertThat(Paths.get(props.getProperty(":module_android_feature.sonar.java.binaries")))
        .isEqualTo(baseDir.resolve("module_android_feature/build/intermediates/javac/flavor1Debug/classes"));
      assertThat(stream(props.getProperty(":module_android_feature.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("module_android_feature/build/intermediates/javac/flavor1DebugAndroidTest/classes"));
    }
    assertThat(props.getProperty(":module_android_feature.sonar.java.source")).isEqualTo("1.8");
    assertThat(props.getProperty(":module_android_feature.sonar.java.target")).isEqualTo("1.8");

    // test only module
    assertThat(props.getProperty(":module-flavor1-androidTest-only.sonar.sources")).isEmpty();
    assertThat(stream(props.getProperty(":module-flavor1-androidTest-only.sonar.tests").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("module-flavor1-androidTest-only/src/main/java"),
        baseDir.resolve("module-flavor1-androidTest-only/src/main/AndroidManifest.xml"));
    assertThat(props).doesNotContainKey(":module-flavor1-androidTest-only.sonar.java.binaries");
    if (getAndroidGradleVersion().isGreaterThanOrEqualTo(VERSION_8_3_0)) {
      assertThat(stream(props.getProperty(":module-flavor1-androidTest-only.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("module-flavor1-androidTest-only/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"));
    } else {
      assertThat(stream(props.getProperty(":module-flavor1-androidTest-only.sonar.java.test.binaries").split(",")).map(Paths::get))
        .containsOnly(
          baseDir.resolve("module-flavor1-androidTest-only/build/intermediates/javac/debug/classes"));
    }
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
  public void testSonarTaskHasNoDependencies() throws Exception {
    // First flavor that is picked up seems to be the flavor1

    RunResult result = runGradlewWithEnvQuietly("/AndroidTestingBlueprintWithDynamicFeatureModule", null, emptyMap(), "sonar", "--dry-run", "--max-workers=1");

    Stream<String> logs = stream(result.getLog().split("\\r?\\n")).sorted();

    assertThat(logs)
      .contains(":sonar SKIPPED")
      .doesNotContainAnyElementsOf(Arrays.asList(
        ":app:compileFlavor1DebugAndroidTestJavaWithJavac SKIPPED",
        ":app:compileFlavor1DebugUnitTestJavaWithJavac SKIPPED",
        ":module-android-library:compileDebugAndroidTestJavaWithJavac SKIPPED",
        ":module-android-library:compileDebugUnitTestJavaWithJavac SKIPPED",
        ":module-flavor1-androidTest-only:compileDebugJavaWithJavac SKIPPED",
        ":module-plain-java:compileTestJava SKIPPED",
        ":module_android_feature:compileFlavor1DebugJavaWithJavac SKIPPED")
      );
  }

  // SONARGRADL-22
  @Test
  public void noDebugVariant() throws Exception {
    Properties props = runGradlewSonarSimulationModeWithEnv("/android-gradle-no-debug", emptyMap(), "compileReleaseUnitTestJavaWithJavac", "compileReleaseJavaWithJavac");

    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(props).contains(entry("sonar.projectKey", "org.sonarqube:example-android-gradle"));
    assertThat(stream(props.getProperty("sonar.sources").split(",")).map(Paths::get))
      .containsOnly(
        baseDir.resolve("src/main/java"),
        baseDir.resolve("src/main/res"),
        baseDir.resolve("src/main/AndroidManifest.xml"));
    assertThat(Paths.get(props.getProperty("sonar.tests"))).isEqualTo(baseDir.resolve("src/test/java"));

    if (getAndroidGradleVersion().isGreaterThanOrEqualTo(VERSION_8_3_0)) {
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
    Properties props = runGradlewSonarSimulationModeWithEnv("/multi-module-android-studio-lint", Collections.emptyMap(), "lint", "lintFullRelease");
    Path baseDir = Paths.get(props.getProperty("sonar.projectBaseDir"));

    assertThat(Paths.get(props.getProperty(":app.sonar.androidLint.reportPaths"))).isEqualTo(baseDir.resolve("app/build/reports/lint-results-debug.xml"));
    assertThat(Paths.get(props.getProperty(":app2.sonar.androidLint.reportPaths"))).isEqualTo(baseDir.resolve("app2/build/reports/lint-results-debug.xml"));
    assertThat(props.getProperty(":app3.sonar.androidLint.reportPaths")).isEqualTo("/custom/path/to/report.xml");
    assertThat(Paths.get(props.getProperty(":app4.sonar.androidLint.reportPaths"))).isEqualTo(baseDir.resolve("app4/build/reports/lint-results-fullRelease.xml"));
  }

  @Test
  public void gradle9AndroidParallelExample() throws Exception {
    ignoreThisTestIfGradleVersionIsLessThan("9.0.0");
    Map<String, String> env = Collections.emptyMap();
    Properties props = runGradlewSonarSimulationModeWithEnv("/android-gradle9", env, "--quiet", "--console=plain");
    Map<String, String> comparableProps = extractComparableProperties(props);

    // Verify key project structure properties
    assertThat(comparableProps)
      .containsEntry("sonar.projectKey", "org.sonarqube:example-android-gradle-dynamic-module")
      .containsEntry("sonar.projectName", "Simple Android Gradle Project With Dynamic Module")
      .containsEntry("sonar.projectVersion", "unspecified")
      .containsEntry("sonar.modules", ":app,:mydynamicfeature")
      .containsEntry("sonar.host.url", "https://sonarcloud.io");

    // Verify :app module properties
    assertThat(comparableProps)
      .containsEntry(":app.sonar.android.detected", "true")
      .containsEntry(":app.sonar.java.source", "1.8")
      .containsEntry(":app.sonar.java.target", "1.8")
      .containsEntry(":app.sonar.moduleKey", "org.sonarqube:example-android-gradle-dynamic-module:app")
      .containsEntry(":app.sonar.projectName", "app");

    // Verify :mydynamicfeature module properties
    assertThat(comparableProps)
      .containsEntry(":mydynamicfeature.sonar.android.detected", "true")
      .containsEntry(":mydynamicfeature.sonar.java.source", "1.8")
      .containsEntry(":mydynamicfeature.sonar.java.target", "1.8")
      .containsEntry(":mydynamicfeature.sonar.moduleKey", "org.sonarqube:example-android-gradle-dynamic-module:mydynamicfeature")
      .containsEntry(":mydynamicfeature.sonar.projectName", "mydynamicfeature");

    // Verify Android libraries are resolved and present
    assertThat(comparableProps.get(":app.sonar.java.libraries"))
      .isNotEmpty()
      .contains("android.jar")
      .contains("core-lambda-stubs.jar");
    assertThat(comparableProps.get(":app.sonar.java.test.libraries"))
      .isNotEmpty()
      .contains("android.jar", "core-lambda-stubs.jar", "R.jar", "jetified-junit-4.13.2.jar", "jetified-hamcrest-core-1.3.jar", "jetified-junit-1.1.2-api.jar");

    assertThat(comparableProps.get(":mydynamicfeature.sonar.java.libraries"))
      .isNotEmpty()
      .contains("android.jar");
    assertThat(comparableProps.get(":mydynamicfeature.sonar.java.test.libraries"))
      .isNotEmpty()
      .contains("android.jar", "core-lambda-stubs.jar", "R.jar", "jetified-junit-4.13.2.jar", "jetified-hamcrest-core-1.3.jar", "jetified-junit-1.1.2-api.jar");
  }
}
