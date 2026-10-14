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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.sonarsource.scanner.lib.internal.shaded.com.google.gson.Gson;
import org.sonarsource.scanner.lib.internal.shaded.com.google.gson.JsonParseException;

/** Serialization for isolated project metadata. */
public final class SonarMetadataSerializer {
  private static final Gson GSON = new Gson();

  private SonarMetadataSerializer() {
  }

  public static SonarProjectMetadata read(File file) throws IOException {
    try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
      SonarProjectMetadata metadata = GSON.fromJson(reader, SonarProjectMetadata.class);
      if (metadata == null || metadata.version != 3 || metadata.projectPath == null || metadata.properties == null
        || metadata.userDefinedKeys == null || metadata.rootProperties == null || metadata.rootUserDefinedKeys == null
        || metadata.defaultProjectKey == null || metadata.projectDirectory == null || metadata.buildFile == null) {
        throw new IOException("Invalid Sonar project metadata: " + file);
      }
      return metadata;
    } catch (JsonParseException e) {
      throw new IOException("Invalid Sonar project metadata: " + file, e);
    }
  }

  public static void write(File file, SonarProjectMetadata metadata) throws IOException {
    File parent = file.getParentFile();
    if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
      throw new IOException("Cannot create Sonar metadata directory: " + parent);
    }
    try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
      GSON.toJson(metadata, writer);
    }
  }
}
