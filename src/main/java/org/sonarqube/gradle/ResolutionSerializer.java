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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ResolutionSerializer {
  private ResolutionSerializer() {
    /* No instantiation expected */
  }

  public static Map<String, List<File>> read(File input) throws IOException {
    Map<String, List<File>> properties = new HashMap<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(input, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        String[] tokens = line.split("=");
        String classPathKey = tokens[0].trim();
        String librariesAsString = (tokens.length == 2) ? tokens[1].trim() : "";
        List<File> files = SonarUtils.splitAsCsv(librariesAsString).stream()
                .map(File::new)
                .collect(Collectors.toList());
        properties.put(classPathKey, files);
      }
    }
    return properties;
  }

  public static File write(File output, Map<String, List<File>> properties) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(output, StandardCharsets.UTF_8))) {
      for (Map.Entry<String, List<File>> entry : properties.entrySet()) {
        String key = entry.getKey();
        List<String> absolutePaths = entry.getValue().stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());
        String csvString = SonarUtils.joinAsCsv(absolutePaths);
        writer.write(String.format("%s=%s%s", key, csvString, System.lineSeparator()));
      }
      writer.newLine();
    }
    return output;
  }

}
