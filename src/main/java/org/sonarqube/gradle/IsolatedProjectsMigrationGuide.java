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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Builds the console messages that explain how to migrate a build to Gradle Isolated Projects. */
final class IsolatedProjectsMigrationGuide {
  static final String DOCUMENTATION_URL =
    "https://docs.sonarsource.com/sonarqube-server/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/";
  private static final int MAX_LISTED_PROJECTS = 10;

  private IsolatedProjectsMigrationGuide() {
    // Utility class
  }

  /**
   * Explains that some projects of the build do not provide Sonar data, because the plugin was only applied in the
   * root build script, and shows the settings-level configuration that fixes it.
   */
  static String missingProjectsMessage(Collection<String> missingProjectPaths, String pluginVersion) {
    return "The Sonar analysis cannot run with Gradle Isolated Projects because the 'org.sonarqube' plugin is not applied to "
      + missingProjectPaths.size() + " project(s) of the build: " + describe(missingProjectPaths) + ".\n\n"
      + howToMigrate(pluginVersion);
  }

  /** Explains why a subproject build script cannot find the {@code sonar} extension, and how to fix it. */
  static String missingExtensionMessage(String pluginVersion) {
    return "A subproject build script uses the 'sonar { }' block, but the 'org.sonarqube' plugin is not applied to that subproject.\n\n"
      + howToMigrate(pluginVersion);
  }

  /**
   * Explains that a build script configured the {@code sonar} extension of another project, and where to move it.
   *
   * @param configuredProjectPath the project that was configured, absent when Gradle reports {@code subprojects { }} or
   *                              {@code allprojects { }} without naming a project
   */
  static String crossProjectConfigurationMessage(@Nullable String configuredProjectPath) {
    String source = Optional.ofNullable(configuredProjectPath)
      .map(path -> "Move the 'sonar { }' configuration of project '" + path + "' from the root build script, for example\n"
        + "from 'project(\"" + path + "\") { }', into the build script of project '" + path + "':")
      .orElse("Move the 'sonar { }' configuration written in 'subprojects { }' or 'allprojects { }' of the root build script\n"
        + "into the build script of each subproject:");
    return String.join("\n",
      "With Gradle Isolated Projects, a build script can only configure the 'sonar { }' block of its own project.",
      source,
      "",
      "  sonar {",
      "    properties {",
      "      property(\"sonar.exclusions\", \"**/generated/**\")",
      "    }",
      "  }",
      "",
      "Configuration shared by many projects can go into a convention plugin (buildSrc or build-logic).",
      "More information: " + DOCUMENTATION_URL);
  }

  /** Explains that the Android Gradle plugin must be declared next to 'org.sonarqube' for its configuration to be read. */
  static String androidPluginNotVisibleMessage(String projectPath, String androidPluginId, String pluginVersion) {
    String version = pluginVersion.isEmpty() ? "<version>" : pluginVersion;
    return String.join("\n",
      "The Android Gradle plugin '" + androidPluginId + "' is applied to project '" + projectPath + "', but the 'org.sonarqube' plugin",
      "cannot read its configuration, because the two plugins are not loaded by the same class loader. This happens",
      "when the Android Gradle plugin is declared in a build script below the one declaring 'org.sonarqube', for",
      "example 'org.sonarqube' in the settings script and the Android Gradle plugin in the root build script.",
      "Declare the Android Gradle plugin next to 'org.sonarqube', in the settings script, without applying it:",
      "",
      "  // settings.gradle.kts",
      "  plugins {",
      "    id(\"" + androidPluginId + "\") version \"<android-gradle-plugin-version>\" apply false",
      "    id(\"org.sonarqube\") version \"" + version + "\"",
      "  }",
      "",
      "Then remove the version of the Android Gradle plugin from the build scripts.",
      "More information: " + DOCUMENTATION_URL);
  }

  private static String howToMigrate(String pluginVersion) {
    String version = pluginVersion.isEmpty() ? "<version>" : pluginVersion;
    return String.join("\n",
      "With Gradle Isolated Projects, the root project is no longer allowed to configure its subprojects, so applying the",
      "'org.sonarqube' plugin in the root build script only is not enough anymore. Apply the plugin once in the settings",
      "script instead:",
      "",
      "  // settings.gradle.kts",
      "  plugins {",
      "    id(\"org.sonarqube\") version \"" + version + "\"",
      "  }",
      "",
      "  // settings.gradle",
      "  plugins {",
      "    id 'org.sonarqube' version '" + version + "'",
      "  }",
      "",
      "The plugins block of the root build script can keep 'org.sonarqube' with the same version, without a version,",
      "or not at all. The 'sonar { }' blocks of the build scripts keep working unchanged.",
      "If your build already has a convention plugin applied to every project (buildSrc or build-logic), applying",
      "'org.sonarqube' in that convention plugin also works.",
      "",
      "Configuration of a subproject written in the root build script, such as 'subprojects { sonar { ... } }' or",
      "'project(\":app\") { sonar { ... } }', must move to the 'sonar { }' block of that subproject's build script.",
      "",
      "Run the analysis with: ./gradlew sonar",
      "More information: " + DOCUMENTATION_URL);
  }

  private static String describe(Collection<String> projectPaths) {
    List<String> sorted = projectPaths.stream().sorted().collect(Collectors.toList());
    if (sorted.size() <= MAX_LISTED_PROJECTS) {
      return String.join(", ", sorted);
    }
    return String.join(", ", sorted.subList(0, MAX_LISTED_PROJECTS)) + " and " + (sorted.size() - MAX_LISTED_PROJECTS) + " more";
  }
}
