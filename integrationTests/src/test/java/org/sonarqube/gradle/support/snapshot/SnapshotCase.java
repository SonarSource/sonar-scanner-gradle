/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle.support.snapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.sonarqube.gradle.support.AbstractGradleIT;
import org.sonarqube.gradle.run_configuration.DefaultRunConfiguration;

public final class SnapshotCase {
  private final String name;
  private final String project;
  private final String executionSubdirectory;
  private final List<String> arguments;
  private final Set<String> ignoredProperties;
  private final String minGradle;
  private final String maxGradleExclusive;
  private final String minAndroidGradle;
  private final boolean requiresAndroid;

  public SnapshotCase(Builder builder) {
    this.name = builder.name;
    this.project = builder.project;
    this.executionSubdirectory = builder.executionSubdirectory;
    this.arguments = List.copyOf(builder.arguments);
    this.ignoredProperties = Set.copyOf(builder.ignoredProperties);
    this.minGradle = builder.minGradle;
    this.maxGradleExclusive = builder.maxGradleExclusive;
    this.minAndroidGradle = builder.minAndroidGradle;
    this.requiresAndroid = builder.requiresAndroid;
  }

  public String name() { return name; }

  public boolean shouldRun() {
    if (minGradle != null && AbstractGradleIT.getGradleVersion().isLowerThan(minGradle)) return false;
    if (maxGradleExclusive != null && !AbstractGradleIT.getGradleVersion().isLowerThan(maxGradleExclusive)) return false;
    if (!requiresAndroid) return true;
    if (AbstractGradleIT.getAndroidGradleVersion() == null) return false;
    return minAndroidGradle == null || AbstractGradleIT.getAndroidGradleVersion().isGreaterThanOrEqualTo(minAndroidGradle);
  }

  public Map<String, String> collect(AbstractGradleIT test, SnapshotComparisonPolicy policy) throws Exception {
    Properties properties = test.runGradlewSonarSimulationModeWithEnv(project, executionSubdirectory, Collections.emptyMap(), new DefaultRunConfiguration(), arguments.toArray(String[]::new));
    return policy.sanitize(new LinkedHashMap<>(AbstractGradleIT.extractComparableProperties(properties)), ignoredProperties);
  }

  public Map<String, String> expected(Map<String, String> stored, Map<String, String> actual, SnapshotComparisonPolicy policy) {
    return policy.sanitize(AbstractGradleIT.expandSnapshotPlaceholders(stored, actual), ignoredProperties);
  }

  @Override public String toString() { return name; }

  public static final class Builder {
    private final String name;
    private final String project;
    private final String executionSubdirectory;
    private final List<String> arguments;
    private final Set<String> ignoredProperties = new LinkedHashSet<>();
    private String minGradle;
    private String maxGradleExclusive;
    private String minAndroidGradle;
    private boolean requiresAndroid;

    public Builder(String name, String project, String executionSubdirectory, String... args) {
      this.name = name;
      this.project = project;
      this.executionSubdirectory = executionSubdirectory;
      this.arguments = List.of(args);
    }

    public Builder minGradle(String value) { this.minGradle = value; return this; }
    public Builder maxGradleExclusive(String value) { this.maxGradleExclusive = value; return this; }
    public Builder gradleRange(String min, String max) { this.minGradle = min; this.maxGradleExclusive = max; return this; }
    public Builder requiresAndroid() { this.requiresAndroid = true; return this; }
    public Builder minAndroidGradle(String value) { this.minAndroidGradle = value; return this; }
    public Builder ignoreProperty(String key) { this.ignoredProperties.add(key); return this; }
    public SnapshotCase build() { return new SnapshotCase(this); }
  }
}
