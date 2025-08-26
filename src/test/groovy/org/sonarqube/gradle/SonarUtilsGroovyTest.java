/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2025 SonarSource
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarqube.gradle;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.gradle.internal.impldep.com.google.common.collect.ImmutableMap;
import org.gradle.internal.impldep.org.apache.commons.lang.SystemUtils;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.Test;
import org.sonarqube.gradle.SonarUtils.InputFileType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.sonarqube.gradle.SonarUtils.findProjectFileType;
import static org.sonarqube.gradle.SonarUtils.isCompatibleWithJavaPluginExtension;

class SonarUtilsGroovyTest {

  @Test
  void get_project_base_dir() {
    Map<String, Object> properties = ImmutableMap.of(
      "sonar.projectBaseDir", "/project/build",
      "m1.sonar.projectBaseDir", "/project/m1",
      "m1.m2.sonar.projectBaseDir", "/project/m1/m2"
    );
    assertEquals(Paths.get("/project").toAbsolutePath().toString(), SonarUtils.findProjectBaseDir(properties));

    properties = ImmutableMap.of(
      "sonar.projectBaseDir", "/build",
      "m1.sonar.projectBaseDir", "/m1",
      "m1.m2.sonar.projectBaseDir", "/m1/m2"
    );
    assertEquals(Paths.get("/").toAbsolutePath().toString(), SonarUtils.findProjectBaseDir(properties));

    properties = ImmutableMap.of(
      "sonar.projectBaseDir", "/project/",
      "m1.sonar.projectBaseDir", "/project/m1",
      "m1.m2.sonar.projectBaseDir", "/project/m1/m2"
    );
    assertEquals(Paths.get("/project").toAbsolutePath().toString(), SonarUtils.findProjectBaseDir(properties));

    properties = ImmutableMap.of(
      "sonar.projectBaseDir", "/project/build",
      "m1.sonar.projectBaseDir", "/m1",
      "m1.m2.sonar.projectBaseDir", "/m2"
    );

    assertEquals(Paths.get("/").toAbsolutePath().toString(), SonarUtils.findProjectBaseDir(properties));
  }

  @Test
  void get_project_base_dir_with_different_roots() {
    assumeTrue(SystemUtils.IS_OS_WINDOWS);

    Map<String, Object> properties = ImmutableMap.of(
      "sonar.projectBaseDir", "C:\\project\\build",
      "m1.sonar.projectBaseDir", "E:\\project\\m1"
    );
    assertEquals(Paths.get("C:\\project\\build").toAbsolutePath().toString(), SonarUtils.findProjectBaseDir(properties));
  }

  @Test
  void testJoinAsCsv() {
    List<String> values = Arrays.asList("/home/users/me/artifact-123,456.jar", "/opt/lib");
    assertThat(SonarUtils.joinAsCsv(values)).isEqualTo("\"/home/users/me/artifact-123,456.jar\",/opt/lib");

    values = Arrays.asList("/opt/lib", "/home/users/me/artifact-123,456.jar");
    assertThat(SonarUtils.joinAsCsv(values)).isEqualTo("/opt/lib,\"/home/users/me/artifact-123,456.jar\"");

    values = Arrays.asList("/opt/lib", "/home/users/me");
    assertThat(SonarUtils.joinAsCsv(values)).isEqualTo("/opt/lib,/home/users/me");
  }

  @Test
  void testSplitAsCsv() {
    String[] expectedValues = {"/home/users/me/artifact-123,456.jar", "/opt/lib", "src/main/java"};
    // Single escaped value
    assertThat(SonarUtils.splitAsCsv("\"/home/users/me/artifact-123,456.jar\",/opt/lib,src/main/java"))
      .containsOnly(expectedValues);
    assertThat(SonarUtils.splitAsCsv("/opt/lib,\"/home/users/me/artifact-123,456.jar\",src/main/java"))
      .containsOnly("/opt/lib", "/home/users/me/artifact-123,456.jar", "src/main/java");
    assertThat(SonarUtils.splitAsCsv("/opt/lib,src/main/java,\"/home/users/me/artifact-123,456.jar\""))
      .containsOnly(expectedValues);

    // Consecutive escaped values
    assertThat(SonarUtils.splitAsCsv("/opt/lib,\"src/main/java\",\"/home/users/me/artifact-123,456.jar\""))
      .containsOnly(expectedValues);
    assertThat(SonarUtils.splitAsCsv("\"/opt/lib\",\"src/main/java\",\"/home/users/me/artifact-123,456.jar\""))
      .containsOnly(expectedValues);
    assertThat(SonarUtils.splitAsCsv("\"/opt/lib\",\"/home/users/me/artifact-123,456.jar\",src/main/java"))
      .containsOnly("/opt/lib", "/home/users/me/artifact-123,456.jar", "src/main/java");

    // Interleaved escaped values
    assertThat(SonarUtils.splitAsCsv("\"/opt/lib\",src/main/java,\"/home/users/me/artifact-123,456.jar\""))
      .containsOnly(expectedValues);
  }

  @Test
  void extractReportPaths_returns_an_empty_list_when_no_Coverage_report_paths() {
    Map<String, Object> emptyProperties = Collections.emptyMap();
    assertThat(SonarUtils.extractReportPaths(emptyProperties)).isEmpty();

    Map<String, Object> unrelatedProperties = Map.of("sonar.not.a.real.parameter", "not-relevant-here");

    assertThat(SonarUtils.extractReportPaths(unrelatedProperties)).isEmpty();
  }

