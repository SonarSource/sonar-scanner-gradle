/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support.snapshot;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public final class SnapshotRepository {
  private static final Gson GSON = new Gson();
  private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() { }.getType();
  private static final Path SNAPSHOT_DIR = Paths.get("integrationTests", "src", "test", "resources", "PropertySnapshotTest");
  private final Path root;

  public SnapshotRepository(Path repositoryRoot) {
    this.root = repositoryRoot.resolve(SNAPSHOT_DIR);
  }

  public Path file(String snapshotName) {
    return root.resolve(snapshotName + ".json");
  }

  public Map<String, String> load(Path snapshotFile) throws IOException {
    try (Reader reader = Files.newBufferedReader(snapshotFile, StandardCharsets.UTF_8)) {
      return GSON.fromJson(reader, STRING_MAP_TYPE);
    }
  }

  public void write(String snapshotName, Map<String, String> properties) throws IOException {
    SnapshotIO.writeFile(file(snapshotName), properties);
  }
}
