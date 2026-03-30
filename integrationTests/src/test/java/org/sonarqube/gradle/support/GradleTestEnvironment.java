/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support;

public final class GradleTestEnvironment {
  private GradleTestEnvironment() {
  }

  public static boolean isWindows() {
    return System.getProperty("os.name").startsWith("Windows");
  }

  public static String userDir() {
    return System.getProperty("user.dir");
  }

  public static String userHome() {
    return System.getProperty("user.home");
  }

  public static String githubWorkingDir() {
    return System.getenv("GITHUB_WORKING_DIR");
  }

  public static int javaVersion() {
    String version = System.getProperty("java.version");
    String normalized = version.startsWith("1.") ? version.substring(2, 3) : version.split("\\.")[0];
    return Integer.parseInt(normalized);
  }
}
