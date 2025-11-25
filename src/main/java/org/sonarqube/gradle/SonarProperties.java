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

import java.util.Map;

/**
 * A mutable wrapper for user-facing SonarQube property configuration in Gradle build scripts.
 * <p>
 * This class is part of the public API and is used during the Gradle <strong>configuration phase</strong>
 * to allow users to configure SonarQube analysis properties via the {@code sonarqube { }} DSL block.
 * <p>
 * The wrapped {@code properties} map is pre-populated with defaults computed from the Gradle project model,
 * and can be further manipulated by users through the convenience methods provided.
 *
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>{@code
 * sonarqube {
 *     properties {
 *         property "sonar.projectKey", "my-project"
 *         properties([
 *             "sonar.sources": "src",
 *             "sonar.tests": "test"
 *         ])
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <strong>Property Value Conversion:</strong>
 * Before passing properties to the Scanner, values are converted to Strings as follows:
 * <ul>
 * <li>{@code Iterable}s are recursively converted and joined into a comma-separated String</li>
 * <li>All other values are converted to Strings by calling their {@code toString()} method</li>
 * </ul>
 *
 * <p>
 * <strong>Note:</strong> This class is used for configuration, not for transferring resolved dependency information.
 * For that purpose, see {@link ProjectProperties}.
 *
 * @see SonarExtension
 * @see ProjectProperties
 */
public class SonarProperties {

  private Map<String, Object> properties;

  public SonarProperties(Map<String, Object> properties) {
    this.properties = properties;
  }

  /**
   * Convenience method for setting a single property.
   *
   * @param key the key of the property to be added
   * @param value the value of the property to be added
   */
  public void property(String key, Object value) {
    properties.put(key, value);
  }

  /**
   * Convenience method for setting multiple properties.
   *
   * @param properties the properties to be added
   */
  public void properties(Map<String, ?> properties) {
    this.properties.putAll(properties);
  }

  /**
   * @return The Sonar properties for the current Gradle project that are to be passed to the Sonar gradle.
   */
  public Map<String, Object> getProperties() {
    return properties;
  }

}
