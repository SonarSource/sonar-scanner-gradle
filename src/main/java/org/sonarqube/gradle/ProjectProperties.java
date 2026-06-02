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

  /**
   * The Gradle project name (e.g., ":subproject" for subprojects, "" for root)
   */
  public final String projectName;

  /**
   * Whether this project is the root project of the analysis
   */
  public final Boolean isRootProject;

  /**
   * Resolved absolute paths of compile classpath dependencies
   */
  public final List<String> compileClasspath;

  /**
   * Resolved absolute paths of test compile classpath dependencies
   */
  public final List<String> testCompileClasspath;

  /**
   * Filtered main libraries (subset of compileClasspath) for SonarQube analysis
   */
  public final List<String> mainLibraries;

  /**
   * Filtered test libraries (subset of testCompileClasspath) for SonarQube analysis
   */
  public final List<String> testLibraries;

  /**
   * The resolved source directories for an Android project.
   */
  public final List<String> androidSources;

  /**
   * The resolved test directories for an Android project.
   */
  public final List<String> androidTests;

  private ProjectProperties(Builder builder) {
    this.projectName = builder.projectName;
    this.isRootProject = builder.isRootProject;
    this.compileClasspath = builder.compileClasspath;
    this.testCompileClasspath = builder.testCompileClasspath;
    this.mainLibraries = builder.mainLibraries;
    this.testLibraries = builder.testLibraries;
    this.androidSources = builder.androidSources;
    this.androidTests = builder.androidTests;
  }

  public static class Builder {
    private final String projectName;
    private final Boolean isRootProject;
    private List<String> compileClasspath = List.of();
    private List<String> testCompileClasspath = List.of();
    private List<String> mainLibraries = List.of();
    private List<String> testLibraries = List.of();
    private List<String> androidSources = List.of();
    private List<String> androidTests = List.of();

    public Builder(String projectName, Boolean isRootProject) {
      this.projectName = projectName;
      this.isRootProject = isRootProject;
    }

    public Builder compileClasspath(List<String> compileClasspath) {
      this.compileClasspath = compileClasspath;
      return this;
    }

    public Builder testCompileClasspath(List<String> testCompileClasspath) {
      this.testCompileClasspath = testCompileClasspath;
      return this;
    }

    public Builder mainLibraries(List<String> mainLibraries) {
      this.mainLibraries = mainLibraries;
      return this;
    }

    public Builder testLibraries(List<String> testLibraries) {
      this.testLibraries = testLibraries;
      return this;
    }

    public Builder androidSources(List<String> androidSources) {
      this.androidSources = androidSources;
      return this;
    }

    public Builder androidTests(List<String> androidTests) {
      this.androidTests = androidTests;
      return this;
    }

    public ProjectProperties build() {
      return new ProjectProperties(this);
    }

  }

}
