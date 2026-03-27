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

import com.android.build.api.variant.Variant;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.JavaCompile;

public class AndroidResolverTask extends DefaultTask {

  public static final String TASK_NAME = "androidResolver";
  public static final String TASK_DESCRIPTION = "Resolves and serializes properties and classpath information for the analysis of Android projects.";
  private static final Logger LOGGER = Logging.getLogger(AndroidResolverTask.class);

  private Provider<FileCollection> bootClassPath;
  private String testBuildType = null;
  private String configuredAndroidVariant = null;
  private final Set<Variant> variants = new LinkedHashSet<>();

  private Provider<FileCollection> mainLibraries;
  private Provider<FileCollection> testLibraries;

  @Inject
  public AndroidResolverTask() {
    super();
    // Some inputs are annotated with internal, thus grade cannot correctly compute if the task is up to date or not.
    this.getOutputs().upToDateWhen(task -> false);
  }

  @Internal
  public Provider<FileCollection> getBootClassPath() {
    return bootClassPath;
  }

  public void setBootClassPath(Provider<FileCollection> bootClassPath) {
    this.bootClassPath = bootClassPath;
  }

  @Nullable
  @Internal
  public String getConfiguredAndroidVariant() {
    return configuredAndroidVariant;
  }

  public void setConfiguredAndroidVariant(@Nullable String configuredAndroidVariant) {
    this.configuredAndroidVariant = configuredAndroidVariant;
  }

  @Internal
  public Set<Variant> getVariants() {
    return variants;
  }

  @Input
  public Provider<FileCollection> getMainLibraries() {
    return mainLibraries;
  }

  public void setMainLibraries(Provider<FileCollection> mainLibraries) {
    this.mainLibraries = mainLibraries;
  }

  @Input
  public Provider<FileCollection> getTestLibraries() {
    return testLibraries;
  }

  public void setTestLibraries(Provider<FileCollection> testLibraries) {
    this.testLibraries = testLibraries;
  }

  @TaskAction
  public void run() {
    LOGGER.info("Boot classpath: {}", bootClassPath.get().getFiles().stream().map(File::getName).collect(java.util.stream.Collectors.toList()));
    LOGGER.info("Main libraries: {}", mainLibraries.get().getFiles().stream().map(File::getName).collect(java.util.stream.Collectors.toList()));
    LOGGER.info("Test libraries: {}", testLibraries.get().getFiles().stream().map(File::getName).collect(java.util.stream.Collectors.toList()));
    LOGGER.info("Configured Android variant: {}", configuredAndroidVariant);
    LOGGER.info("Found variants: {}", variants.stream().map(Variant::getName).collect(java.util.stream.Collectors.toList()));
    LOGGER.info("Variant: {}", getVariant().getName());
  }

  @Nullable
  private Variant getVariant() {
    if (variants.isEmpty()) {
      return null;
    }

    if (configuredAndroidVariant == null) {
      // Take the first "test" buildType when there is no provided variant name.
      // Release variant may be obfuscated using proguard. Unit tests and coverage reports are also usually collected in debug mode.
      Optional<Variant> firstDebug = variants.stream().filter(v -> testBuildType != null && testBuildType.equals(v.getBuildType())).findFirst();
      Variant result = firstDebug.orElse(variants.iterator().next());
      LOGGER.info("No variant name specified to be used by SonarQube. Default to '{}'", result.getName());
      return result;
    }

    Optional<Variant> variant = variants.stream()
      .filter(v -> configuredAndroidVariant.equals(v.getName()))
      .findFirst();
    if (variant.isPresent()) {
      return variant.get();
    } else {
      throw new IllegalArgumentException(
        "Unable to find variant '"
          + configuredAndroidVariant
          + "' to use for SonarQube analysis. Candidates are: "
          + variants.stream().map(Variant::getName).collect(java.util.stream.Collectors.joining(", "))
      );
    }
  }

}
