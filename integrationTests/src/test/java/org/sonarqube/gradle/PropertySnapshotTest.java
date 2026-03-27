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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Ignore;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.sonarqube.gradle.run_configuration.DefaultRunConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class PropertySnapshotTest extends AbstractGradleIT {

  private static final Path SNAPSHOT_FILE_ROOT = resolveRepositoryRoot()
    .resolve(Paths.get("integrationTests", "src", "test", "resources", "PropertySnapshotTest"));
  private static final Gson GSON = new Gson();
  private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() {
  }.getType();

  private static final List<SnapshotCase> SNAPSHOT_CASES = List.of(
    SnapshotCase.of("gradle-9-example", "/gradle-9-example", null, "--console=plain", "build").minGradle("9.0.0"),
    SnapshotCase.of("android-gradle9", "/android-gradle9", null, "--quiet", "--console=plain").minGradle("9.0.0").requiresAndroid(),
    SnapshotCase.of("java-gradle-simple", "/java-gradle-simple", null, "compileJava", "compileTestJava").maxGradleExclusive("9.0.0"),
    SnapshotCase.of("java-gradle-custom-config", "/java-gradle-custom-config", null, "compileJava", "compileTestJava").maxGradleExclusive("9.0.0"),
    SnapshotCase.of("java-gradle-user-properties", "/java-gradle-user-properties", null, "compileJava", "compileTestJava"),
    SnapshotCase.of("java-groovy-tests-gradle", "/java-groovy-tests-gradle", null, "build").maxGradleExclusive("9.0.0"),
    SnapshotCase.of("module-inclusion", "/module-inclusion", null),
    SnapshotCase.of("multi-module-source-in-root", "/multi-module-source-in-root", null, "compileJava", "compileTestJava").maxGradleExclusive("9.0.0"),
    SnapshotCase.of("multi-module-flat", "/multi-module-flat", "build").maxGradleExclusive("9.0.0"),
    SnapshotCase.of("java-gradle-no-tests", "/java-gradle-no-tests", null).maxGradleExclusive("9.0.0"),
    SnapshotCase.of("java-gradle-no-real-tests", "/java-gradle-no-real-tests", null, "test").maxGradleExclusive("9.0.0"),
    SnapshotCase.of("java-gradle-lazy-configuration", "/java-gradle-lazy-configuration", null, "test").maxGradleExclusive("9.0.0"),
    SnapshotCase.of("java-gradle-jacoco-before-7", "/java-gradle-jacoco-before-7", null, "processResources", "processTestResources", "test", "jacocoTestReport").maxGradleExclusive("7.0.0"),
    SnapshotCase.of("java-gradle-jacoco-after-7", "/java-gradle-jacoco-after-7", null, "processResources", "processTestResources", "test", "jacocoTestReport").gradleRange("7.0.0", "9.0.0"),
    SnapshotCase.of("kotlin-multiplatform", "/kotlin-multiplatform", null, "compileKotlinJvm", "compileKotlinMetadata", "compileTestKotlinJvm").gradleRange("6.8.3", "9.0.0"),
    SnapshotCase.of("kotlin-multiplatform-with-submodule", "/kotlin-multiplatform-with-submodule", null, "compileKotlinJvm", "compileKotlinMetadata", "compileTestKotlinJvm").gradleRange("6.8.3", "9.0.0"),
    SnapshotCase.of("kotlin-jvm", "/kotlin-jvm", null, "compileKotlin", "compileTestKotlin").gradleRange("6.8.3", "9.0.0"),
    SnapshotCase.of("kotlin-jvm-submodule", "/kotlin-jvm-submodule", null, "compileKotlin", "compileTestKotlin").gradleRange("6.8.3", "9.0.0"),
    SnapshotCase.of("multi-module-with-submodules", "/multi-module-with-submodules", null, "compileJava", "compileTestJava", "--info")
      .ignoreProperty(":skippedModule.:skippedModule:skippedSubmodule.sonar.java.test.libraries"),
    SnapshotCase.of("java-gradle-simple-with-github", "/java-gradle-simple-with-github", null, "compileJava", "compileTestJava").maxGradleExclusive("9.0.0"),
    SnapshotCase.of("java-compile-only", "/java-compile-only", null).maxGradleExclusive("9.0.0"),
    SnapshotCase.of("java-gradle-log-level", "/java-gradle-log-level", null).maxGradleExclusive("9.0.0"),
    SnapshotCase.of("java-gradle-classpath-dependency", "/java-gradle-classpath-dependency", null),
    SnapshotCase.of("java-gradle-simple-skip-jre-prov", "/java-gradle-simple-skip-jre-prov", null).maxGradleExclusive("9.0.0"),
    SnapshotCase.of("android-gradle-default-variant", "/android-gradle-default-variant", null, "test", "compileDemoMinApi23DebugAndroidTestJavaWithJavac").requiresAndroid(),
    SnapshotCase.of("android-gradle-dynamic-feature", "/android-gradle-dynamic-feature", null, "test", "compileDebugAndroidTestJavaWithJavac").requiresAndroid(),
    SnapshotCase.of("android-gradle-nondefault-variant", "/android-gradle-nondefault-variant", null, "test").requiresAndroid(),
    SnapshotCase.of("multi-module-android-studio", "/multi-module-android-studio", null, "test", "compileDebugAndroidTestJavaWithJavac").requiresAndroid(),
    SnapshotCase.of("android-testing-blueprint-with-dynamic-feature-module", "/AndroidTestingBlueprintWithDynamicFeatureModule", null,
      "assembleDebug",
      "compileFlavor1DebugUnitTestJavaWithJavac",
      "compileFlavor1DebugAndroidTestJavaWithJavac",
      "compileDebugAndroidTestJavaWithJavac",
      "compileDebugUnitTestJavaWithJavac",
      "compileTestJava").requiresAndroid(),
    SnapshotCase.of("android-gradle-no-debug", "/android-gradle-no-debug", null, "compileReleaseUnitTestJavaWithJavac", "compileReleaseJavaWithJavac").requiresAndroid(),
    SnapshotCase.of("multi-module-android-studio-lint", "/multi-module-android-studio-lint", null, "lint", "lintFullRelease").requiresAndroid()
  );

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> parameters() {
    return SNAPSHOT_CASES.stream()
      .map(snapshotCase -> new Object[]{snapshotCase})
      .collect(Collectors.toList());
  }

  private final SnapshotCase snapshotCase;

  public PropertySnapshotTest(SnapshotCase snapshotCase) {
    this.snapshotCase = snapshotCase;
  }

  @Test
  public void verifyExistingPropertySnapshots() throws Exception {
    Assume.assumeTrue("Snapshot case should run: " + snapshotCase.name, snapshotCase.shouldRun());

    Map<String, String> actual = snapshotCase.collect(this);
    assertThat(snapshotCase.expectedFile())
      .as("expected snapshot file for %s", snapshotCase.name)
      .exists();
    Map<String, String> expected = snapshotCase.sanitize(loadExpectedMap(snapshotCase.expectedFile()));
    assertThat(actual)
      .as(snapshotCase.name)
      .containsAllEntriesOf(expected);
  }

  @Ignore("Run locally to regenerate all integration test property snapshots.")
  @Test
  public void rewritePropertySnapshot() throws Exception {
    writeExpectedMap(snapshotCase.expectedFile(), snapshotCase.collect(this));
  }

  public static void main(String[] args) throws Exception {
    for (SnapshotCase snapshotCase : SNAPSHOT_CASES) {
      PropertySnapshotTest test = new PropertySnapshotTest(snapshotCase);
      test.temp.create();
      try {
        test.writeExpectedMap(snapshotCase.expectedFile(), snapshotCase.collect(test));
      } finally {
        test.temp.delete();
      }
    }
  }

  private static final class SnapshotCase {
    private final String name;
    private final String project;
    private final String exeRelativePath;
    private final String[] args;
    private final Set<String> ignoredProperties = new LinkedHashSet<>();
    private String minGradle;
    private String maxGradleExclusive;
    private boolean requiresAndroid;

    private SnapshotCase(String name, String project, String exeRelativePath, String[] args) {
      this.name = name;
      this.project = project;
      this.exeRelativePath = exeRelativePath;
      this.args = args;
    }

    static SnapshotCase of(String name, String project, String exeRelativePath, String... args) {
      return new SnapshotCase(name, project, exeRelativePath, args);
    }

    SnapshotCase minGradle(String minGradle) {
      this.minGradle = minGradle;
      return this;
    }

    SnapshotCase maxGradleExclusive(String maxGradleExclusive) {
      this.maxGradleExclusive = maxGradleExclusive;
      return this;
    }

    SnapshotCase gradleRange(String minGradle, String maxGradleExclusive) {
      this.minGradle = minGradle;
      this.maxGradleExclusive = maxGradleExclusive;
      return this;
    }

    SnapshotCase requiresAndroid() {
      this.requiresAndroid = true;
      return this;
    }

    SnapshotCase ignoreProperty(String propertyKey) {
      this.ignoredProperties.add(propertyKey);
      return this;
    }

    boolean shouldRun() {
      if (minGradle != null && getGradleVersion().isLowerThan(minGradle)) {
        return false;
      }
      if (maxGradleExclusive != null && !getGradleVersion().isLowerThan(maxGradleExclusive)) {
        return false;
      }
      return !requiresAndroid || getAndroidGradleVersion() != null;
    }

    Path expectedFile() {
      return SNAPSHOT_FILE_ROOT.resolve(name + ".json");
    }

    Map<String, String> collect(PropertySnapshotTest test) throws Exception {
      Properties props = test.runGradlewSonarSimulationModeWithEnv(project, exeRelativePath, Collections.emptyMap(), new DefaultRunConfiguration(), args);
      return sanitize(new LinkedHashMap<>(extractComparableProperties(props)));
    }

    Map<String, String> sanitize(Map<String, String> properties) {
      Map<String, String> sanitized = new LinkedHashMap<>(properties);
      ignoredProperties.forEach(sanitized::remove);
      return sanitized;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static Map<String, String> loadExpectedMap(Path path) throws IOException {
    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      return GSON.fromJson(reader, STRING_MAP_TYPE);
    }
  }

  private static Path resolveRepositoryRoot() {
    Path workingDirectory = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    if (Files.exists(workingDirectory.resolve("integrationTests").resolve("pom.xml"))) {
      return workingDirectory;
    }
    if (Files.exists(workingDirectory.resolve("pom.xml"))) {
      Path parent = workingDirectory.getParent();
      if (parent != null && Files.exists(parent.resolve("integrationTests").resolve("pom.xml"))) {
        return parent;
      }
    }
    return workingDirectory;
  }
}
