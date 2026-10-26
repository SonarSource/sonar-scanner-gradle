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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SonarIsolatedProjectsTest {
  @TempDir Path directory;

  @Test
  void preservesAnalysisPropertiesAndReusesConfigurationCache() throws IOException {
    write("settings.gradle", "rootProject.name = 'isolated-analysis'\ninclude ':nested:library', ':consumer', ':skipped:child'\n");
    write("build.gradle", "plugins { id 'org.sonarqube' }\ngroup = 'example'\nversion = '1.0'\n" +
      "sonar { properties { property 'sonar.exclusions', '**/generated/**'; property 'sonar.coverage.jacoco.xmlReportPaths', 'custom.xml' } }\n");
    write("nested/build.gradle", "group = 'example'\nversion = '1.0'\n");
    write("nested/library/build.gradle", "plugins { id 'java-library' }\ngroup = 'example'\nversion = '1.0'\n" +
      "sonar { properties { property 'sonar.projectName', 'Custom library' } }\n");
    write("consumer/build.gradle", "plugins { id 'java-library' }\ngroup = 'example'\nversion = '1.0'\n" +
      "dependencies { implementation project(':nested:library') }\n");
    write("skipped/build.gradle", "sonar { skipProject = true }\n");
    write("skipped/child/build.gradle", "plugins { id 'java-library' }\n");
    write("nested/library/src/main/java/Example.java", "public class Example { public int value() { return 1; } }\n");
    write("consumer/src/main/java/Consumer.java", "public class Consumer { int value() { return new Example().value(); } }\n");
    Path dump = directory.resolve("analysis.properties");
    runner(false, dump).build();
    Properties original = load(dump);
    write("settings.gradle", "plugins { id 'org.sonarqube.settings' }\nrootProject.name = 'isolated-analysis'\n" +
      "include ':nested:library', ':consumer', ':skipped:child'\n");
    BuildResult first = runner(true, dump).build();
    assertThat(first.getOutput()).contains("Configuration cache entry stored");
    Properties isolated = load(dump);
    // Legacy resolver files also contain unused properties for descendants of skipped modules.
    original.keySet().removeIf(key -> key.toString().startsWith(":skipped."));
    // Module ordering does not affect analysis; artifact resolution uses a stable sorted order.
    original.replaceAll((key, value) -> key.toString().endsWith("sonar.modules") ? sortedModules(value) : value);
    Properties normalized = new Properties();
    normalized.putAll(isolated);
    normalized.replaceAll((key, value) -> key.toString().endsWith("sonar.modules") ? sortedModules(value) : value);
    assertThat(normalized).isEqualTo(original);
    assertThat(isolated.getProperty(":nested.:nested:library.sonar.projectName")).isEqualTo("Custom library");
    assertThat(isolated.getProperty("sonar.modules")).doesNotContain("skipped");
    assertThat(isolated.getProperty("sonar.coverage.jacoco.xmlReportPaths")).isEqualTo("custom.xml");
    BuildResult repeat = runner(true, dump).build();
    assertThat(repeat.getOutput()).contains("Reusing configuration cache");
    assertThat(repeat.task(":nested:library:sonarProjectProperties").getOutcome().name()).isEqualTo("UP_TO_DATE");
    write("nested/library/src/main/java/Example.java", "public class Example { public int value() { return 2; } }\n");
    BuildResult edited = runner(true, dump).build();
    assertThat(edited.getOutput()).contains("Reusing configuration cache");
    assertThat(edited.task(":nested:library:compileJava").getOutcome().name()).isEqualTo("SUCCESS");
  }

  private GradleRunner runner(boolean isolated, Path dump) {
    List<String> arguments = new ArrayList<>(Arrays.asList("classes", "sonar", "--stacktrace", "--configuration-cache-problems=fail",
      "-Dsonar.scanner.internal.dumpToFile=" + dump.toAbsolutePath()));
    if (isolated) {
      arguments.add("-Dorg.gradle.isolated-projects=true");
    }
    return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath()
      .withGradleVersion("9.8.1").withArguments(arguments);
  }

  @Test
  void collectsKotlinBuildScriptsAndExcludesSkippedSubtrees() throws IOException {
    write("settings.gradle.kts", "plugins { id(\"org.sonarqube.settings\") }\n" +
      "rootProject.name = \"kotlin-build-scripts\"\ninclude(\":nested:library\", \":skipped:child\")\n");
    write("build.gradle.kts", "plugins { id(\"org.sonarqube\") }\n");
    write("nested/build.gradle.kts", "plugins { base }\n");
    write("nested/library/build.gradle.kts", "plugins { `java-library` }\n");
    write("skipped/build.gradle.kts", "configure<org.sonarqube.gradle.SonarExtension> { isSkipProject = true }\n");
    write("skipped/child/build.gradle.kts", "plugins { `java-library` }\n");
    Path dump = directory.resolve("analysis.properties");
    runner(true, dump).build();
    String sources = load(dump).getProperty("sonar.sources");
    assertThat(sources).contains("settings.gradle.kts", "nested/build.gradle.kts", "nested/library/build.gradle.kts");
    assertThat(sources).doesNotContain("skipped");
    assertThat(runner(true, dump).build().getOutput()).contains("Reusing configuration cache");
  }

  private Properties load(Path path) throws IOException {
    Properties result = new Properties();
    try (InputStream stream = Files.newInputStream(path)) {
      result.load(stream);
    }
    return result;
  }

  @Test
  void rejectsScanAllUntilIsolatedSourceDiscoveryIsSupported() throws IOException {
    write("settings.gradle", "plugins { id 'org.sonarqube.settings' }\nrootProject.name = 'unsupported-scan-all'\n");
    write("build.gradle", "plugins { id 'java-library' }\n" +
      "sonar { properties { property 'sonar.gradle.scanAll', true } }\n");
    Path dump = directory.resolve("analysis.properties");
    BuildResult result = runner(true, dump).buildAndFail();
    assertThat(result.getOutput()).contains("sonar.gradle.scanAll is not yet supported by org.sonarqube.settings");
    assertThat(dump).doesNotExist();
  }

  private String sortedModules(Object value) {
    return Arrays.stream(value.toString().split(",")).sorted().collect(java.util.stream.Collectors.joining(","));
  }

  private void write(String path, String text) throws IOException {
    Path file = directory.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
