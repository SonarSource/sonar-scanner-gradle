/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2021 SonarSource
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.jvm.Jvm;
import org.gradle.util.GradleVersion;

public class JavaCompilerUtils {
  private static final Logger LOGGER = Logging.getLogger(JavaCompilerUtils.class);

  private JavaCompilerUtils() {
    // Utility class
  }

  public static Optional<JavaCompilerConfiguration> extractJavaCompilerConfigurationFromCompileTasks(Project project) {
    TaskCollection<JavaCompile> javaCompileTaskCollection = project.getTasks().withType(JavaCompile.class);
    if (javaCompileTaskCollection.isEmpty()) {
      return Optional.empty();
    }
    List<JavaCompilerConfiguration> jdkHomesUsedByCompileTasks = javaCompileTaskCollection.stream()
      .map(JavaCompilerUtils::extractConfiguration)
      .collect(Collectors.toList());

    JavaCompilerConfiguration first = jdkHomesUsedByCompileTasks.get(0);
    if (!jdkHomesUsedByCompileTasks.stream().allMatch(config -> JavaCompilerConfiguration.same(config, first))) {
      LOGGER.info("Heterogeneous compiler configuration has been detected. Using compiler configuration from task: '" + first.getTaskName() + "'");
    }
    return Optional.of(first);
  }

  static JavaCompilerConfiguration extractConfiguration(JavaCompile compileTask) {
    JavaCompilerConfiguration javaCompilerConfiguration = new JavaCompilerConfiguration(compileTask.getName());
    configureCompatibilityOptions(compileTask, javaCompilerConfiguration);
    javaCompilerConfiguration.setJdkHome(extractJavaHome(compileTask).getAbsolutePath());
    return javaCompilerConfiguration;
  }

  private static File extractJavaHome(JavaCompile compileTask) {
    Optional<File> jdkHomeFromToolchains = Optional.empty();
    if (areToolchainsSupported()) {
      jdkHomeFromToolchains = ToolchainUtils.getJdkHome(compileTask);
    }

    if (jdkHomeFromToolchains.isPresent()) {
      return jdkHomeFromToolchains.get();
    } else {
      CompileOptions compileOptions = compileTask.getOptions();
      if (compileOptions.isFork()) {
        File javaHome = compileOptions.getForkOptions().getJavaHome();
        if (javaHome != null) {
          return javaHome;
        }
      }
      return Jvm.current().getJavaHome();
    }
  }

  private static boolean areToolchainsSupported() {
    return GradleVersion.current().compareTo(GradleVersion.version("6.7")) >= 0;
  }

  // Inspired by
  // https://github.com/gradle/gradle/blob/d3e4faca3df507176b67d9b3bb3ee91bf2aa070c/subprojects/language-java/src/main/java/org/gradle/api/tasks/compile/JavaCompile.java#L400
  private static void configureCompatibilityOptions(JavaCompile compileTask, JavaCompilerConfiguration config) {
    if (areToolchainsSupported() && ToolchainUtils.hasToolchains(compileTask)) {
      ToolchainUtils.configureCompatibilityOptions(compileTask, config);
    } else {
      Optional<String> release = getRelease(compileTask.getOptions());
      if (release.isPresent()) {
        config.setRelease(release.get());
      } else {
        config.setTarget(compileTask.getTargetCompatibility());
        config.setSource(compileTask.getSourceCompatibility());
      }
    }
  }

  private static Optional<String> getRelease(CompileOptions options) {
    if (GradleVersion.current().compareTo(GradleVersion.version("6.6")) >= 0) {
      return Gradle6dot6Utils.getRelease(options);
    }
    return Optional.empty();
  }

}
