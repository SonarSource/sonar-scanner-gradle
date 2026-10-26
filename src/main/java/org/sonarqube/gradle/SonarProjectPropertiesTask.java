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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Contains project-specific absolute paths")
public abstract class SonarProjectPropertiesTask extends DefaultTask {
  @Input
  public abstract MapProperty<String, String> getAnalysisProperties();
  @Input
  public abstract ListProperty<String> getUserDefinedKeys();
  @Input
  public abstract Property<String> getProjectPath();
  @Input
  public abstract Property<String> getBuildScript();
  @Input
  public abstract Property<Boolean> getSkipProject();
  @OutputFile
  public abstract RegularFileProperty getOutputFile();

  @TaskAction
  public void writeProperties() throws IOException {
    Properties properties = new Properties();
    properties.putAll(getAnalysisProperties().get());
    properties.setProperty("_gradle.path", getProjectPath().get());
    properties.setProperty("_gradle.buildScript", getBuildScript().get());
    properties.setProperty("_gradle.skip", getSkipProject().get().toString());
    properties.setProperty("_gradle.userKeys", String.join("\n", getUserDefinedKeys().get()));
    java.nio.file.Path output = getOutputFile().get().getAsFile().toPath();
    Files.createDirectories(output.getParent());
    try (OutputStream stream = Files.newOutputStream(output)) {
      properties.store(stream, "Sonar project properties");
    }
  }
}
