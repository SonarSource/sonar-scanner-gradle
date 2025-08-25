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

public final class ScanProperties {
  public static final String SKIP = "sonar.skip";
  public static final String PROJECT_SOURCE_DIRS = "sonar.sources";
  public static final String PROJECT_TEST_DIRS = "sonar.tests";
  public static final String LIBRARIES = "sonar.libraries";

  public static final String GRADLE_CACHE = "sonar.gradle.cache";


  /**
   * Should the given property be excluded from the gradle cache input?
   */
  public static  boolean excludePropertyFromCache(String key) {

    // Will be covered by input files
    if (key.endsWith(".libraries") || key.endsWith(".binaries")) {
      return true;
    }

    switch (key) {
      case "sonar.kotlin.gradleProjectRoot":
      case "sonar.projectBaseDir":
      case "sonar.working.directory":
      case "sonar.java.jdkHome":

      // Shall be included in the input files
      case ScanProperties.PROJECT_SOURCE_DIRS:
      case ScanProperties.PROJECT_TEST_DIRS:
        return true;
      default:
        return false;
    }
  }


  private ScanProperties() {
    /* This is a utility class with constants */
  }
}
