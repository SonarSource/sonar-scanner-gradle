/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support.normalization;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AndroidPathNormalizer {
  private static final String HOME_PLACEHOLDER = "${HOME}";
  private static final Pattern BUILD_TOOLS = Pattern.compile("/build-tools/\\d+\\.\\d+\\.\\d+/core-lambda-stubs\\.jar");
  private static final Pattern VERSIONED_TRANSFORMS = Pattern.compile("\\.gradle/caches/\\d+\\.\\d+/transforms/[a-f0-9]+");
  private static final Pattern STABLE_TRANSFORMS = Pattern.compile("\\.gradle/caches/transforms-3/[a-f0-9]+");
  private static final Pattern PROJECT_TRANSFORMS = Pattern.compile("/build/\\.transforms/[a-f0-9]+");
  private static final Pattern COMPILE_CLASSES = Pattern.compile("/compile[a-zA-Z0-9]+JavaWithJavac/classes");
  private static final Pattern JAVAC_CLASSES = Pattern.compile("/javac/[a-zA-Z0-9]+/classes");
  private static final Pattern R_JAR = Pattern.compile("/(process|generate)[a-zA-Z0-9]+(Resources|RFile|StubRFile)/R\\.jar");
  private static final Pattern CLASSES_JAR = Pattern.compile("/bundle[a-zA-Z0-9]+(ClassesToCompileJar|CompileToJar[a-zA-Z0-9]+)/classes\\.jar");
  private static final Pattern ENTRY = Pattern.compile("(?<=,|^)([^,]+)(?=,|$)");
  private static final Pattern JETIFIED = Pattern.compile(".*/(?:\\.gradle/caches/transforms-\\d+|build/\\.transforms)/<hash>/transformed/(?:instrumented_)?(jetified-[^,/]+\\.jar)");

  private AndroidPathNormalizer() {
  }

  public static String normalize(String value) {
    String result = BUILD_TOOLS.matcher(value).replaceAll("/build-tools/30.0.3/core-lambda-stubs.jar");
    result = VERSIONED_TRANSFORMS.matcher(result).replaceAll(".gradle/caches/transforms-3/<hash>");
    result = STABLE_TRANSFORMS.matcher(result).replaceAll(".gradle/caches/transforms-3/<hash>");
    result = PROJECT_TRANSFORMS.matcher(result).replaceAll("/build/.transforms/<hash>");
    result = COMPILE_CLASSES.matcher(result).replaceAll("/classes");
    result = JAVAC_CLASSES.matcher(result).replaceAll("/classes");
    result = R_JAR.matcher(result).replaceAll("/R.jar");
    result = CLASSES_JAR.matcher(result).replaceAll("/classes.jar");
    result = result.replace("/compile_app_classes_jar/", "/app_classes/").replace("/compile_library_classes_jar/", "/library_classes/");
    return normalizeJetifiedEntries(result);
  }

  private static String normalizeJetifiedEntries(String value) {
    Matcher matcher = ENTRY.matcher(value);
    StringBuilder buffer = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(buffer, normalizeJetifiedEntry(matcher.group(1)));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private static String normalizeJetifiedEntry(String entry) {
    Matcher jarMatcher = JETIFIED.matcher(entry);
    if (!jarMatcher.matches()) {
      return Matcher.quoteReplacement(entry);
    }
    return Matcher.quoteReplacement(HOME_PLACEHOLDER + "/.gradle/caches/transforms-3/<hash>/transformed/" + jarMatcher.group(1));
  }
}
