/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support;

import java.io.File;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.junit.rules.TemporaryFolder;

final class ExecutionLayout {
  private final File executionDir;

  private ExecutionLayout(File executionDir) {
    this.executionDir = executionDir;
  }

  static ExecutionLayout prepare(TemporaryFolder temp, String project, @Nullable String subdir) throws Exception {
    File sourceDir = new File(ExecutionLayout.class.getResource(project).toURI());
    String copyName = project.startsWith("/") ? "." + project : project;
    File projectDir = new File(temp.getRoot(), copyName);
    if (!projectDir.exists()) {
      projectDir = temp.newFolder(copyName);
    }
    FileUtils.copyDirectory(sourceDir, projectDir);
    return new ExecutionLayout(subdir == null ? projectDir : new File(projectDir, subdir));
  }

  File executionDir() {
    return executionDir;
  }
}
