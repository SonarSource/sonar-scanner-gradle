/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

public final class SnapshotIO {
  private static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

  private SnapshotIO() {
  }

  public static void writeFile(Path targetFile, Map<String, String> properties) throws IOException {
    File parent = targetFile.toFile().getParentFile();
    if (parent != null && !parent.exists()) {
      parent.mkdirs();
    }
    try (FileWriter writer = new FileWriter(targetFile.toFile(), StandardCharsets.UTF_8)) {
      PRETTY_GSON.toJson(SnapshotPlaceholders.canonicalize(properties), writer);
    }
  }
}
