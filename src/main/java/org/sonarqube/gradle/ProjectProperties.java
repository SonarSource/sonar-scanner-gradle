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

/**
 * An immutable data transfer object that holds resolved dependency information for a Gradle project.
 * <p>
 * This class is used during the Gradle <strong>execution phase</strong> to transfer resolved classpath
 * information between tasks, specifically from {@link SonarResolverTask} to {@link SonarTask}.
 * It is an <strong>internal implementation detail</strong> and is not exposed to users.
 *
 * <p>
 * <strong>Purpose:</strong>
 * <ul>
 * <li>Carries resolved compile and test classpaths after dependency resolution</li>
 * <li>Supports Gradle configuration cache by being serializable/deserializable</li>
 * <li>Ensures immutability for safe sharing between tasks</li>
 * </ul>
 *
 * <p>
 * <strong>Note:</strong> This class is for internal task-to-task communication, not for user configuration.
 * For user-facing property configuration, see {@link SonarProperties}.
 */
public class ProjectProperties {

  /** The Gradle project name (e.g., ":subproject" for subprojects, "" for root) */
  public final String projectName;

  /** Whether this project is the root project of the analysis */
  public final Boolean isRootProject;

  /** Resolved absolute paths of compile classpath dependencies */
  public final List<String> compileClasspath;

  /** Resolved absolute paths of test compile classpath dependencies */
  public final List<String> testCompileClasspath;

  /** Filtered main libraries (subset of compileClasspath) for SonarQube analysis */
  public final List<String> mainLibraries;

  /** Filtered test libraries (subset of testCompileClasspath) for SonarQube analysis */
  public final List<String> testLibraries;

  /**
   * Creates a new immutable ProjectProperties instance.
   *
   * @param projectName the Gradle project name
   * @param isRootProject whether this is the root project
   * @param compileClasspath resolved compile classpath as absolute paths
   * @param testCompileClasspath resolved test compile classpath as absolute paths
   * @param mainLibraries filtered main libraries for analysis
   * @param testLibraries filtered test libraries for analysis
   */
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
