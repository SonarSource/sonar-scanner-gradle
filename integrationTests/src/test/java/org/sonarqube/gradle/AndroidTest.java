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

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sonarqube.gradle.support.AbstractGradleIT;
import org.sonarqube.gradle.run_configuration.DefaultRunConfiguration;
import org.sonarqube.gradle.support.normalization.SnapshotNormalizer;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

public class AndroidTest extends AbstractGradleIT {

  @BeforeClass
  public static void beforeAll() {
    assumeNotNull(System.getenv("JAVA_HOME"));
    assumeNotNull(getAndroidGradleVersion());
    assumeTrue(getAndroidGradleVersion().isGreaterThan("7.0.0"));
  }

  @Test
  public void testSonarTaskHasNoDependencies() throws Exception {
    RunResult result = runGradlewWithEnvQuietly("/AndroidTestingBlueprintWithDynamicFeatureModule", emptyMap(), new DefaultRunConfiguration(), "sonar", "--dry-run",
      "--max-workers=1");

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

  @Test
  public void gradle9AndroidParallelExample() throws Exception {
    ignoreThisTestIfGradleVersionIsLessThan("9.0.0");
    Map<String, String> env = Collections.emptyMap();
    Properties props = runGradlewSonarSimulationModeWithEnv("/android-gradle9", env, new DefaultRunConfiguration(), "--quiet", "--console=plain");
    Map<String, String> comparableProps = SnapshotNormalizer.normalize(props, Set.of(), Set.of());

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

    // Verify :javalib module properties (plain Java library, not Android)
    assertThat(comparableProps)
      .doesNotContainKey(":javalib.sonar.android.detected")
      .containsEntry(":javalib.sonar.java.source", "1.8")
      .containsEntry(":javalib.sonar.java.target", "1.8")
      .containsEntry(":javalib.sonar.moduleKey", "org.sonarqube:example-android-gradle-dynamic-module:javalib")
      .containsEntry(":javalib.sonar.projectName", "javalib");

    // Verify javalib does NOT contain Android-specific libraries
    assertThat(comparableProps.get(":javalib.sonar.java.libraries"))
      .doesNotContain("android.jar");
    assertThat(comparableProps.get(":javalib.sonar.java.test.libraries"))
      .isNotEmpty()
      .contains("junit-4.13.2.jar", "hamcrest-core-1.3.jar")
      .doesNotContain("android.jar", "jetified-junit-", "jetified-hamcrest-core-");
  }
}
