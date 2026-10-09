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

import java.util.List;
import java.util.Map;

/** A project's analysis configuration transferred between isolated Gradle projects. */
public class SonarProjectMetadata {
  public final int version;
  public final String projectPath;
  public final boolean skipped;
  public final Map<String, String> properties;
  public final List<String> userDefinedKeys;
  public final Map<String, String> rootProperties;
  public final List<String> rootUserDefinedKeys;
  public final String defaultProjectKey;
  public final boolean scanAllSourcesOverridden;
  public final String projectDirectory;
  public final String buildFile;

  public SonarProjectMetadata(String projectPath, boolean skipped, Map<String, String> properties,
    List<String> userDefinedKeys, String projectDirectory, String buildFile) {
    this(builder(projectPath, projectDirectory, buildFile)
      .skipped(skipped).properties(properties, userDefinedKeys).rootProperties(properties, userDefinedKeys));
  }

  private SonarProjectMetadata(Builder builder) {
    this.version = 3;
    this.projectPath = builder.projectPath;
    this.skipped = builder.skipped;
    this.properties = builder.properties;
    this.userDefinedKeys = builder.userDefinedKeys;
    this.rootProperties = builder.rootProperties;
    this.rootUserDefinedKeys = builder.rootUserDefinedKeys;
    this.defaultProjectKey = builder.defaultProjectKey;
    this.scanAllSourcesOverridden = builder.scanAllSourcesOverridden;
    this.projectDirectory = builder.projectDirectory;
    this.buildFile = builder.buildFile;
  }

  public static Builder builder(String projectPath, String projectDirectory, String buildFile) {
    return new Builder(projectPath, projectDirectory, buildFile);
  }

  public static final class Builder {
    private final String projectPath;
    private final String projectDirectory;
    private final String buildFile;
    private boolean skipped;
    private Map<String, String> properties = Map.of();
    private List<String> userDefinedKeys = List.of();
    private Map<String, String> rootProperties = Map.of();
    private List<String> rootUserDefinedKeys = List.of();
    private String defaultProjectKey = "";
    private boolean scanAllSourcesOverridden;

    private Builder(String projectPath, String projectDirectory, String buildFile) {
      this.projectPath = projectPath;
      this.projectDirectory = projectDirectory;
      this.buildFile = buildFile;
    }

    public Builder skipped(boolean skipped) {
      this.skipped = skipped;
      return this;
    }

    public Builder properties(Map<String, String> properties, List<String> userDefinedKeys) {
      this.properties = properties;
      this.userDefinedKeys = userDefinedKeys;
      return this;
    }

    public Builder rootProperties(Map<String, String> rootProperties, List<String> rootUserDefinedKeys) {
      this.rootProperties = rootProperties;
      this.rootUserDefinedKeys = rootUserDefinedKeys;
      return this;
    }

    public Builder defaultProjectKey(String defaultProjectKey) {
      this.defaultProjectKey = defaultProjectKey;
      return this;
    }

    public Builder scanAllSourcesOverridden(boolean scanAllSourcesOverridden) {
      this.scanAllSourcesOverridden = scanAllSourcesOverridden;
      return this;
    }

    public SonarProjectMetadata build() {
      return new SonarProjectMetadata(this);
    }
  }
}
