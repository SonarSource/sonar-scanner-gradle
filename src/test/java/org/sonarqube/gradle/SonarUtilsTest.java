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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SonarUtilsTest {

  private static final class SkipHierarchy {
    // The plugin is applied to "analysisRoot", not to the Gradle root, so "gradleRoot" has no Sonar extension
    final Project gradleRoot = ProjectBuilder.builder().withName("gradleRoot").build();
    final Project analysisRoot = ProjectBuilder.builder().withName("analysisRoot").withParent(gradleRoot).build();
    final Project module = ProjectBuilder.builder().withName("module").withParent(analysisRoot).build();
    final Project submodule = ProjectBuilder.builder().withName("submodule").withParent(module).build();

    SkipHierarchy() {
      analysisRoot.getPluginManager().apply(SonarQubePlugin.class);
    }

    static void skip(Project project, String extensionName) {
      ((SonarExtension) project.getExtensions().getByName(extensionName)).setSkipProject(true);
    }
  }

  @Test
  void is_skipped_is_false_when_nothing_is_skipped() {
    SkipHierarchy hierarchy = new SkipHierarchy();

    assertThat(SonarUtils.isSkipped(hierarchy.submodule, hierarchy.analysisRoot)).isFalse();
    assertThat(SonarUtils.notSkipped(hierarchy.analysisRoot, hierarchy.analysisRoot)).isTrue();
  }

  @Test
  void is_skipped_when_the_project_itself_is_skipped() {
    SkipHierarchy hierarchy = new SkipHierarchy();
    SkipHierarchy.skip(hierarchy.submodule, SonarExtension.SONAR_EXTENSION_NAME);

    assertThat(SonarUtils.isSkipped(hierarchy.submodule, hierarchy.analysisRoot)).isTrue();
    assertThat(SonarUtils.isSkipped(hierarchy.module, hierarchy.analysisRoot)).isFalse();
  }

  @Test
  void is_skipped_when_an_ancestor_is_skipped_with_the_deprecated_extension() {
    SkipHierarchy hierarchy = new SkipHierarchy();
    SkipHierarchy.skip(hierarchy.module, SonarExtension.SONAR_DEPRECATED_EXTENSION_NAME);

    assertThat(SonarUtils.isSkipped(hierarchy.submodule, hierarchy.analysisRoot)).isTrue();
    assertThat(SonarUtils.notSkipped(hierarchy.submodule, hierarchy.analysisRoot)).isFalse();
  }

  @Test
  void is_skipped_when_the_analysis_root_is_skipped() {
    SkipHierarchy hierarchy = new SkipHierarchy();
    SkipHierarchy.skip(hierarchy.analysisRoot, SonarExtension.SONAR_EXTENSION_NAME);

    assertThat(SonarUtils.isSkipped(hierarchy.submodule, hierarchy.analysisRoot)).isTrue();
  }

  @Test
  void is_skipped_does_not_look_above_the_analysis_root_when_ancestors_have_no_sonar_extension() {
    SkipHierarchy hierarchy = new SkipHierarchy();

    assertThat(hierarchy.gradleRoot.getExtensions().findByName(SonarExtension.SONAR_EXTENSION_NAME)).isNull();
    assertThat(SonarUtils.isSkipped(hierarchy.submodule, hierarchy.analysisRoot)).isFalse();
  }

  @Test
  void is_skipped_ignores_skipped_ancestors_above_the_analysis_root() {
    Project enclosing = ProjectBuilder.builder().withName("enclosing").build();
    enclosing.getPluginManager().apply(SonarQubePlugin.class);
    // Created after the plugin was applied to "enclosing", so it is not part of the "enclosing" analysis
    Project scan = ProjectBuilder.builder().withName("scan").withParent(enclosing).build();
    scan.getPluginManager().apply(SonarQubePlugin.class);
    SkipHierarchy.skip(enclosing, SonarExtension.SONAR_EXTENSION_NAME);

    assertThat(SonarUtils.isSkipped(scan, scan)).isFalse();
    assertThat(SonarUtils.isSkipped(scan, enclosing)).isTrue();
  }

  @Test
  void test_construct_prefixed_project_name() {
    assertThat(SonarUtils.constructPrefixedProjectName(":module:submodule")).isEqualTo(":module.:module:submodule");
    assertThat(SonarUtils.constructPrefixedProjectName(":module:submodule:mimimi")).isEqualTo(":module.:module:submodule.:module:submodule:mimimi");
  }

  @Test
  void append_props_to_without_previous_value() {
    Map<String, Object> properties = new HashMap<>();
    SonarUtils.appendProps(properties, "my-key", List.of("a", "b", "c", "d"));
    assertThat((Collection<Object>) properties.get("my-key")).containsExactly("a", "b", "c", "d");
  }

  @Test
  void append_props_to_a_previous_file_value() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("my-key", new File("a"));
    SonarUtils.appendProps(properties, "my-key", List.of("b", "c", "d"));
    assertThat((Collection<Object>) properties.get("my-key")).containsExactly(new File("a"), "b", "c", "d");
  }

  @Test
  void append_props_to_a_previous_collection_value() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("my-key", List.of("a", "b"));
    SonarUtils.appendProps(properties, "my-key", List.of("c", "d"));
    assertThat((Collection<Object>) properties.get("my-key")).containsExactly("a", "b", "c", "d");
  }

  @Test
  void test_join_csv_without_duplicates(){
    String a = "a,b,c";
    String b = "b,c,d";
    assertThat(SonarUtils.joinCsvStringsWithoutDuplicates(a, b)).isEqualTo("a,b,c,d");
    String c = "";
    assertThat(SonarUtils.joinCsvStringsWithoutDuplicates(a, c)).isEqualTo("a,b,c");
    String d = "g,";
    assertThat(SonarUtils.joinCsvStringsWithoutDuplicates(a, d)).isEqualTo("a,b,c,g");
    String e = ",";
    assertThat(SonarUtils.joinCsvStringsWithoutDuplicates(a, e)).isEqualTo("a,b,c");
  }

  @Test
  void test_getRuntimeJars_throws_IllegalStateException_when_java_home_is_invalid() {
    String originalJavaHome = System.getProperty("java.home");
    try {
      // Set java.home to a path with null character, which will cause an exception in getCanonicalFile()
      System.setProperty("java.home", "/invalid\u0000path");
      assertThatThrownBy(SonarUtils::getRuntimeJars)
        .isInstanceOf(IllegalStateException.class);
    } finally {
      System.setProperty("java.home", originalJavaHome);
    }
  }

}
