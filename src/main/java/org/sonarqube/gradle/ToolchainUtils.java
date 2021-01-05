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
import java.util.Optional;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * Only access this class when running on an Gradle 6.7+
 */
class ToolchainUtils {

  private ToolchainUtils() {
  }

  static Optional<File> getJdkHome(JavaCompile compileTask) {
    Property<JavaCompiler> javaCompiler = compileTask.getJavaCompiler();
    if (javaCompiler.isPresent()) {
      return Optional.of(javaCompiler.get().getMetadata().getInstallationPath().getAsFile());
    }
    return Optional.empty();
  }

  public static boolean hasToolchains(JavaCompile compileTask) {
    return compileTask.getJavaCompiler().isPresent();
  }

  // Inspired by
  // https://github.com/gradle/gradle/blob/d3e4faca3df507176b67d9b3bb3ee91bf2aa070c/subprojects/language-java/src/main/java/org/gradle/api/tasks/compile/JavaCompile.java#L400
  static void configureCompatibilityOptions(JavaCompile compileTask, JavaCompilerConfiguration config) {
    final JavaInstallationMetadata toolchain = compileTask.getJavaCompiler().map(JavaCompiler::getMetadata).getOrNull();
    if (toolchain != null) {
      Property<Integer> release = compileTask.getOptions().getRelease();
      if (release.isPresent()) {
        config.setRelease(release.get().toString());
      } else {
        boolean isSourceOrTargetConfigured = false;
        if (compileTask.getSourceCompatibility() != null) {
          config.setSource(compileTask.getSourceCompatibility());
          isSourceOrTargetConfigured = true;
        }
        if (compileTask.getTargetCompatibility() != null) {
          config.setTarget(compileTask.getTargetCompatibility());
          isSourceOrTargetConfigured = true;
        }
        if (!isSourceOrTargetConfigured) {
          JavaLanguageVersion languageVersion = toolchain.getLanguageVersion();
          if (languageVersion.canCompileOrRun(10)) {
            config.setRelease(languageVersion.toString());
          } else {
            String version = languageVersion.toString();
            config.setSource(version);
            config.setTarget(version);
          }
        }
      }
    }
  }
}
