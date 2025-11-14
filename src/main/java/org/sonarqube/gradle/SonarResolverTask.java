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
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;


@UntrackedTask(because = "task must always be recomputed, as we cannot declare input output properly")
public abstract class SonarResolverTask extends DefaultTask {
  public static final String TASK_NAME = "sonarResolver";
  public static final String TASK_DESCRIPTION = "Resolves and serializes project information and classpath for SonarQube analysis.";
  private static final Logger LOGGER = Logger.getLogger(SonarResolverTask.class.getName());

  private String projectName;
  private boolean isTopLevelProject;
  private FileCollection mainLibraries;
  private FileCollection testLibraries;
  private Provider<FileCollection> compileClasspath;
  private Provider<FileCollection> testCompileClasspath;
  private File outputDirectory;
  private Provider<Boolean> skipProject;

  SonarResolverTask() {
    super();
    // UntrackedTask should be enough, but gradle is buggy
    this.getOutputs().upToDateWhen(task -> false);
  }


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

  @Internal
  FileCollection getCompileClasspath() {
    return this.compileClasspath.get();
  }

  public void setCompileClasspath(Provider<FileCollection> compileClasspath) {
    this.compileClasspath = compileClasspath;
  }

  @Internal
  Provider<FileCollection> getTestCompileClasspath() {
    return this.testCompileClasspath;
  }

  public void setTestCompileClasspath(Provider<FileCollection> testCompileClasspath) {
    this.testCompileClasspath = testCompileClasspath;
  }

  @Internal
  FileCollection getMainLibraries() {
    return this.mainLibraries;
  }

  public void setMainLibraries(FileCollection mainLibraries) {
    this.mainLibraries = mainLibraries;
  }

  @Internal
  FileCollection getTestLibraries() {
    return this.testLibraries;
  }

  public void setTestLibraries(FileCollection testLibraries) {
    this.testLibraries = testLibraries;
  }

  public void setOutputDirectory(File outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  /**
   * @return the path where resolved properties will be written. Does not create the file itself or check that it exists.
   */
  @OutputFile
  public File getOutputFile() {
    return new File(outputDirectory, "properties");
  }

  @Input
  public Provider<Boolean> getSkipProject() {
    return skipProject;
  }

  public void setSkipProject(Provider<Boolean> skipProject) {
    this.skipProject = skipProject;
  }

  @TaskAction
  void run() throws IOException {
    if(Boolean.TRUE.equals(this.skipProject.get())){
      return;
    }

    String displayName = getProjectName();
    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.info("Resolving properties for " + displayName + ".");
    }

    var mainClasspath = this.compileClasspath.getOrNull();
    var testClasspath = testCompileClasspath.getOrNull();

    List<String> compileClasspathFilenames = SonarUtils.exists(mainClasspath == null ? Collections.emptyList() : mainClasspath)
      .stream()
      .map(File::getAbsolutePath)
      .collect(Collectors.toList());
    List<String> testCompileClasspathFilenames = SonarUtils.exists(testClasspath == null ? Collections.emptyList() : testClasspath)
      .stream()
      .map(File::getAbsolutePath)
      .collect(Collectors.toList());

    List<String> mainLibrariesFilenames = SonarUtils.exists(getMainLibraries() == null ? Collections.emptyList() : getMainLibraries())
      .stream()
      .map(File::getAbsolutePath)
      .collect(Collectors.toList());
    List<String> testLibrariesFilenames = SonarUtils.exists(getTestLibraries() == null ? Collections.emptyList() : getTestLibraries())
      .stream()
      .map(File::getAbsolutePath)
      .collect(Collectors.toList());

    ProjectProperties projectProperties = new ProjectProperties(
      getProjectName(),
      isTopLevelProject(),
      compileClasspathFilenames,
      testCompileClasspathFilenames,
      mainLibrariesFilenames,
      testLibrariesFilenames
    );

    ResolutionSerializer.write(
      getOutputFile(),
      projectProperties
    );
    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.info("Resolved properties for " + displayName + " and wrote them to " + getOutputFile() + ".");
    }
  }
}
