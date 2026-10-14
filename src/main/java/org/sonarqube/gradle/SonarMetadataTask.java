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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/** Writes locally computed properties as a task output consumable by the analysis owner. */
public abstract class SonarMetadataTask extends DefaultTask {
  public static final String TASK_NAME = "sonarMetadata";

  @Input public abstract Property<String> getProjectPath();
  @Input public abstract Property<Boolean> getSkipped();
  @Input public abstract MapProperty<String, String> getProperties();
  @Input public abstract ListProperty<String> getUserDefinedKeys();
  @Input public abstract MapProperty<String, String> getRootProperties();
  @Input public abstract ListProperty<String> getRootUserDefinedKeys();
  @Input public abstract Property<String> getDefaultProjectKey();
  @Input public abstract Property<Boolean> getScanAllSourcesOverridden();
  @Input public abstract Property<String> getProjectDirectory();
  @Input public abstract Property<String> getBuildFile();
  @OutputFile public abstract RegularFileProperty getMetadataFile();

  @TaskAction
  public void writeMetadata() throws IOException {
    SonarProjectMetadata metadata = SonarProjectMetadata.builder(
        getProjectPath().get(), getProjectDirectory().get(), getBuildFile().get())
      .skipped(getSkipped().get())
      .properties(new LinkedHashMap<>(getProperties().get()), new ArrayList<>(getUserDefinedKeys().get()))
      .rootProperties(new LinkedHashMap<>(getRootProperties().get()), new ArrayList<>(getRootUserDefinedKeys().get()))
      .defaultProjectKey(getDefaultProjectKey().get())
      .scanAllSourcesOverridden(getScanAllSourcesOverridden().get())
      .build();
    SonarMetadataSerializer.write(getMetadataFile().get().getAsFile(), metadata);
  }
}
