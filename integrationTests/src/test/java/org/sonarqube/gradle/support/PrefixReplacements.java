/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support;

import java.util.Map;
import java.util.Set;

public final class PrefixReplacements {
  private PrefixReplacements() {
  }

  public static String apply(String value, Map<String, String> replacements) {
    String result = value;
    for (Map.Entry<String, String> entry : replacements.entrySet()) {
      result = replacePrefix(result, entry.getValue(), entry.getKey());
    }
    return result;
  }

  public static String replaceM2(String value, Set<String> placeholders, String m2Placeholder) {
    String result = value;
    for (String placeholder : placeholders) {
      result = replacePrefix(result, placeholder + "/.m2", m2Placeholder);
    }
    return result;
  }

  public static String replacePrefix(String value, String oldPrefix, String newPrefix) {
    String result = value;
    String prefixed = "," + oldPrefix;
    int index = result.lastIndexOf(prefixed);
    while (index != -1) {
      result = result.substring(0, index + 1) + newPrefix + result.substring(index + prefixed.length());
      index = result.lastIndexOf(prefixed);
    }
    return result.startsWith(oldPrefix) ? newPrefix + result.substring(oldPrefix.length()) : result;
  }
}
