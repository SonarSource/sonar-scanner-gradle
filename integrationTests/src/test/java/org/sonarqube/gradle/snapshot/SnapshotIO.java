/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarqube.gradle.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public final class SnapshotIO {
  private static final Gson GSON = new Gson();
  private static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
  private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() { }.getType();
  private static final Path SNAPSHOT_DIR = Paths.get("integrationTests", "src", "test", "resources", "Snapshots");

  private SnapshotIO() {
    // Utility class: contains only static methods and is not intended to be instantiated.
  }

  private static Path repositoryRoot() {
    Path workingDirectory = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    if (Files.exists(workingDirectory.resolve("integrationTests").resolve("pom.xml"))) return workingDirectory;
    Path parent = workingDirectory.getParent();
    return parent != null && Files.exists(workingDirectory.resolve("pom.xml")) && Files.exists(parent.resolve("integrationTests").resolve("pom.xml")) ? parent : workingDirectory;
  }

  private static Path snapshotsRoot() {
    return repositoryRoot().resolve(SNAPSHOT_DIR);
  }

  public static Path file(String snapshotName) {
    return snapshotsRoot().resolve(snapshotName + ".json");
  }

  public static Map<String, String> load(Path snapshotFile) throws IOException {
    try (Reader reader = Files.newBufferedReader(snapshotFile, StandardCharsets.UTF_8)) {
      return GSON.fromJson(reader, STRING_MAP_TYPE);
    }
  }

  public static void write(String snapshotName, Map<String, String> properties) throws IOException {
    File parent = file(snapshotName).toFile().getParentFile();
    if (parent != null && !parent.exists()) parent.mkdirs();
    try (FileWriter writer = new FileWriter(file(snapshotName).toFile(), StandardCharsets.UTF_8)) {
      PRETTY_GSON.toJson(SnapshotUtils.canonicalize(properties), writer);
    }
  }
}
