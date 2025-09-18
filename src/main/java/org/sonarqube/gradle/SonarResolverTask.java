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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class SonarResolverTask extends DefaultTask {
  public static final String TASK_NAME = "sonarResolver";
  private static final Logger LOGGER = Logger.getLogger(SonarResolverTask.class.getName());

  private String projectName;
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
    String filename;
    if (projectName == null || projectName.isEmpty()) {
      filename = "properties";
    } else {
      filename = String.format("%s.properties", projectName);
    }
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
    if (displayName.isEmpty()) {
      displayName = "top-level project";
    }
    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.info("Resolving properties for " + displayName + ".");
    }
    List<File> computedCompileClassPath = SonarUtils.exists(getCompileClasspath() == null ? Collections.emptyList() : getCompileClasspath())
            .stream()
            .collect(Collectors.toList());

    List<File> computedTestCompileClassPath = SonarUtils.exists(getTestCompileClasspath() == null ? Collections.emptyList() : getTestCompileClasspath())
            .stream()
            .collect(Collectors.toList());

    ResolutionSerializer.write(
            getOutputFile(),
            Map.of(
                    Constants.COMPILE_CLASSPATH, computedCompileClassPath,
                    Constants.TEST_COMPILE_CLASSPATH, computedTestCompileClassPath
            )
    );
    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.info("Resolved properties for " + displayName +" and wrote them to " + getOutputFile() + ".");
    }
  }
}
