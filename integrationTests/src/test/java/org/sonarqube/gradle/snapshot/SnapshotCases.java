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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
    private final String project;
    private final List<String> args;
    private String minGradle;
    private String maxGradleExclusive;
    private String minAndroidGradle;
    private String subdir;
    private boolean requiresAndroid;
    private final Set<String> excludedProperties = new LinkedHashSet<>();
    private final Set<String> excludedPaths = new LinkedHashSet<>();

    private Case(String name, String project, String... args) {
      this.name = name;
      this.project = project;
      this.args = List.of(args);
    }

    public String name() {
      return name;
    }

    public boolean shouldRun() {
      return (minGradle == null || !AbstractGradleIT.getGradleVersion().isLowerThan(minGradle)) && (maxGradleExclusive == null || AbstractGradleIT.getGradleVersion().isLowerThan(maxGradleExclusive)) && (!requiresAndroid || (AbstractGradleIT.getAndroidGradleVersion() != null && (minAndroidGradle == null || AbstractGradleIT.getAndroidGradleVersion().isGreaterThanOrEqualTo(minAndroidGradle))));
    }

    public Map<String, String> collect(AbstractGradleIT test) throws Exception {
      Properties p = test.runGradlewSonarSimulationModeWithEnv(project, subdir, Collections.emptyMap(), new DefaultRunConfiguration(), args.toArray(String[]::new));
      return SnapshotNormalizer.normalize(p, excludedProperties, excludedPaths);
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

    public Case minAndroidGradle(String value) {
      this.minAndroidGradle = value;
      return this;
    }

    public Case subdir(String subdir) {
      this.subdir = subdir;
      return this;
    }

    public Case excludeProperty(String property) {
      this.excludedProperties.add(property);
      return this;
    }

    public Case excludePath(String path) {
      this.excludedPaths.add(path);
      return this;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static List<Case> cases() {
    return List.of(
      c("gradle-9-example", "/gradle-9-example", "--console=plain", "build")
        .minGradle("9.0.0"),
      c("android-gradle9", "/android-gradle9", "--quiet", "--console=plain")
        .minGradle("9.0.0")
        .requiresAndroid()
        .minAndroidGradle("7.0.0"),
      c("java-gradle-simple", "/java-gradle-simple", "compileJava", "compileTestJava")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-custom-config", "/java-gradle-custom-config", "compileJava", "compileTestJava")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-user-properties", "/java-gradle-user-properties", "compileJava", "compileTestJava"),
      c("java-groovy-tests-gradle", "/java-groovy-tests-gradle", "build")
        .maxGradleExclusive("9.0.0"),
      c("module-inclusion", "/module-inclusion"),
      c("multi-module-source-in-root", "/multi-module-source-in-root", "compileJava", "compileTestJava")
        .maxGradleExclusive("9.0.0"),
      c("multi-module-flat", "/multi-module-flat").subdir("build")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-no-tests", "/java-gradle-no-tests")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-no-real-tests", "/java-gradle-no-real-tests", "test")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-lazy-configuration", "/java-gradle-lazy-configuration", "test")
        .maxGradleExclusive("9.0.0")
        .excludeProperty("sonar.java.test.libraries"),
      c("java-gradle-jacoco-before-7", "/java-gradle-jacoco-before-7", "processResources", "processTestResources", "test", "jacocoTestReport")
        .maxGradleExclusive("7.0.0"),
      c("java-gradle-jacoco-after-7", "/java-gradle-jacoco-after-7", "processResources", "processTestResources", "test", "jacocoTestReport")
        .gradleRange("7.0.0", "9.0.0"),
      c("kotlin-multiplatform", "/kotlin-multiplatform", "compileKotlinJvm", "compileKotlinMetadata", "compileTestKotlinJvm")
        .gradleRange("6.8.3", "9.0.0"),
      c("kotlin-multiplatform-with-submodule", "/kotlin-multiplatform-with-submodule", "compileKotlinJvm", "compileKotlinMetadata", "compileTestKotlinJvm")
        .gradleRange("6" + ".8.3", "9.0.0"),
      c("kotlin-jvm", "/kotlin-jvm", "compileKotlin", "compileTestKotlin")
        .gradleRange("6.8.3", "9.0.0"),
      c("kotlin-jvm-submodule", "/kotlin-jvm-submodule", "compileKotlin", "compileTestKotlin")
        .gradleRange("6.8.3", "9.0.0"),
      c("multi-module-with-submodules", "/multi-module-with-submodules", "compileJava", "compileTestJava", "--info")
        .excludeProperty(":skippedModule.:skippedModule:skippedSubmodule.sonar.java.test.libraries")
        .excludeProperty("sonar.java.test.libraries")
        .excludeProperty(":module.:module:submodule.sonar.binaries"),
      c("java-gradle-simple-with-github", "/java-gradle-simple-with-github", "compileJava", "compileTestJava")
        .maxGradleExclusive("9.0.0"),
      c("java-compile-only", "/java-compile-only")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-log-level", "/java-gradle-log-level")
        .maxGradleExclusive("9.0.0"),
      c("java-gradle-classpath-dependency", "/java-gradle-classpath-dependency"),
      c("java-gradle-simple-skip-jre-prov", "/java-gradle-simple-skip-jre-prov")
        .maxGradleExclusive("9.0.0"),
      c("android-gradle-default-variant", "/android-gradle-default-variant", "test", "compileDemoMinApi23DebugAndroidTestJavaWithJavac")
        .requiresAndroid()
        .minAndroidGradle("7.0.0")
        .excludePath("${PROJECT_BASE_DIR}/app3/build/intermediates/javac/debug/classes")
        .excludePath("${PROJECT_BASE_DIR}/build/intermediates/javac/demoMinApi23DebugAndroidTest/classes"),
      c("android-gradle-dynamic-feature", "/android-gradle-dynamic-feature", "test", "compileDebugAndroidTestJavaWithJavac")
        .requiresAndroid()
        .minAndroidGradle("7.0.0"),
      c("android-gradle-nondefault-variant", "/android-gradle-nondefault-variant", "test")
        .requiresAndroid()
        .minAndroidGradle("7.0.0"),
      c("multi-module-android-studio", "/multi-module-android-studio", "test", "compileDebugAndroidTestJavaWithJavac").requiresAndroid().minAndroidGradle("7.0.0"),
      c("android-testing-blueprint-with-dynamic-feature-module", "/AndroidTestingBlueprintWithDynamicFeatureModule", "assembleDebug",
        "compileFlavor1DebugUnitTestJavaWithJavac", "compileFlavor1DebugAndroidTestJavaWithJavac", "compileDebugAndroidTestJavaWithJavac", "compileDebugUnitTestJavaWithJavac",
        "compileTestJava")
        .requiresAndroid()
        .minAndroidGradle("7.0.0"),
      c("android-gradle-no-debug", "/android-gradle-no-debug", "compileReleaseUnitTestJavaWithJavac", "compileReleaseJavaWithJavac")
        .requiresAndroid()
        .minAndroidGradle("7.0.0")
        .excludePath("${PROJECT_BASE_DIR}/app/build/intermediates/app_classes/debug/classes.jar")
        .excludePath("${PROJECT_BASE_DIR}/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/R.jar")
        .excludePath("${PROJECT_BASE_DIR}/app/build/intermediates/javac/debug/classes"),
      c("multi-module-android-studio-lint", "/multi-module-android-studio-lint", "lint", "lintFullRelease")
        .requiresAndroid()
        .minAndroidGradle("7.0.0")
        .excludeProperty(":app3.sonar.java.libraries")
        .excludeProperty(":app3.sonar.libraries")
        .excludeProperty(":app4.sonar.java.libraries")
        .excludeProperty(":app4.sonar.libraries")
        .excludeProperty(":app.sonar.libraries")
        .excludeProperty(":app.sonar.java.libraries")
        .excludeProperty(":app2.sonar.libraries")
        .excludeProperty(":app2.sonar.java.libraries")
        .excludeProperty(":app.sonar.binaries")
        .excludeProperty(":app.sonar.java.binaries")
        .excludeProperty(":app2.sonar.binaries")
        .excludeProperty(":app2.sonar.java.binaries")
        .excludeProperty(":app3.sonar.binaries")
        .excludeProperty(":app3.sonar.java.binaries")
        .excludeProperty(":app4.sonar.binaries")
        .excludeProperty(":app4.sonar.java.binaries")
        .excludePath("${PROJECT_BASE_DIR}/app3/build/intermediates/javac/debug/classes")
        .excludePath("${PROJECT_BASE_DIR}/app4/build/intermediates/javac/fullRelease/classes")
        .excludePath("${PROJECT_BASE_DIR}/app/build/intermediates/app_classes/debug/classes.jar")
        .excludePath("${PROJECT_BASE_DIR}/app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/R.jar")
        .excludePath("${PROJECT_BASE_DIR}/app/build/intermediates/javac/debug/classes")
        .excludePath("${PROJECT_BASE_DIR}/app2/build/intermediates/javac/debug/classes")
    );
  }

  private static Case c(String name, String project, String... args) {
    return new Case(name, project, args);
  }
}
