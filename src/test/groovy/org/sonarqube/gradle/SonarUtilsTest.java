/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2024 SonarSource
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

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.gradle.internal.impldep.com.google.common.collect.ImmutableMap;
import org.gradle.internal.impldep.org.apache.commons.lang.SystemUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SonarUtilsTest {
  @Test
  public void get_project_base_dir() {
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
  public void get_project_base_dir_with_different_roots() {
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
}
