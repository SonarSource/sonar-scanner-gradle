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
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.GradleVersion;

import static org.sonarqube.gradle.SonarUtils.getMainClassPath;
import static org.sonarqube.gradle.SonarUtils.getTestClassPath;

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

  @InputFiles
  @Optional
  FileCollection getCompileClasspath() {
    return this.compileClasspath;
  }

  public void setCompileClasspath(FileCollection compileClasspath) {
    this.compileClasspath = compileClasspath;
  }

  @InputFiles
  @Optional
  FileCollection getTestCompileClasspath() {
    return this.testCompileClasspath;
  }

  public void setTestCompileClasspath(FileCollection testCompileClasspath) {
    this.testCompileClasspath = testCompileClasspath;
  }

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

    if(compileClasspath == null){
      compileClasspath = getClasspathFromSourceSets("main");
    }
    if(testCompileClasspath == null){
      testCompileClasspath = getClasspathFromSourceSets("test");
    }

    List<String> compileClasspathFilenames = SonarUtils.exists(compileClasspath == null ? Collections.emptyList() : compileClasspath)
      .stream()
      .map(File::getAbsolutePath)
      .collect(Collectors.toList());
    List<String> testCompileClasspathFilenames = SonarUtils.exists(testCompileClasspath == null ? Collections.emptyList() : testCompileClasspath)
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

  // Suppress warning about using deprecated API for Gradle < 7 compatibility
  @SuppressWarnings("java:S1874")
  private FileCollection getClasspathFromSourceSets(String sourceSetName) {
    if(isAtLeastGradle7()){
      JavaPluginExtension javaExt = getExtensions().findByType(JavaPluginExtension.class);
      if(javaExt != null){
        return SonarUtils.getClassPathFromSourceSets(sourceSetName, javaExt.getSourceSets());
      }
    }else{
      JavaPluginConvention javaPlugin = getConvention().findPlugin(JavaPluginConvention.class);
      if(javaPlugin != null){
        return SonarUtils.getClassPathFromSourceSets(sourceSetName, javaPlugin.getSourceSets());
      }
    }
    return null;
  }

  private static boolean isAtLeastGradle7() {
    return GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("7.0")) >= 0;
  }

}
