/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support.snapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import org.sonarqube.gradle.support.normalization.AndroidPathNormalizer;
import org.sonarqube.gradle.support.normalization.AndroidSdkPathNormalizer;
import org.sonarqube.gradle.support.normalization.JavaVersionKeys;

public final class SnapshotPlaceholders {
  private static final String JAVA_VERSION_PLACEHOLDER = "${JAVA_VERSION}";
  private static final String JAVA_SOURCE_PLACEHOLDER = "${JAVA_SOURCE}";
  private static final String JAVA_TARGET_PLACEHOLDER = "${JAVA_TARGET}";

  private SnapshotPlaceholders() {
  }

  public static Map<String, String> expand(Map<String, String> expected, Map<String, String> actual) {
    Map<String, String> expanded = new LinkedHashMap<>();
    expected.forEach((key, value) -> expanded.put(key, expandValue(key, value, actual)));
    return expanded;
  }

  public static Map<String, String> canonicalize(Map<String, String> properties) {
    Map<String, String> canonicalized = new LinkedHashMap<>();
    properties.forEach((key, value) -> canonicalized.put(key, canonicalizeValue(key, value)));
    return canonicalized;
  }

  private static String expandValue(String key, String value, Map<String, String> actual) {
    if (value == null) {
      return null;
    }
    String expanded = replaceJavaPlaceholders(value, actual.get(key));
    return AndroidPathNormalizer.normalize(AndroidSdkPathNormalizer.normalize(expanded));
  }

  private static String canonicalizeValue(String key, String value) {
    if (value == null) {
      return null;
    }
    String canonicalized = AndroidPathNormalizer.normalize(AndroidSdkPathNormalizer.normalize(value));
    return JavaVersionKeys.isJavaVersionKey(key) ? JavaVersionKeys.placeholderFor(key) : canonicalized;
  }

  private static String replaceJavaPlaceholders(String value, String actualValue) {
    if (actualValue == null) {
      return value;
    }
    return value.replace(JAVA_VERSION_PLACEHOLDER, actualValue)
      .replace(JAVA_SOURCE_PLACEHOLDER, actualValue)
      .replace(JAVA_TARGET_PLACEHOLDER, actualValue);
  }
}
