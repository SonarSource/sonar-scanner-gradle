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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SonarTaskTest {

  @Test
  void resolveSonarJavaLibraries_skips_resolution_when_no_configuration_provided() {
    Map<String, String> emptyMap = new HashMap<>();
    SonarTask.resolveSonarJavaLibraries(new ProjectProperties("", true, List.of(), List.of()), null, emptyMap);
    assertThat(emptyMap).isEmpty();
  }

  @Test
  void resolveSonarJavaLibraries_adds_sonar_java_libraries_and_sonar_binaries_to_map_for_top_level_project(@TempDir File tempDir) throws IOException {
    Map<String, String> properties = new HashMap<>();
    File emptyJar = new File(tempDir, "empty.jar");
    emptyJar.createNewFile();
    List<File> fileCollection = List.of(emptyJar);
    SonarTask.resolveSonarJavaLibraries(new ProjectProperties("", true, List.of(), List.of()), fileCollection, properties);
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
    SonarTask.resolveSonarJavaLibraries(new ProjectProperties("subproject", false, List.of(), List.of()), fileCollection, properties);
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
    SonarTask.resolveSonarJavaLibraries(new ProjectProperties("", true, List.of(), List.of()), fileCollection, properties);
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
    SonarTask.resolveSonarJavaTestLibraries(new ProjectProperties("", true, List.of(), List.of()), null, emptyMap);
    assertThat(emptyMap).isEmpty();
  }

  @Test
  void resolveSonarJavaTestLibraries_adds_sonar_java_libraries_and_sonar_binaries_to_map_for_top_level_project(@TempDir File tempDir) throws IOException {
    Map<String, String> properties = new HashMap<>();
    File emptyJar = new File(tempDir, "empty.jar");
    emptyJar.createNewFile();
    List<File> fileCollection = List.of(emptyJar);
    SonarTask.resolveSonarJavaTestLibraries(new ProjectProperties("", true, List.of(), List.of()), fileCollection, properties);
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
    SonarTask.resolveSonarJavaTestLibraries(new ProjectProperties("subproject", false, List.of(), List.of()), fileCollection, properties);
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
    SonarTask.resolveSonarJavaTestLibraries(new ProjectProperties("", true, List.of(), List.of()), fileCollection, properties);

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
    SonarTask.resolveSonarJavaTestLibraries(new ProjectProperties("", true, List.of(), List.of()), fileCollection, properties);

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
  void resolveSonarJavaTestLibraries_does_not_leave_a_dangling_comma_when_there_are_no_libraries_to_add() {
    Map<String, String> properties = new HashMap<>();
    String binaries = "should-not-be-followed-by-a-comma";
    properties.put("sonar.java.binaries", binaries);
    SonarTask.resolveSonarJavaTestLibraries(new ProjectProperties("", true, Collections.emptyList(), Collections.emptyList()), Collections.emptyList(), properties);
    assertThat(properties).containsEntry("sonar.java.test.libraries", "should-not-be-followed-by-a-comma");
  }
}