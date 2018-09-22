/**
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2018 SonarSource
 * sonarqube@googlegroups.com
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.sonarsource.scanner.api.EmbeddedScanner;
import org.sonarsource.scanner.api.LogOutput;
import org.sonarsource.scanner.api.ScanProperties;

/**
 * Analyses one or more projects with the <a href="http://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle">SonarQube Scanner</a>.
 * Can be used with or without the {@code "sonar-gradle"} plugin.
 * If used together with the plugin, {@code properties} will be populated with defaults based on Gradle's object model and user-defined
 * values configured via {@link SonarQubeExtension}.
 * If used without the plugin, all properties have to be configured manually.
 * For more information on how to configure the SonarQube Scanner, and on which properties are available, see the
 * <a href="http://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle">SonarQube Scanner documentation</a>.
 */
public class SonarQubeTask extends ConventionTask {

  private static final Logger LOGGER = Logging.getLogger(SonarQubeTask.class);

  private static final LogOutput LOG_OUTPUT = new DefaultLogOutput();

  private static class DefaultLogOutput implements LogOutput {
    @Override public void log(String formattedMessage, Level level) {
      switch (level) {
        case TRACE:
          LOGGER.trace(formattedMessage);
          return;
        case DEBUG:
          LOGGER.debug(formattedMessage);
          return;
        case INFO:
          LOGGER.info(formattedMessage);
          return;
        case WARN:
          LOGGER.warn(formattedMessage);
          return;
        case ERROR:
          LOGGER.error(formattedMessage);
          return;
        default:
          throw new IllegalArgumentException(level.name());
      }
    }
  }

  private Map<String, String> sonarProperties;

  @TaskAction
  public void run() {
    Map<String, String> properties = getProperties();

    if (properties.isEmpty()) {
      LOGGER.warn("Skipping SonarQube analysis: no properties configured, was it skipped in all projects?");
      return;
    }

    if (LOGGER.isDebugEnabled()) {
      properties.put("sonar.verbose", "true");
    }

    if (isSkippedWithProperty(properties)) {
      return;
    }

    EmbeddedScanner scanner = EmbeddedScanner.create("ScannerGradle", getPluginVersion() + "/" + getProject().getGradle().getGradleVersion(), LOG_OUTPUT)
      .addGlobalProperties(properties);
    scanner.start();
    scanner.execute(new HashMap<>());
  }

  private String getPluginVersion() {
    InputStream inputStream = this.getClass().getResourceAsStream("/version.txt");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      return reader.readLine();
    } catch(IOException e) {
      LOGGER.warn("Failed to find the version of the plugin", e);
    }
    return "";
  }

  private static boolean isSkippedWithProperty(Map<String, String> properties) {
    if ("true".equalsIgnoreCase(properties.getOrDefault(ScanProperties.SKIP, "false"))) {
      LOGGER.warn("SonarQube Scanner analysis skipped");
      return true;
    }
    return false;
  }

  /**
   * @return The String key/value pairs to be passed to the SonarQube Scanner.
   * {@code null} values are not permitted.
   */
  @Input
  public Map<String, String> getProperties() {
    if (sonarProperties == null) {
      sonarProperties = new LinkedHashMap<>();
    }

    return sonarProperties;
  }

}
