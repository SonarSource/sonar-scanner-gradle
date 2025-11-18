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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.util.Optional;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ResolutionSerializer {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Logger LOGGER = Logging.getLogger(ResolutionSerializer.class);

  private ResolutionSerializer() {
    /* No instantiation expected */
  }

  public static Optional<ProjectProperties> read(File input) throws IOException {
    if (!input.exists()) {
      return Optional.empty();
    }

    try (FileReader reader = new FileReader(input, StandardCharsets.UTF_8)) {
      ProjectProperties projectProperties = GSON.fromJson(reader, ProjectProperties.class);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Read project properties from file: {}", input.getAbsolutePath());
      }
      return Optional.of(projectProperties);
    }
  }

  public static void write(File output, ProjectProperties properties) throws IOException {
    if (properties.compileClasspath.isEmpty()
      && properties.testCompileClasspath.isEmpty()
      && properties.mainLibraries.isEmpty()
      && properties.testLibraries.isEmpty()) {
      // make sure we do not reuse output from previous execution
      Files.deleteIfExists(output.toPath());
      return;
    }

    try (FileWriter writer = new FileWriter(output, StandardCharsets.UTF_8)) {
      GSON.toJson(properties, writer);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Wrote project properties to file: {}", output.getAbsolutePath());
      }
    }
  }

}
