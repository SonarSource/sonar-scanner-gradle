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
package org.sonarqube.gradle.properties;

import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Full identifier of a sonar property, they are passed to the analyzers.
 * Some properties are only global and other can be scoped within a subproject.
 * The full identifier consists of subproject + property name.
 */
public class SonarProperty {
  public static final String SKIP = "sonar.skip";
  public static final String GRADLE_SCAN_ALL = "sonar.gradle.scanAll";
  public static final String VERBOSE = "sonar.verbose";

  // Project structure
  public static final String PROJECT_KEY = "sonar.projectKey";
  public static final String MODULE_KEY = "sonar.moduleKey";
  public static final String MODULES = "sonar.modules";
  public static final String PROJECT_NAME = "sonar.projectName";
  public static final String PROJECT_DESCRIPTION = "sonar.projectDescription";
  public static final String PROJECT_VERSION = "sonar.projectVersion";
  public static final String PROJECT_BASE_DIR = "sonar.projectBaseDir";
  public static final String WORKING_DIRECTORY = "sonar.working.directory";

  // Sources and tests
  public static final String PROJECT_SOURCE_DIRS = "sonar.sources";
  public static final String PROJECT_TEST_DIRS = "sonar.tests";
  public static final String SOURCE_ENCODING = "sonar.sourceEncoding";

  // Java configuration
  public static final String JAVA_SOURCE = "sonar.java.source";
  public static final String JAVA_TARGET = "sonar.java.target";
  public static final String JAVA_ENABLE_PREVIEW = "sonar.java.enablePreview";
  public static final String JAVA_JDK_HOME = "sonar.java.jdkHome";
  public static final String JAVA_BINARIES = "sonar.java.binaries";
  public static final String JAVA_LIBRARIES = "sonar.java.libraries";
  public static final String JAVA_TEST_BINARIES = "sonar.java.test.binaries";
  public static final String JAVA_TEST_LIBRARIES = "sonar.java.test.libraries";

  /**
   * Kept for backward compatibility
   */
  public static final String LIBRARIES = "sonar.libraries";

  // Groovy configuration
  public static final String GROOVY_BINARIES = "sonar.groovy.binaries";

  // Kotlin configuration
  public static final String KOTLIN_GRADLE_PROJECT_ROOT = "sonar.kotlin.gradleProjectRoot";

  // Test reports
  public static final String JUNIT_REPORT_PATHS = "sonar.junit.reportPaths";

  /**
   * Kept for backward compatibility
   */
  public static final String JUNIT_REPORTS_PATH = "sonar.junit.reportsPath";

  /**
   * Kept for backward compatibility
   */
  public static final String SUREFIRE_REPORTS_PATH = "sonar.surefire.reportsPath";

  // Coverage reports
  public static final String JACOCO_XML_REPORT_PATHS = "sonar.coverage.jacoco.xmlReportPaths";

  // Android
  public static final String ANDROID_LINT_REPORT_PATHS = "sonar.androidLint.reportPaths";

  /**
   * Kept for backward compatibility
   */
  public static final String BINARIES = "sonar.binaries";

  private static final Set<String> ALL_SONAR_PROPERTIES = Set.of(
    SKIP,
    GRADLE_SCAN_ALL,
    VERBOSE,
    PROJECT_KEY,
    MODULE_KEY,
    MODULES,
    PROJECT_NAME,
    PROJECT_DESCRIPTION,
    PROJECT_VERSION,
    PROJECT_BASE_DIR,
    WORKING_DIRECTORY,
    PROJECT_SOURCE_DIRS,
    PROJECT_TEST_DIRS,
    SOURCE_ENCODING,
    JAVA_SOURCE,
    JAVA_TARGET,
    JAVA_ENABLE_PREVIEW,
    JAVA_JDK_HOME,
    JAVA_BINARIES,
    JAVA_LIBRARIES,
    JAVA_TEST_BINARIES,
    JAVA_TEST_LIBRARIES,
    LIBRARIES,
    GROOVY_BINARIES,
    KOTLIN_GRADLE_PROJECT_ROOT,
    JUNIT_REPORT_PATHS,
    JUNIT_REPORTS_PATH,
    SUREFIRE_REPORTS_PATH,
    JACOCO_XML_REPORT_PATHS,
    ANDROID_LINT_REPORT_PATHS,
    BINARIES
  );


  /**
   * if subproject is null then the property belong to the root project.
   */
  @Nullable
  private final String subproject;
  private final String propertyName;

  /**
   * Parse a property as a string, refer to {@code toString()} for the  exact format.
   * The format is approximately {@code "${subproject_name}.{property_name}"}.
   * {@code parse(prop.toString()).equals(prop)} is always true
   * <p>
   * Note, module names can also contain dots. The only way to parse a property is to verify if it has as suffix one of the properties above.
   *
   * @param value a string that respects the property format
   * @return parsed property or empty if parsing failed
   */
  public static Optional<SonarProperty> parse(String value) {
    if (value == null || value.isEmpty()) {
      return Optional.empty();
    }

    for (String prop : ALL_SONAR_PROPERTIES) {
      if (value.equals(prop)) {
        return Optional.of(new SonarProperty("", prop));
      } else if (value.endsWith("." + prop)) {
        String module = value.substring(0, value.length() - prop.length() - 1);
        if (!module.isEmpty()) {
          return Optional.of(new SonarProperty(module, prop));
        }
      }
    }
    return Optional.empty();
  }

  public static SonarProperty rootProjectProperty(String property) {
    return new SonarProperty(null, property);
  }

  public SonarProperty(@Nullable String subproject, String propertyName) {
    if (subproject != null && subproject.isEmpty()) {
      subproject = null;
    }
    this.subproject = subproject;
    this.propertyName = propertyName;
  }

  @Nullable
  public String getSubproject() {
    return subproject;
  }

  public String getProperty() {
    return propertyName;
  }

  @Override
  public String toString() {
    if (subproject != null) {
      return subproject + "." + propertyName;
    } else {
      return propertyName;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    SonarProperty that = (SonarProperty) o;

    if (subproject == null && that.subproject == null) {
      return propertyName.equals(that.propertyName);
    } else {
      return propertyName.equals(that.propertyName) && subproject != null && subproject.equals(that.subproject);
    }
  }

  @Override
  public int hashCode() {
    return this.toString().hashCode();
  }
}
