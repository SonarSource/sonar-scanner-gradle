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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ResolutionSerializerTest {

  private static final String TEST_PROJECT_NAME = "testProject";

  @TempDir
  Path tempDir;

  @Test
  void testWriteReadWithProperties() throws IOException {
    File file = tempDir.resolve("test.json").toFile();
    List<String> compileClasspath = Arrays.asList("path1.jar", "path2.jar");
    List<String> testCompileClasspath = Arrays.asList("test1.jar", "test2.jar");
    ProjectProperties properties = new ProjectProperties(TEST_PROJECT_NAME, true, compileClasspath, testCompileClasspath);

    ResolutionSerializer.write(file, properties);
    Optional<ProjectProperties> readProperties = ResolutionSerializer.read(file);

    assertThat(readProperties).isPresent();
    assertThat(readProperties.get().projectName).isEqualTo(TEST_PROJECT_NAME);
    assertThat(readProperties.get().isRootProject).isTrue();
    assertThat(readProperties.get().compileClasspath).isEqualTo(compileClasspath);
    assertThat(readProperties.get().testCompileClasspath).isEqualTo(testCompileClasspath);
  }


  @Test
  void testWriteReadWithoutProperties() throws IOException {
    File file = tempDir.resolve("test.json").toFile();
    List<String> emptyList = Collections.emptyList();
    ProjectProperties properties = new ProjectProperties(TEST_PROJECT_NAME, false, emptyList, emptyList);

    ResolutionSerializer.write(file, properties);
    Optional<ProjectProperties> readProperties = ResolutionSerializer.read(file);

    assertThat(readProperties).isEmpty();
  }
}
