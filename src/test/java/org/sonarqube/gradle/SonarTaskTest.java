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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SonarTaskTest {

  private final ProjectProperties projectProperties = new ProjectProperties("", true, List.of(), List.of(), List.of(), List.of());

  @Test
  void resolveSonarJavaLibraries_skips_resolution_when_no_configuration_provided() {
    Map<String, String> emptyMap = new HashMap<>();
    SonarTask.resolveSonarJavaLibraries(projectProperties, null, emptyMap);
    assertThat(emptyMap).isEmpty();
  }

  @Test
  void resolveSonarJavaLibraries_adds_sonar_java_libraries_and_sonar_binaries_to_map_for_top_level_project(@TempDir File tempDir) throws IOException {
    Map<String, String> properties = new HashMap<>();
    File emptyJar = new File(tempDir, "empty.jar");
    emptyJar.createNewFile();
    List<File> fileCollection = List.of(emptyJar);
    SonarTask.resolveSonarJavaLibraries(projectProperties, fileCollection, properties);
    assertThat(properties).containsExactlyInAnyOrderEntriesOf(
            Map.of(
                    "sonar.java.libraries", emptyJar.getAbsolutePath(),
                    "sonar.libraries", emptyJar.getAbsolutePath()
            )
    );
  }

  @Test
  void resolveSonarJavaLibraries_adds_sonar_java_libraries_and_sonar_binaries_to_map_for_subproject(@TempDir File tempDir) throws IOException {
    Map<String, String> properties = new HashMap<>();
    File emptyJar = new File(tempDir, "empty.jar");
    emptyJar.createNewFile();
    List<File> fileCollection = List.of(emptyJar);
    ProjectProperties subprojectProperties = new ProjectProperties(":subproject", false, List.of(), List.of(), List.of(), List.of());
    SonarTask.resolveSonarJavaLibraries(subprojectProperties, fileCollection, properties);
    assertThat(properties)
            .hasSize(2)
            .containsEntry(":subproject.sonar.java.libraries", emptyJar.getAbsolutePath())
            .containsEntry(":subproject.sonar.libraries", emptyJar.getAbsolutePath());
  }

  @Test
  void resolveSonarJavaLibraries_combines_with_existing_property(@TempDir File tempDir) throws IOException {
    Map<String, String> properties = new HashMap<>();
    File known = new File(tempDir, "known.jar");
    properties.put("sonar.java.libraries", known.getAbsolutePath());

    File toBeResolved = new File(tempDir, "resolved.jar");
    toBeResolved.createNewFile();
    List<File> fileCollection = List.of(toBeResolved);
    SonarTask.resolveSonarJavaLibraries(projectProperties, fileCollection, properties);
    String expectedValue = String.format("%s,%s", known.getAbsolutePath(), toBeResolved.getAbsolutePath());
    assertThat(properties).containsExactlyInAnyOrderEntriesOf(
            Map.of(
                    "sonar.java.libraries", expectedValue,
                    "sonar.libraries", expectedValue
            )
    );
  }

  @Test
  void resolveSonarJavaTestLibraries_skips_resolution_when_no_configuration_provided() {
    Map<String, String> emptyMap = new HashMap<>();
    SonarTask.resolveSonarJavaTestLibraries(projectProperties, null, emptyMap);
    assertThat(emptyMap).isEmpty();
  }

  @Test
  void resolveSonarJavaTestLibraries_adds_sonar_java_libraries_and_sonar_binaries_to_map_for_top_level_project(@TempDir File tempDir) throws IOException {
    Map<String, String> properties = new HashMap<>();
    File emptyJar = new File(tempDir, "empty.jar");
    emptyJar.createNewFile();
    List<File> fileCollection = List.of(emptyJar);
    SonarTask.resolveSonarJavaTestLibraries(projectProperties, fileCollection, properties);
    assertThat(properties)
            .hasSize(1)
            .containsEntry("sonar.java.test.libraries", emptyJar.getAbsolutePath());
  }

  @Test
  void resolveSonarJavaTestLibraries_adds_sonar_java_libraries_and_sonar_binaries_to_map_for_subproject(@TempDir File tempDir) throws IOException {
    Map<String, String> properties = new HashMap<>();
    File emptyJar = new File(tempDir, "empty.jar");
    emptyJar.createNewFile();
    List<File> fileCollection = List.of(emptyJar);
    ProjectProperties subprojectProperties = new ProjectProperties(":subproject", false, List.of(), List.of(), List.of(), List.of());
    SonarTask.resolveSonarJavaTestLibraries(subprojectProperties, fileCollection, properties);
    assertThat(properties)
            .hasSize(1)
            .containsEntry(":subproject.sonar.java.test.libraries", emptyJar.getAbsolutePath());
  }

  @Test
  void resolveSonarJavaTestLibraries_combines_with_existing_property_to_add_sonar_java_test_libraries(@TempDir File tempDir) throws IOException {
    Map<String, String> properties = new HashMap<>();

    File known = new File(tempDir, "known.jar");
    known.createNewFile();
    properties.put("sonar.java.test.libraries", known.getAbsolutePath());

    File toBeResolved = new File(tempDir, "resolved.jar");
    toBeResolved.createNewFile();
    List<File> fileCollection = List.of(toBeResolved);
    SonarTask.resolveSonarJavaTestLibraries(projectProperties, fileCollection, properties);

    String expectedValue = String.format(
            "%s,%s",
            known.getAbsolutePath(),
            toBeResolved.getAbsolutePath()
    );

    assertThat(properties).containsExactlyInAnyOrderEntriesOf(
            Map.of(
                    "sonar.java.test.libraries", expectedValue
            )
    );
  }

  @Test
  void resolveSonarJavaTestLibraries_combines_with_sonar_java_binaries_and_existing_property_to_add_sonar_java_test_libraries(@TempDir File tempDir) throws IOException {
    Map<String, String> properties = new HashMap<>();

    File buildFolder = new File(tempDir, "build-folder");
    buildFolder.mkdirs();
    properties.put("sonar.java.binaries", buildFolder.getAbsolutePath());

    File known = new File(tempDir, "known.jar");
    known.createNewFile();
    properties.put("sonar.java.test.libraries", known.getAbsolutePath());

    File toBeResolved = new File(tempDir, "resolved.jar");
    toBeResolved.createNewFile();
    List<File> fileCollection = List.of(toBeResolved);
    SonarTask.resolveSonarJavaTestLibraries(projectProperties, fileCollection, properties);

    String expectedValue = String.format(
            "%s,%s,%s",
            buildFolder.getAbsolutePath(),
            known.getAbsolutePath(),
            toBeResolved.getAbsolutePath()
    );

    assertThat(properties).containsExactlyInAnyOrderEntriesOf(
            Map.of(
                    "sonar.java.binaries", buildFolder.getAbsolutePath(),
                    "sonar.java.test.libraries", expectedValue
            )
    );
  }

  @Test
  void processResolverFile_skips_when_file_does_not_exist() {
    Map<String, String> result = new HashMap<>();
    File nonExistentFile = new File("non-existent-file.json");
    SonarTask.processResolverFile(nonExistentFile, result);
    assertThat(result).isEmpty();
  }

  @Test
  void processResolverFile_processes_when_file_exists_with_valid_data(@TempDir File tempDir) throws IOException {
    Map<String, String> result = new HashMap<>();
    File resolverFile = new File(tempDir, "resolver.json");


    Path lib1 = Files.createFile(tempDir.toPath().resolve("lib1.jar"));
    Path lib2 = Files.createFile(tempDir.toPath().resolve("lib2.jar"));
    Path testLib1 = Files.createFile(tempDir.toPath().resolve("testlib1.jar"));

    ProjectProperties props = new ProjectProperties("", true,
      List.of(lib1.toAbsolutePath().toString(), lib2.toAbsolutePath().toString()),
      List.of(testLib1.toAbsolutePath().toString()),
      List.of(),
      List.of());
    ResolutionSerializer.write(resolverFile, props);
    SonarTask.processResolverFile(resolverFile, result);
    assertThat(result)
      .containsEntry("sonar.java.libraries", lib1.toAbsolutePath() + "," + lib2.toAbsolutePath())
      .containsEntry("sonar.java.test.libraries", testLib1.toAbsolutePath().toString());
  }

  @Test
  void filterPathProperties_removes_non_existing_source_paths(@TempDir File tempDir) {
    File existingSources = new File(tempDir, "src/main/java");
    existingSources.mkdirs();
    File nonExistingSources = new File(tempDir, "src/main/kotlin");

    Map<String, String> properties = new HashMap<>();
    properties.put("sonar.sources", existingSources.getAbsolutePath() + "," + nonExistingSources.getAbsolutePath());

    SonarTask.filterPathProperties(properties, Set.of());

    assertThat(properties).containsEntry("sonar.sources", existingSources.getAbsolutePath());
  }

  @Test
  void filterPathProperties_preserves_user_defined_non_existing_paths(@TempDir File tempDir) {
    File nonExistingSources = new File(tempDir, "custom/source");

    Map<String, String> properties = new HashMap<>();
    properties.put("sonar.sources", nonExistingSources.getAbsolutePath());

    SonarTask.filterPathProperties(properties, Set.of("sonar.sources"));

    assertThat(properties).containsEntry("sonar.sources", nonExistingSources.getAbsolutePath());
  }

  @Test
  void filterPathProperties_preserves_wildcards_in_properties() {
    String reportPaths = "**/*.xml,reports/*.xml,${projectDir}/*.xml";

    Map<String, String> properties = new HashMap<>();
    properties.put("sonar.coverage.jacoco.xmlReportPaths", reportPaths);

    SonarTask.filterPathProperties(properties, Set.of());

    assertThat(properties).containsEntry("sonar.coverage.jacoco.xmlReportPaths", reportPaths);
  }

  @Test
  void filterPathProperties_filters_github_from_sources_even_if_user_defined(@TempDir File tempDir) {
    File nonExistingGithubFolder = new File(tempDir, ".github");
    File normalSources = new File(tempDir, "src");
    normalSources.mkdir();

    Map<String, String> properties = new HashMap<>();
    properties.put("sonar.sources", normalSources.getAbsolutePath() + "," + nonExistingGithubFolder.getAbsolutePath());

    SonarTask.filterPathProperties(properties, Set.of("sonar.sources"));

    assertThat(properties).containsEntry("sonar.sources", normalSources.getAbsolutePath());
  }

  @Test
  void filterPathProperties_filters_settings_gradle_kts_even_if_user_defined(@TempDir File tempDir) {
    File nonExistingSettingsFile = new File(tempDir, "settings.gradle.kts");
    File normalSources = new File(tempDir, "src");
    normalSources.mkdir();

    Map<String, String> properties = new HashMap<>();
    properties.put("sonar.sources", normalSources.getAbsolutePath() + "," + nonExistingSettingsFile.getAbsolutePath());

    SonarTask.filterPathProperties(properties, Set.of("sonar.sources"));

    assertThat(properties).containsEntry("sonar.sources", normalSources.getAbsolutePath());
  }

  @Test
  void filterPathProperties_preserves_jacoco_xml_report_paths_when_files_exist(@TempDir File tempDir) throws IOException {
    File reportFile = new File(tempDir, "jacoco.xml");
    reportFile.createNewFile();

    Map<String, String> properties = new HashMap<>();
    properties.put("sonar.coverage.jacoco.xmlReportPaths", reportFile.getAbsolutePath());

    SonarTask.filterPathProperties(properties, Set.of());

    assertThat(properties).containsEntry("sonar.coverage.jacoco.xmlReportPaths", reportFile.getAbsolutePath());
  }

  @Test
  void filterPathProperties_removes_jacoco_xml_report_paths_when_files_dont_exist(@TempDir File tempDir) {
    File nonExistingReport = new File(tempDir, "jacoco.xml");

    Map<String, String> properties = new HashMap<>();
    properties.put("sonar.coverage.jacoco.xmlReportPaths", nonExistingReport.getAbsolutePath());

    SonarTask.filterPathProperties(properties, Set.of());

    assertThat(properties).doesNotContainKey("sonar.coverage.jacoco.xmlReportPaths");
  }

  @Test
  void filterPathProperties_handles_submodule_properties(@TempDir File tempDir) {
    File existingSources = new File(tempDir, "module/src");
    existingSources.mkdirs();
    File nonExistingSources = new File(tempDir, "module/other");

    Map<String, String> properties = new HashMap<>();
    properties.put(":module.sonar.sources", existingSources.getAbsolutePath() + "," + nonExistingSources.getAbsolutePath());

    SonarTask.filterPathProperties(properties, Set.of());

    assertThat(properties).containsEntry(":module.sonar.sources", existingSources.getAbsolutePath());
  }

  @Test
  void filterPathProperties_keeps_user_defined_submodule_properties(@TempDir File tempDir) {
    File nonExistingSources = new File(tempDir, "module/other");

    Map<String, String> properties = new HashMap<>();
    properties.put(":module.sonar.sources", nonExistingSources.getAbsolutePath());

    SonarTask.filterPathProperties(properties, Set.of(":module.sonar.sources"));

    assertThat(properties).containsEntry(":module.sonar.sources", nonExistingSources.getAbsolutePath());
  }

  @Test
  void filterPathProperties_filters_multiple_property_types(@TempDir File tempDir) {
    File existingSources = new File(tempDir, "src/main/java");
    existingSources.mkdirs();
    File nonExistingSources = new File(tempDir, "src/main/kotlin");

    File existingBinaries = new File(tempDir, "build/classes");
    existingBinaries.mkdirs();
    File nonExistingBinaries = new File(tempDir, "build/other");

    Map<String, String> properties = new HashMap<>();
    properties.put("sonar.sources", existingSources.getAbsolutePath() + "," + nonExistingSources.getAbsolutePath());
    properties.put("sonar.java.binaries", existingBinaries.getAbsolutePath() + "," + nonExistingBinaries.getAbsolutePath());

    SonarTask.filterPathProperties(properties, Set.of());

    assertThat(properties)
      .containsEntry("sonar.sources", existingSources.getAbsolutePath())
      .containsEntry("sonar.java.binaries", existingBinaries.getAbsolutePath());
  }

  @Test
  void filterPathProperties_handles_mixed_user_defined_and_automatic_properties(@TempDir File tempDir) {
    File existingAutoSources = new File(tempDir, "src/main/java");
    existingAutoSources.mkdirs();
    File nonExistingAutoSources = new File(tempDir, "src/main/groovy");
    File nonExistingUserDefinedTestSources = new File(tempDir, "custom/source");

    Map<String, String> properties = new HashMap<>();
    properties.put("sonar.sources", existingAutoSources.getAbsolutePath() + "," + nonExistingAutoSources.getAbsolutePath());
    properties.put("sonar.tests", nonExistingUserDefinedTestSources.getAbsolutePath());

    SonarTask.filterPathProperties(properties, Set.of("sonar.tests"));

    assertThat(properties)
      .containsEntry("sonar.sources", existingAutoSources.getAbsolutePath())
      .containsEntry("sonar.tests", nonExistingUserDefinedTestSources.getAbsolutePath());
  }
}
