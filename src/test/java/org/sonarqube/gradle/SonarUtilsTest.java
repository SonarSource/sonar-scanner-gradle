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
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class SonarUtilsTest {

  @Test
  void test_get_class_path_from_sourcesets(){
    SourceSetContainer sourceSetsMissingMain = Mockito.mock(SourceSetContainer.class);
    when(sourceSetsMissingMain.findByName("main")).thenReturn(null);
    assertThat(SonarUtils.getClassPathFromSourceSets("main", sourceSetsMissingMain)).isNull();

    SourceSetContainer sourceSets = Mockito.mock(SourceSetContainer.class);
    SourceSet sourceSet = Mockito.mock(SourceSet.class);
    when(sourceSets.findByName("main")).thenReturn(sourceSet);
    when(sourceSet.getCompileClasspath()).thenReturn(null);
    assertThat(SonarUtils.getClassPathFromSourceSets("main", sourceSets)).isNull();
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

}
