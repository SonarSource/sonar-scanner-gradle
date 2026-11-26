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

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IsolatedProjectsMigrationHintTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "Could not find method sonar() for arguments [build_abc$_run_closure1@1] on project ':app' of type org.gradle.api.Project.",
    "Could not find method sonarqube() for arguments [closure] on project ':app'",
    "Script compilation error:\n  Line 3: sonar { properties { } }\n           ^ Unresolved reference: sonar",
    "e: file:///work/app/build.gradle.kts:3:1: Unresolved reference 'sonar'."
  })
  void diagnoses_a_missing_sonar_extension(String gradleMessage) {
    Optional<String> hint = IsolatedProjectsMigrationHint.diagnose(wrapped(gradleMessage), "8.0.0.1");

    assertThat(hint).hasValueSatisfying(message -> assertThat(message)
      .startsWith("A subproject build script uses the 'sonar { }' block, but the 'org.sonarqube' plugin is not applied to that subproject.")
      .contains("id(\"org.sonarqube\") version \"8.0.0.1\"")
      .contains("id 'org.sonarqube' version '8.0.0.1'"));
  }

  @Test
  void diagnoses_a_sonar_block_written_for_another_project() {
    Optional<String> hint = IsolatedProjectsMigrationHint.diagnose(
      wrapped("Project ':' cannot access 'sonar' extension on another project ':lib:core'"), "8.0.0.1");

    assertThat(hint).hasValueSatisfying(message -> assertThat(message)
      .contains("Move the 'sonar { }' configuration of project ':lib:core' from the root build script")
      .contains("'project(\":lib:core\") { }'"));
  }

  @Test
  void diagnoses_a_sonar_block_written_in_subprojects() {
    Optional<String> hint = IsolatedProjectsMigrationHint.diagnose(
      wrapped("Project ':' cannot access 'sonar' extension on subprojects"), "8.0.0.1");

    assertThat(hint).hasValueSatisfying(message -> assertThat(message)
      .contains("written in 'subprojects { }' or 'allprojects { }' of the root build script")
      .contains("into the build script of each subproject"));
  }

  @Test
  void finds_the_diagnosis_in_a_suppressed_failure() {
    RuntimeException failure = new RuntimeException("Build failed with 2 failures");
    failure.addSuppressed(new IllegalStateException("unrelated"));
    failure.addSuppressed(wrapped("Could not find method sonar() for arguments [closure]"));

    assertThat(IsolatedProjectsMigrationHint.diagnose(failure, "8.0.0.1")).isPresent();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Could not find method sonarLint() for arguments [closure]",
    "Unresolved reference: sonarConventions",
    "Project ':' cannot access 'android' extension on another project ':app'",
    "unrelated failure"
  })
  void ignores_unrelated_failures(String gradleMessage) {
    assertThat(IsolatedProjectsMigrationHint.diagnose(wrapped(gradleMessage), "8.0.0.1")).isEmpty();
  }

  @Test
  void ignores_failures_without_message() {
    assertThat(IsolatedProjectsMigrationHint.diagnose(new RuntimeException(new IllegalStateException()), "8.0.0.1")).isEmpty();
  }

  @Test
  void falls_back_to_a_version_placeholder() {
    assertThat(IsolatedProjectsMigrationGuide.missingExtensionMessage("")).contains("id 'org.sonarqube' version '<version>'");
  }

  @Test
  void lists_at_most_ten_missing_projects() {
    var paths = java.util.stream.IntStream.rangeClosed(10, 21).mapToObj(i -> ":p" + i).collect(java.util.stream.Collectors.toList());

    assertThat(IsolatedProjectsMigrationGuide.missingProjectsMessage(paths, "8.0.0.1"))
      .contains("not applied to 12 project(s) of the build: :p10, :p11, :p12, :p13, :p14, :p15, :p16, :p17, :p18, :p19 and 2 more.");
  }

  private static RuntimeException wrapped(String gradleMessage) {
    return new RuntimeException("A problem occurred evaluating project ':app'.", new IllegalStateException(gradleMessage));
  }
}