  @Test
  void extractReportPaths_returns_the_expected_paths_when_Coverage_report_paths_are_configured() {
    Map<String, Object> properties = Map.of(
      "sonar.coverageReportPaths", "path/to/generic-report.xml",
      "sonar.lang.reportPaths", "path/to/other-report-for-lang.bin",
      "sonar.lang.reportsPaths", "path/to/first-report-for-lang.bin, path/to/second-report-for-lang.bin",
      "sonar.lang.coverage.reportPaths", "path/to/third-report-for-lang.bin, path/to/fourth-report-for-lang.bin",
      "sonar.lang.coverage.reportPath", "path/to/fifth-report-for-lang.bin",
      "sonar.very.fancy.lang.coverage.reportPath", "path/to/sixth-report-for-lang.bin",
      "sonar.coverage.jacoco.xmlReportPaths", "path/to/seventh-report-for-lang.xml"
    );

    assertThat(SonarUtils.extractReportPaths(properties)).containsExactlyInAnyOrder(
      Path.of("path/to/generic-report.xml"),
      Path.of("path/to/other-report-for-lang.bin"),
      Path.of("path/to/first-report-for-lang.bin"),
      Path.of("path/to/second-report-for-lang.bin"),
      Path.of("path/to/fourth-report-for-lang.bin"),
      Path.of("path/to/fifth-report-for-lang.bin"),
      Path.of("path/to/third-report-for-lang.bin"),
      Path.of("path/to/sixth-report-for-lang.bin"),
      Path.of("path/to/seventh-report-for-lang.xml")
    );
  }

  @Test
  void computeReportPaths_throws_an_IllegalStateException_when_sonar_dot_projectBaseDir_is_not_defined() {
    Map<String, Object> emptyProperties = Collections.emptyMap();
    assertThatThrownBy(() -> SonarUtils.computeReportPaths(emptyProperties))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Cannot compute absolute paths for reports because \"sonar.projectBaseDir\" is not defined.");
  }

  @Test
  void computeReportPaths_returns_the_expected_absolute_paths() {
    Path projectBaseDir = Path.of("not", "a", "real", "project", "baseDir").toAbsolutePath();
    Path relativeReportFile = Path.of("my-relative-report.xml");
    Path absoluteReportFile = Path.of("my-absolute-report.xml").toAbsolutePath();
    Map<String, Object> minimalProperties = Map.of(
      "sonar.projectBaseDir", projectBaseDir
    );
    assertThat(SonarUtils.computeReportPaths(minimalProperties)).isEmpty();

    Map<String, Object> propertiesWithSingleReportPath = Map.of(
      "sonar.projectBaseDir", projectBaseDir,
      "sonar.lang.reportPaths", relativeReportFile.toString()
    );

    assertThat(SonarUtils.computeReportPaths(propertiesWithSingleReportPath))
      .containsOnly(projectBaseDir.resolve(relativeReportFile))
      .allMatch(Path::isAbsolute);

    Map<String, Object> relativeAndAbsolutePathsMix = Map.of(
      "sonar.projectBaseDir", projectBaseDir,
      "sonar.lang.reportPaths", String.format("%s,%s", relativeReportFile.toString(), absoluteReportFile.toString())
    );

    assertThat(SonarUtils.computeReportPaths(relativeAndAbsolutePathsMix))
      .containsOnly(
        projectBaseDir.resolve(relativeReportFile),
        absoluteReportFile
      )
      .allMatch(Path::isAbsolute);
  }

  @Test
  void findProjectFileType_should_return_TEST_for_path_related_to_tests() {
    String project = "my-project";
    Path projectDir = Paths.get(project);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/integrationTests/run.sh"))).isEqualTo(InputFileType.TEST);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/integrationTests/run.sh"))).isEqualTo(InputFileType.TEST);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/run-allTests.sh"))).isEqualTo(InputFileType.TEST);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/run-allTests.sh"))).isEqualTo(InputFileType.TEST);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/test/run.sh"))).isEqualTo(InputFileType.TEST);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/test/run.sh"))).isEqualTo(InputFileType.TEST);
  }

  @Test
  void findProjectFileType_should_return_MAIN_for_path_not_related_to_tests() {
    String project = "my-project";
    Path projectDir = Paths.get(project);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "detest-bad-code.md"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "generate-testimonial.sh"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "protest-for-freedom.json"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "run-testy-testiness-testimony.sh"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/build.sh"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/run-latest.sh"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/run.sh"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/runContest.sh"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "testate/write-testament-from-testator-and-testatrix.sh"))).isEqualTo(InputFileType.MAIN);
  }

  @Test
  void isCompatibleWithJavaPluginExtension_only_returns_true_for_gradle_seven_and_greater() {
    assertThat(isCompatibleWithJavaPluginExtension(GradleVersion.version("7.0.0"))).isTrue();
    assertThat(isCompatibleWithJavaPluginExtension(GradleVersion.version("7.0"))).isTrue();
    assertThat(isCompatibleWithJavaPluginExtension(GradleVersion.version("7.0-rc-1"))).isTrue();

    assertThat(isCompatibleWithJavaPluginExtension(GradleVersion.version("7.0.1"))).isTrue();
    assertThat(isCompatibleWithJavaPluginExtension(GradleVersion.version("7.1"))).isTrue();
    assertThat(isCompatibleWithJavaPluginExtension(GradleVersion.version("7.1-rc-1"))).isTrue();

    assertThat(isCompatibleWithJavaPluginExtension(GradleVersion.version("6.9.9"))).isFalse();
    assertThat(isCompatibleWithJavaPluginExtension(GradleVersion.version("6.14"))).isFalse();
    assertThat(isCompatibleWithJavaPluginExtension(GradleVersion.version("6.9999-rc-1"))).isFalse();
  }
}
