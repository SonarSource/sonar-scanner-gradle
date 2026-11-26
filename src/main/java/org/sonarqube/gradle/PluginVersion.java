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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/** Reads the version of this plugin, written into the jar at build time. */
final class PluginVersion {
  private static final Logger LOGGER = Logging.getLogger(PluginVersion.class);
  private static final String VERSION_RESOURCE = "/org/sonarqube/gradle/sonarqube-gradle-plugin-version.txt";

  private PluginVersion() {
    // Utility class
  }

  static String get() {
    InputStream inputStream = PluginVersion.class.getResourceAsStream(VERSION_RESOURCE);
    if (inputStream == null) {
      LOGGER.warn("Failed to find the version of the plugin");
      return "";
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String version = reader.readLine();
      return version == null ? "" : version;
    } catch (IOException e) {
      LOGGER.warn("Failed to find the version of the plugin", e);
      return "";
    }
  }
}
