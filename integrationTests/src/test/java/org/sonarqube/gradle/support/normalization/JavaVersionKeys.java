/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support.normalization;

public final class JavaVersionKeys {
  private static final String JAVA_SOURCE_PLACEHOLDER = "${JAVA_SOURCE}";
  private static final String JAVA_TARGET_PLACEHOLDER = "${JAVA_TARGET}";

  private JavaVersionKeys() {
  }

  public static boolean isJavaVersionKey(String key) {
    return "sonar.java.source".equals(key)
      || "sonar.java.target".equals(key)
      || key.endsWith(".sonar.java.source")
      || key.endsWith(".sonar.java.target");
  }

  public static String placeholderFor(String key) {
    return key.endsWith(".source") ? JAVA_SOURCE_PLACEHOLDER : JAVA_TARGET_PLACEHOLDER;
  }
}
