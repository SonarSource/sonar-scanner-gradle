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

import java.util.List;

public class ProjectProperties {

  public final String projectName;
  public final Boolean isRootProject;
  public final List<String> compileClasspath;
  public final List<String> testCompileClasspath;
  public final List<String> mainLibraries;
  public final List<String> testLibraries;

  public ProjectProperties(String projectName, Boolean isRootProject, List<String> compileClasspath, List<String> testCompileClasspath,
                           List<String> mainLibraries, List<String> testLibraries) {
    this.projectName = projectName;
    this.isRootProject = isRootProject;
    this.compileClasspath = compileClasspath;
    this.testCompileClasspath = testCompileClasspath;
    this.mainLibraries = mainLibraries;
    this.testLibraries = testLibraries;
  }

}
