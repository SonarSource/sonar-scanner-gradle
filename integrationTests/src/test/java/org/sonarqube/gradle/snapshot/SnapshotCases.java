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
package org.sonarqube.gradle.snapshot;

import com.vdurmont.semver4j.Semver;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.sonarqube.gradle.run_configuration.DefaultRunConfiguration;
import org.sonarqube.gradle.support.AbstractGradleIT;
import org.sonarqube.gradle.support.normalization.SnapshotNormalizer;

public final class SnapshotCases {
  private SnapshotCases() {
    // Utility class: contains only static methods and is not intended to be instantiated.
  }

  public static Iterable<Object[]> parameters() {
    return cases().stream().map(snapshotCase -> new Object[]{snapshotCase}).collect(Collectors.toList());
  }

  public static final class Case {
    private final String name;
    private final List<String> args;
    private String projectDir;
    private String minGradle;
    private String maxGradleExclusive;
    private String subdir;
    private boolean requiresAndroid;
    private String rewriteWithGradle;
    private String rewriteWithAndroidGradle;

    private Case(String name, String... args) {
      this.name = name;
      this.projectDir = name;
      this.args = List.of(args);
    }


    public String name() {
      return name;
    }

    public boolean shouldRun() {
      final Semver androidGradleVersion = AbstractGradleIT.getAndroidGradleVersion();
      if (requiresAndroid ^ (androidGradleVersion != null)) {
        return false;
      }
      final Semver gradleVersion = AbstractGradleIT.getGradleVersion();
      return (minGradle == null || !gradleVersion.isLowerThan(minGradle)) &&
        (maxGradleExclusive == null || gradleVersion.isLowerThan(maxGradleExclusive));
    }

    public Map<String, String> collect(AbstractGradleIT test) throws Exception {
      Properties p = test.runGradlewSonarSimulationModeWithEnv("/" + projectDir, subdir, Collections.emptyMap(), new DefaultRunConfiguration(), args.toArray(String[]::new));
      return SnapshotNormalizer.normalize(p);
    }

    public Map<String, String> collectWithVersionsOverride(AbstractGradleIT test) throws Exception {
      Properties p = test.runGradlewSonarSimulationModeWithVersions(
        "/" + projectDir,
        subdir,
        Collections.emptyMap(),
        new DefaultRunConfiguration(),
        rewriteWithGradle,
        rewriteWithAndroidGradle,
        args.toArray(String[]::new)
      );
      return SnapshotNormalizer.normalize(p);
    }

    public Case minGradle(String value) {
      this.minGradle = value;
      return this;
    }

    public Case maxGradleExclusive(String value) {
      this.maxGradleExclusive = value;
      return this;
    }

    public Case gradleRange(String min, String max) {
      return minGradle(min).maxGradleExclusive(max);
    }

    public Case requiresAndroid() {
      this.requiresAndroid = true;
      return this;
    }

    public Case subdir(String subdir) {
      this.subdir = subdir;
      return this;
    }

    public Case withProjectDir(String projectDir) {
      this.projectDir = projectDir;
      return this;
    }

    public Case rewriteWithGradle(String value) {
      this.rewriteWithGradle = value;
      return this;
    }

    public Case rewriteWithAndroidGradle(String value) {
      this.rewriteWithAndroidGradle = value;
      return this;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static List<Case> cases() {
    return List.of(
      c("gradle-9-example", "--console=plain", "build")
        .minGradle("9.0.0"),
      c("android-gradle9", "--console=plain")
        .minGradle("9.0.0")
        .requiresAndroid(),
      c("java-gradle-simple", "compileJava", "compileTestJava")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-custom-config", "compileJava", "compileTestJava")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-user-properties", "compileJava", "compileTestJava"),
      c("java-groovy-tests-gradle", "build")
        .maxGradleExclusive("9.0.0"),
      c("module-inclusion"),
      c("multi-module-source-in-root", "compileJava", "compileTestJava")
        .maxGradleExclusive("9.0.0"),
      c("multi-module-flat").subdir("build")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-no-tests")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-no-real-tests", "test")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-lazy-configuration", "test")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-jacoco-before-7", "processResources", "processTestResources", "test", "jacocoTestReport")
        .maxGradleExclusive("7.0.0")
        .rewriteWithGradle("7.5.1"),
      c("java-gradle-jacoco-after-7", "processResources", "processTestResources", "test", "jacocoTestReport")
        .gradleRange("7.0.0", "9.0.0"),
      c("kotlin-multiplatform", "compileKotlinJvm", "compileKotlinMetadata", "compileTestKotlinJvm")
        .gradleRange("6.8.3", "9.0.0"),
      c("kotlin-multiplatform-with-submodule", "compileKotlinJvm", "compileKotlinMetadata", "compileTestKotlinJvm")
        .gradleRange("6" + ".8.3", "9.0.0"),
      c("kotlin-jvm", "compileKotlin", "compileTestKotlin")
        .gradleRange("6.8.3", "9.0.0"),
      c("kotlin-jvm-submodule", "compileKotlin", "compileTestKotlin")
        .gradleRange("6.8.3", "9.0.0"),
      c("multi-module-with-submodules", "compileJava", "compileTestJava", "--info"),
      c("java-gradle-simple-with-github", "compileJava", "compileTestJava")
        .maxGradleExclusive("9.0.0"),
      c("java-compile-only")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-log-level")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-classpath-dependency"),
      c("java-gradle-simple-skip-jre-prov")
        .maxGradleExclusive("9.0.0"),
      c("android-gradle-default-variant", "test", "compileDemoMinApi23DebugAndroidTestJavaWithJavac")
        .requiresAndroid(),
      c("android-gradle-dynamic-feature", "test", "compileDebugAndroidTestJavaWithJavac")
        .requiresAndroid(),
      c("android-gradle-nondefault-variant", "test")
        // this project is compatible with gradle 9 but not fully supported yet,
        // tests libraries are not correctly resolved and thus not included in the snapshot.
        .maxGradleExclusive("9.0.0")
        .requiresAndroid(),
      c("multi-module-android-studio", "test", "compileDebugAndroidTestJavaWithJavac")
        .requiresAndroid()
        .maxGradleExclusive("9.0.0"),
      c("android-testing-blueprint-with-dynamic-feature-module", "assembleDebug",
        "compileFlavor1DebugUnitTestJavaWithJavac", "compileFlavor1DebugAndroidTestJavaWithJavac", "compileDebugAndroidTestJavaWithJavac", "compileDebugUnitTestJavaWithJavac",
        "compileTestJava")
        .withProjectDir("AndroidTestingBlueprintWithDynamicFeatureModule")
        .requiresAndroid()
        .maxGradleExclusive("9.0.0"),
      c("android-gradle-no-debug")
        // this project is compatible with gradle 9 but not fully supported yet,
        // tests libraries are not correctly resolved and thus not included in the snapshot.
        .maxGradleExclusive("9.0.0")
        .requiresAndroid(),
      c("multi-module-android-studio-lint", "lint", "lintFullRelease")
        .requiresAndroid()
        .maxGradleExclusive("9.0.0")
    );
  }

  private static Case c(String projectName, String... args) {
    return new Case(projectName, args);
  }
}
