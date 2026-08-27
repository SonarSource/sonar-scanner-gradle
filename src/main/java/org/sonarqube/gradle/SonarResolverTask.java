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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskDependency;


public abstract class SonarResolverTask extends DefaultTask {
  public static final String TASK_NAME = "sonarResolver";
  public static final String TASK_DESCRIPTION = "Resolves and serializes project information and classpath for SonarQube analysis.";
  private static final Logger LOGGER = Logger.getLogger(SonarResolverTask.class.getName());
  private static final String FILE_COLLECTION_RESOLUTION_FAILURE_MESSAGE = "Failed to resolve file collection input; skipping it.";

  private final ConfigurableFileCollection trackedCompileClasspath;
  private final ConfigurableFileCollection trackedTestCompileClasspath;
  private Provider<FileCollection> compileClasspath;
  private Provider<FileCollection> testCompileClasspath;
  @Nullable
  private Provider<FileCollection> legacyMainLibraries;
  @Nullable
  private Provider<FileCollection> legacyTestLibraries;
  private File outputDirectory;

  @Inject
  public SonarResolverTask() {
    super();
    this.trackedCompileClasspath = getProject().files();
    this.trackedTestCompileClasspath = getProject().files();
  }


  @Input
  public abstract Property<String> getProjectName();

  @Input
  public abstract Property<Boolean> getTopLevelProject();

  public void setCompileClasspath(Provider<FileCollection> compileClasspath) {
    this.compileClasspath = compileClasspath;
    this.getCompileClasspath().setFrom(compileClasspath.map(SonarResolverTask::getClasspathEntries));
    this.mustRunAfter(getClasspathProducerOrdering(compileClasspath));
  }

  public void setTestCompileClasspath(Provider<FileCollection> testCompileClasspath) {
    this.testCompileClasspath = testCompileClasspath;
    this.getTestCompileClasspath().setFrom(testCompileClasspath.map(SonarResolverTask::getClasspathEntries));
    this.mustRunAfter(getClasspathProducerOrdering(testCompileClasspath));
  }

  public void setLegacyMainLibraries(Provider<FileCollection> legacyMainLibraries) {
    this.legacyMainLibraries = legacyMainLibraries;
    this.getOutputs().upToDateWhen(task -> false);
  }

  public void setLegacyTestLibraries(Provider<FileCollection> legacyTestLibraries) {
    this.legacyTestLibraries = legacyTestLibraries;
    this.getOutputs().upToDateWhen(task -> false);
  }

  @Classpath
  public ConfigurableFileCollection getCompileClasspath() {
    return trackedCompileClasspath;
  }

  @Classpath
  public ConfigurableFileCollection getTestCompileClasspath() {
    return trackedTestCompileClasspath;
  }

  @Classpath
  public abstract ConfigurableFileCollection getMainLibraries();

  @Classpath
  public abstract ConfigurableFileCollection getTestLibraries();

  @PathSensitive(PathSensitivity.RELATIVE)
  @InputFiles
  public abstract ConfigurableFileCollection getAndroidSources();

  @PathSensitive(PathSensitivity.RELATIVE)
  @InputFiles
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
    try {
      return SonarUtils.exists(fileCollection)
        .stream()
        .map(File::getAbsolutePath)
        .collect(Collectors.toList());
    } catch (RuntimeException e) {
      LOGGER.log(Level.WARNING, FILE_COLLECTION_RESOLUTION_FAILURE_MESSAGE, e);
      return Collections.emptyList();
    }
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
      LOGGER.log(Level.WARNING, FILE_COLLECTION_RESOLUTION_FAILURE_MESSAGE, e);
      return Collections.emptyList();
    }
  }

  private static List<String> getAbsolutePaths(FileCollection fileCollection, @Nullable Provider<FileCollection> additionalFilesProvider) {
    List<String> filenames = new ArrayList<>(getAbsolutePaths(fileCollection));
    if (additionalFilesProvider != null) {
      filenames.addAll(getAbsolutePaths(additionalFilesProvider));
    }
    return filenames;
  }

  private static List<File> getClasspathEntries(FileCollection fileCollection) {
    try {
      // Keep missing producer outputs in the tracked value so configuration-cache state does not depend on
      // whether those outputs exist while Gradle constructs the task graph. Serialization filters them later.
      return new ArrayList<>(fileCollection.getFiles());
    } catch (RuntimeException e) {
      LOGGER.log(Level.WARNING, FILE_COLLECTION_RESOLUTION_FAILURE_MESSAGE, e);
      return Collections.emptyList();
    }
  }

  /**
   * Lazily finds all tasks that may produce the classpath.
   * <p>
   * A FileCollection can expose an aggregate producer such as {@code classes}. If only one of its dependencies, such
   * as {@code processResources}, is selected, the aggregate task is absent from the task graph. Ordering sonarResolver
   * only after the aggregate task would therefore not satisfy Gradle's implicit-dependency validation.
   * <p>
   * Including transitive task dependencies ensures every selected concrete producer is ordered before sonarResolver,
   * without making it a task dependency.
   */
  private static TaskDependency getClasspathProducerOrdering(Provider<FileCollection> filesProvider) {
    return task -> {
      try {
        FileCollection files = filesProvider.getOrNull();
        if (files == null) {
          return Collections.emptySet();
        }
        return collectTransitiveTaskDependencies(task, files.getBuildDependencies().getDependencies(task));
      } catch (RuntimeException e) {
        LOGGER.log(Level.WARNING, FILE_COLLECTION_RESOLUTION_FAILURE_MESSAGE, e);
        return Collections.emptySet();
      }
    };
  }

  private static Set<Task> collectTransitiveTaskDependencies(Task consumer, Set<? extends Task> producerTasks) {
    Set<Task> producersToOrderAfter = new HashSet<>();
    Queue<Task> queue = new ArrayDeque<>(producerTasks);
    while (!queue.isEmpty()) {
      Task producer = queue.remove();
      if (producer != consumer && producersToOrderAfter.add(producer)) {
        queue.addAll(producer.getTaskDependencies().getDependencies(producer));
      }
    }
    return producersToOrderAfter;
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
    List<String> mainLibrariesFilenames = getAbsolutePaths(getMainLibraries(), legacyMainLibraries);
    List<String> testLibrariesFilenames = getAbsolutePaths(getTestLibraries(), legacyTestLibraries);
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
