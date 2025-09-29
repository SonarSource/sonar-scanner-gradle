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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;

public abstract class SonarResolverTask extends DefaultTask {
  public static final String TASK_NAME = "sonarResolver";
  private static final Logger LOGGER = Logger.getLogger(SonarResolverTask.class.getName());

  private String projectName;
  private boolean isTopLevelProject;
  private FileCollection compileClasspath;
  private FileCollection testCompileClasspath;
  private File outputDirectory;

  @Input
  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String name) {
    this.projectName = name;
  }

  @Input
  public boolean isTopLevelProject() {
    return isTopLevelProject;
  }

  public void setTopLevelProject(boolean topLevelProject) {
    this.isTopLevelProject = topLevelProject;
  }

  //TODO remove
//  @InputFiles
//  @Optional
//  FileCollection getCompileClasspath() {
//    return this.compileClasspath;
//  }
//
//  public void setCompileClasspath(FileCollection compileClasspath) {
//    this.compileClasspath = compileClasspath;
//  }

  //TODO remove
//  @InputFiles
//  @Optional
//  FileCollection getTestCompileClasspath() {
//    return this.testCompileClasspath;
//  }
//
//  public void setTestCompileClasspath(FileCollection testCompileClasspath) {
//    this.testCompileClasspath = testCompileClasspath;
//  }

  public void setOutputDirectory(File outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  @OutputFile
  public File getOutputFile() throws IOException {
    String filename = "properties";
    File output = new File(outputDirectory, filename);
    if (output.isFile() && output.exists()) {
      return output;
    }
    if (!output.createNewFile()) {
      throw new IOException("Could not create output file: " + output.getAbsolutePath());
    }
    return output;
  }

  @TaskAction
  void run() throws IOException {
    String displayName = getProjectName();
    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.info("Resolving properties for " + displayName + ".");
    }

    FileCollection mainClassPath = getMainClassPath(getProject());
    FileCollection testClassPath = getTestClassPath(getProject());

    List<String> compileClasspathFilenames = SonarUtils.exists(mainClassPath == null ? Collections.emptyList() : mainClassPath)
      .stream()
      .map(File::getAbsolutePath)
      .collect(Collectors.toList());
    List<String> testCompileClasspathFilenames = SonarUtils.exists(testClassPath == null ? Collections.emptyList() : testClassPath)
      .stream()
      .map(File::getAbsolutePath)
      .collect(Collectors.toList());
    ProjectProperties projectProperties = new ProjectProperties(getProjectName(), isTopLevelProject(), compileClasspathFilenames, testCompileClasspathFilenames);

    ResolutionSerializer.write(
      getOutputFile(),
      projectProperties
    );
    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.info("Resolved properties for " + displayName + " and wrote them to " + getOutputFile() + ".");
    }
  }

  @Nullable
  private static FileCollection getMainClassPath(Project project) {
    return getClassPath(project, "main");
  }

  @Nullable
  private static FileCollection getTestClassPath(Project project) {
    return getClassPath(project, "test");
  }

  @Nullable
  private static FileCollection getClassPath(Project project, String sourceSetName) {
    SourceSetContainer sourceSets = SonarUtils.getSourceSets(project);
    if (sourceSets == null) {
      return null;
    }
    SourceSet sourceSet = sourceSets.findByName(sourceSetName);
    if (sourceSet == null) {
      return null;
    }
    FileCollection compileClasspath = sourceSet.getCompileClasspath();
    if (compileClasspath == null) {
      return null;
    }
    return compileClasspath;
  }

}
