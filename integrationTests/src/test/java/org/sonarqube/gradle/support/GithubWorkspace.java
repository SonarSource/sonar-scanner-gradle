/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support;

public final class GithubWorkspace {
  private GithubWorkspace() {
  }

  public static String homeEquivalent() {
    String githubWorkingDir = GradleTestEnvironment.githubWorkingDir();
    return githubWorkingDir == null ? GradleTestEnvironment.userHome() : githubWorkingDir.replace('/', java.io.File.separatorChar);
  }
}
