/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support.snapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SnapshotPaths {
  private SnapshotPaths() {
  }

  public static Path repositoryRoot() {
    Path workingDirectory = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    if (Files.exists(workingDirectory.resolve("integrationTests").resolve("pom.xml"))) {
      return workingDirectory;
    }
    Path parent = workingDirectory.getParent();
    if (parent != null && Files.exists(workingDirectory.resolve("pom.xml")) && Files.exists(parent.resolve("integrationTests").resolve("pom.xml"))) {
      return parent;
    }
    return workingDirectory;
  }
}
