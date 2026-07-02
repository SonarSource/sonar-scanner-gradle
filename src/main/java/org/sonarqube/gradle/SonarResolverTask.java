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
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;


public abstract class SonarResolverTask extends DefaultTask {
  public static final String TASK_NAME = "sonarResolver";
  public static final String TASK_DESCRIPTION = "Resolves and serializes project information and classpath for SonarQube analysis.";
  private static final Logger LOGGER = Logger.getLogger(SonarResolverTask.class.getName());

  private Provider<FileCollection> compileClasspath;
  private Provider<FileCollection> testCompileClasspath;
  private File outputDirectory;

  @Inject
  public SonarResolverTask() {
    super();
  }


  @Input
  public abstract Property<String> getProjectName();

  @Input
  public abstract Property<Boolean> getTopLevelProject();

  public void setCompileClasspath(Provider<FileCollection> compileClasspath) {
    this.compileClasspath = compileClasspath;
  }

  public void setTestCompileClasspath(Provider<FileCollection> testCompileClasspath) {
    this.testCompileClasspath = testCompileClasspath;
  }

  @Classpath
  public abstract ConfigurableFileCollection getMainLibraries();

  @Classpath
  public abstract ConfigurableFileCollection getTestLibraries();

  @PathSensitive(PathSensitivity.RELATIVE)
  @org.gradle.api.tasks.InputFiles
  public abstract ConfigurableFileCollection getAndroidSources();

  @PathSensitive(PathSensitivity.RELATIVE)
  @org.gradle.api.tasks.InputFiles
  public abstract ConfigurableFileCollection getAndroidTests();

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
  public abstract Property<Boolean> getSkipProject();

  /**
   * Returns the absolute paths of the files in the given FileCollection.
   */
  private static List<String> getAbsolutePaths(FileCollection fileCollection) {
    return SonarUtils.exists(fileCollection)
      .stream()
      .map(File::getAbsolutePath)
      .collect(Collectors.toList());
  }

  private static List<String> getAbsolutePaths(Provider<FileCollection> filesProvider) {
    try {
      FileCollection files = filesProvider.getOrNull();
      if (files == null) {
        return Collections.emptyList();
      }
      return SonarUtils.exists(files).stream()
        .map(File::getAbsolutePath)
        .collect(Collectors.toList());
    } catch (RuntimeException e) {
      return Collections.emptyList();
    }
  }

  @TaskAction
  void run() throws IOException {
    if (Boolean.TRUE.equals(getSkipProject().getOrElse(false))) {
      return;
    }

    String displayName = getProjectName().get();
    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.info("Resolving properties for " + displayName + ".");
    }

    List<String> compileClasspathFilenames = getAbsolutePaths(compileClasspath);
    List<String> testCompileClasspathFilenames = getAbsolutePaths(testCompileClasspath);
    List<String> mainLibrariesFilenames = getAbsolutePaths(getMainLibraries());
    List<String> testLibrariesFilenames = getAbsolutePaths(getTestLibraries());
    List<String> androidSourcesFilenames = getAbsolutePaths(getAndroidSources());
    List<String> androidTestsFilenames = getAbsolutePaths(getAndroidTests());

    ProjectProperties projectProperties = new ProjectProperties.Builder(displayName, getTopLevelProject().getOrElse(false))
      .compileClasspath(compileClasspathFilenames)
      .testCompileClasspath(testCompileClasspathFilenames)
      .mainLibraries(mainLibrariesFilenames)
      .testLibraries(testLibrariesFilenames)
      .androidSources(androidSourcesFilenames)
      .androidTests(androidTestsFilenames)
      .build();

    outputDirectory.mkdirs();
    ResolutionSerializer.write(
      getOutputFile(),
      projectProperties
    );
    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.info("Resolved properties for " + displayName + " and wrote them to " + getOutputFile() + ".");
    }
  }
}
