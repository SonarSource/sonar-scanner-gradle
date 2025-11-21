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

/**
 * Constants for SonarQube scan property names.
 */
public final class ScanPropertyNames {
  // General
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
  /** @deprecated Kept for backward compatibility */
  @Deprecated
  public static final String LIBRARIES = "sonar.libraries";

  // Groovy configuration
  public static final String GROOVY_BINARIES = "sonar.groovy.binaries";

  // Kotlin configuration
  public static final String KOTLIN_GRADLE_PROJECT_ROOT = "sonar.kotlin.gradleProjectRoot";

  // Test reports
  public static final String JUNIT_REPORT_PATHS = "sonar.junit.reportPaths";
  /** @deprecated Kept for backward compatibility */
  @Deprecated
  public static final String JUNIT_REPORTS_PATH = "sonar.junit.reportsPath";
  /** @deprecated Kept for backward compatibility */
  @Deprecated
  public static final String SUREFIRE_REPORTS_PATH = "sonar.surefire.reportsPath";

  // Coverage reports
  public static final String JACOCO_XML_REPORT_PATHS = "sonar.coverage.jacoco.xmlReportPaths";

  // Android
  public static final String ANDROID_LINT_REPORT_PATHS = "sonar.androidLint.reportPaths";

  // Deprecated properties
  /** @deprecated Kept for backward compatibility */
  @Deprecated
  public static final String BINARIES = "sonar.binaries";

  private ScanPropertyNames() {
    /* This is a utility class with constants */
  }
}
