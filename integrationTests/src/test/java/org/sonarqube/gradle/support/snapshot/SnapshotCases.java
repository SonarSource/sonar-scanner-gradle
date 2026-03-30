/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support.snapshot;

import java.util.List;
import java.util.stream.Collectors;

public final class SnapshotCases {
  private SnapshotCases() {
  }

  public static Iterable<Object[]> parameters() {
    return cases().stream().map(snapshotCase -> new Object[] { snapshotCase }).collect(Collectors.toList());
  }

  private static List<SnapshotCase> cases() {
    return List.of(
      c("gradle-9-example", "/gradle-9-example", null, "--console=plain", "build").minGradle("9.0.0").build(),
      c("android-gradle9", "/android-gradle9", null, "--quiet", "--console=plain").minGradle("9.0.0").requiresAndroid().minAndroidGradle("7.0.0").build(),
      c("java-gradle-simple", "/java-gradle-simple", null, "compileJava", "compileTestJava").maxGradleExclusive("9.0.0").build(),
      c("java-gradle-custom-config", "/java-gradle-custom-config", null, "compileJava", "compileTestJava").maxGradleExclusive("9.0.0").build(),
      c("java-gradle-user-properties", "/java-gradle-user-properties", null, "compileJava", "compileTestJava").build(),
      c("java-groovy-tests-gradle", "/java-groovy-tests-gradle", null, "build").maxGradleExclusive("9.0.0").build(),
      c("module-inclusion", "/module-inclusion", null).build(),
      c("multi-module-source-in-root", "/multi-module-source-in-root", null, "compileJava", "compileTestJava").maxGradleExclusive("9.0.0").build(),
      c("multi-module-flat", "/multi-module-flat", "build").maxGradleExclusive("9.0.0").build(),
      c("java-gradle-no-tests", "/java-gradle-no-tests", null).maxGradleExclusive("9.0.0").build(),
      c("java-gradle-no-real-tests", "/java-gradle-no-real-tests", null, "test").maxGradleExclusive("9.0.0").build(),
      c("java-gradle-lazy-configuration", "/java-gradle-lazy-configuration", null, "test").maxGradleExclusive("9.0.0").ignoreProperty("sonar.java.test.libraries").build(),
      c("java-gradle-jacoco-before-7", "/java-gradle-jacoco-before-7", null, "processResources", "processTestResources", "test", "jacocoTestReport").maxGradleExclusive("7.0.0").build(),
      c("java-gradle-jacoco-after-7", "/java-gradle-jacoco-after-7", null, "processResources", "processTestResources", "test", "jacocoTestReport").gradleRange("7.0.0", "9.0.0").build(),
      c("kotlin-multiplatform", "/kotlin-multiplatform", null, "compileKotlinJvm", "compileKotlinMetadata", "compileTestKotlinJvm").gradleRange("6.8.3", "9.0.0").build(),
      c("kotlin-multiplatform-with-submodule", "/kotlin-multiplatform-with-submodule", null, "compileKotlinJvm", "compileKotlinMetadata", "compileTestKotlinJvm").gradleRange("6.8.3", "9.0.0").build(),
      c("kotlin-jvm", "/kotlin-jvm", null, "compileKotlin", "compileTestKotlin").gradleRange("6.8.3", "9.0.0").build(),
      c("kotlin-jvm-submodule", "/kotlin-jvm-submodule", null, "compileKotlin", "compileTestKotlin").gradleRange("6.8.3", "9.0.0").build(),
      c("multi-module-with-submodules", "/multi-module-with-submodules", null, "compileJava", "compileTestJava", "--info").ignoreProperty(":skippedModule.:skippedModule:skippedSubmodule.sonar.java.test.libraries").build(),
      c("java-gradle-simple-with-github", "/java-gradle-simple-with-github", null, "compileJava", "compileTestJava").maxGradleExclusive("9.0.0").build(),
      c("java-compile-only", "/java-compile-only", null).maxGradleExclusive("9.0.0").build(),
      c("java-gradle-log-level", "/java-gradle-log-level", null).maxGradleExclusive("9.0.0").build(),
      c("java-gradle-classpath-dependency", "/java-gradle-classpath-dependency", null).build(),
      c("java-gradle-simple-skip-jre-prov", "/java-gradle-simple-skip-jre-prov", null).maxGradleExclusive("9.0.0").build(),
      c("android-gradle-default-variant", "/android-gradle-default-variant", null, "test", "compileDemoMinApi23DebugAndroidTestJavaWithJavac").requiresAndroid().minAndroidGradle("7.0.0").build(),
      c("android-gradle-dynamic-feature", "/android-gradle-dynamic-feature", null, "test", "compileDebugAndroidTestJavaWithJavac").requiresAndroid().minAndroidGradle("7.0.0").build(),
      c("android-gradle-nondefault-variant", "/android-gradle-nondefault-variant", null, "test").requiresAndroid().minAndroidGradle("7.0.0").build(),
      c("multi-module-android-studio", "/multi-module-android-studio", null, "test", "compileDebugAndroidTestJavaWithJavac").requiresAndroid().minAndroidGradle("7.0.0").build(),
      c("android-testing-blueprint-with-dynamic-feature-module", "/AndroidTestingBlueprintWithDynamicFeatureModule", null, "assembleDebug", "compileFlavor1DebugUnitTestJavaWithJavac", "compileFlavor1DebugAndroidTestJavaWithJavac", "compileDebugAndroidTestJavaWithJavac", "compileDebugUnitTestJavaWithJavac", "compileTestJava").requiresAndroid().minAndroidGradle("7.0.0").build(),
      c("android-gradle-no-debug", "/android-gradle-no-debug", null, "compileReleaseUnitTestJavaWithJavac", "compileReleaseJavaWithJavac").requiresAndroid().minAndroidGradle("7.0.0").build(),
      c("multi-module-android-studio-lint", "/multi-module-android-studio-lint", null, "lint", "lintFullRelease").requiresAndroid().minAndroidGradle("7.0.0")
        .ignoreProperty(":app.sonar.binaries").ignoreProperty(":app.sonar.java.binaries").ignoreProperty(":app2.sonar.binaries").ignoreProperty(":app2.sonar.java.binaries")
        .ignoreProperty(":app3.sonar.binaries").ignoreProperty(":app3.sonar.java.binaries").ignoreProperty(":app4.sonar.binaries").ignoreProperty(":app4.sonar.java.binaries").build());
  }

  private static SnapshotCase.Builder c(String name, String project, String subdir, String... args) {
    return new SnapshotCase.Builder(name, project, subdir, args);
  }
}
