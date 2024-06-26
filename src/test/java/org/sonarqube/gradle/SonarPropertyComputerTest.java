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

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.sonarqube.gradle.SonarPropertyComputer.InputFileType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarqube.gradle.SonarPropertyComputer.findProjectFileType;

class SonarPropertyComputerTest {

  @Test
  void testFindProjectFileType() {
    String project = "my-project";
    Path projectDir = Paths.get(project);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/build.sh"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/run.sh"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/test/run.sh"))).isEqualTo(InputFileType.TEST);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/integrationTests/run.sh"))).isEqualTo(InputFileType.TEST);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/run-allTests.sh"))).isEqualTo(InputFileType.TEST);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "script/runContest.sh"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "protest-for-freedom.json"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "detest-bad-code.md"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "testate/write-testament-from-testator-and-testatrix.sh"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "generate-testimonial.sh"))).isEqualTo(InputFileType.MAIN);
    assertThat(findProjectFileType(projectDir, Paths.get(project, "run-testy-testiness-testimony.sh"))).isEqualTo(InputFileType.MAIN);
  }

}
