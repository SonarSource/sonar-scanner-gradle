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
import java.util.Set;

/**
 * Holds computed SonarQube properties along with a set of keys that users explicitly defined
 * (via sonarqube {} DSL, system properties, or environment variables).
 * <p>
 * This distinction is important because user-defined properties should not be filtered for non-existing paths,
 * as users may legitimately reference paths that don't exist yet or use wildcards/placeholders.
 */
class ComputedProperties {
  final Map<String, Object> properties;
  final Set<String> userDefinedKeys;

  ComputedProperties(Map<String, Object> properties, Set<String> userDefinedKeys) {
    this.properties = properties;
    this.userDefinedKeys = userDefinedKeys;
  }
}
