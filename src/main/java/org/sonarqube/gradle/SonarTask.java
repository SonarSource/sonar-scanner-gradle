/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2024 SonarSource
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.GradleVersion;
import org.sonarsource.scanner.lib.ScannerEngineBootstrapper;
import org.sonarsource.scanner.lib.ScannerEngineFacade;
import org.sonarsource.scanner.lib.internal.batch.LogOutput;

/**
 * Analyses one or more projects with the <a href="http://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle">SonarQube Scanner</a>.
 * Can be used with or without the {@code "sonar-gradle"} plugin.
 * If used together with the plugin, {@code properties} will be populated with defaults based on Gradle's object model and user-defined
 * values configured via {@link SonarExtension}.
 * If used without the plugin, all properties have to be configured manually.
 * For more information on how to configure the SonarQube Scanner, and on which properties are available, see the
 * <a href="http://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle">SonarQube Scanner documentation</a>.
 */
public class SonarTask extends ConventionTask {

  private static final Logger LOGGER = Logging.getLogger(SonarTask.class);

  private LogOutput logOutput = new DefaultLogOutput();

  private Provider<Map<String, String>> properties;

  private static class DefaultLogOutput implements LogOutput {
    @Override
    public void log(String formattedMessage, Level level) {
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

  /**
   * Logs output from the given {@link Level} at the {@link LogLevel#LIFECYCLE} log level, which is the default log
   * level for Gradle tasks. This can be used to specify the level of Sonar Scanner which it output during standard
   * task execution, without needing to override the log level for the full Gradle execution.
   */
  private static class LifecycleLogOutput implements LogOutput {

    private final Level logLevel;

    public LifecycleLogOutput(Level logLevel) {
      this.logLevel = logLevel;
    }

    @Override
    public void log(String formattedMessage, Level level) {
      if (level.ordinal() <= logLevel.ordinal()) {
        LOGGER.lifecycle(formattedMessage);
      }
    }
  }

  @TaskAction
  public void run() {
    if (SonarExtension.SONAR_DEPRECATED_TASK_NAME.equals(this.getName())) {
      LOGGER.warn("Task 'sonarqube' is deprecated. Use 'sonar' instead.");
    }

    Map<String, String> mapProperties = getProperties().get();
    if (mapProperties.isEmpty()) {
      LOGGER.warn("Skipping Sonar analysis: no properties configured, was it skipped in all projects?");
      return;
    }

    if (LOGGER.isDebugEnabled()) {
      mapProperties = new HashMap<>(mapProperties);
      mapProperties.put("sonar.verbose", "true");
      mapProperties = Collections.unmodifiableMap(mapProperties);
    }

    if (isSkippedWithProperty(mapProperties)) {
      return;
    }

    ScannerEngineBootstrapper scanner = ScannerEngineBootstrapper
      .create("ScannerGradle", getPluginVersion() + "/" + GradleVersion.current())
      .addBootstrapProperties(mapProperties);
    try (ScannerEngineFacade engineFacade = scanner.bootstrap()) {
      boolean analysisIsSuccessful = engineFacade.analyze(new HashMap<>());
      if (!analysisIsSuccessful) {
        throw new AnalysisException("The analysis has failed! See the logs for more details.");
      }
    } catch (AnalysisException e) {
      throw e;
    } catch (Exception e) {
      throw new AnalysisException(e);
    }
  }

  private String getPluginVersion() {
    InputStream inputStream = this.getClass().getResourceAsStream("/sonarqube-gradle-plugin-version.txt");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      return reader.readLine();
    } catch (IOException e) {
      LOGGER.warn("Failed to find the version of the plugin", e);
    }
    return "";
  }

  private static boolean isSkippedWithProperty(Map<String, String> properties) {
    if ("true".equalsIgnoreCase(properties.getOrDefault(ScanProperties.SKIP, "false"))) {
      LOGGER.warn("Sonar Scanner analysis skipped");
      return true;
    }
    return false;
  }

  /**
   * @return The String key/value pairs to be passed to the SonarQube Scanner.
   * {@code null} values are not permitted.
   */
  @Input
  public Provider<Map<String, String>> getProperties() {
    return properties;
  }

  void setProperties(Provider<Map<String, String>> properties) {
    this.properties = properties;
  }

  /**
   * Sets the {@link LogLevel} to use during Scanner execution. All logged messages from the Scanner at this level or
   * greater will be printed at the {@link LogLevel#LIFECYCLE} level, which is the default level for Gradle tasks. This
   * can be used to specify the level of Sonar Scanner which it output during standard task execution, without needing
   * to override the log level for the full Gradle execution.
   * <p>
   * This overrides the default {@link LogOutput} functionality, which passes logs through to the Gradle logger without
   * modifying the log level.
   *
   * @param logLevel the minimum log level to include in {@link LogLevel#LIFECYCLE} logs
   */
  public void useLoggerLevel(LogLevel logLevel) {
    LogOutput.Level internalLevel = LogOutput.Level.valueOf(logLevel.name());
    this.logOutput = new LifecycleLogOutput(internalLevel);
  }

  /**
   * @return The {@link LogOutput} object to use during Scanner execution. All logged messages from the Scanner will
   * pass through this object. If needed, a custom implementation can be used to handle logged output, such as printing
   * {@link LogLevel#INFO}-level log output when Gradle is only configured at the {@link LogLevel#LIFECYCLE} level.
   */
  @Internal
  public LogOutput getLogOutput() {
    return this.logOutput;
  }

  public void setLogOutput(LogOutput logOutput) {
    this.logOutput = logOutput;
  }
}
