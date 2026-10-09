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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SonarMetadataTest {
  @TempDir Path tempDir;

  @Test
  void aggregatesNestedModulesAndSkipsTheirChildren() {
    SonarProjectMetadata root = metadata(":", false, Map.of("sonar.projectKey", "acme:main", "sonar.sources", ""), List.of());
    SonarProjectMetadata child = metadata(":app", false, Map.of("sonar.sources", "src", "sonar.projectName", "app"), List.of("sonar.sources"));
    SonarProjectMetadata nested = metadata(":app:core", false, Map.of("sonar.tests", "test"), List.of());
    SonarProjectMetadata skipped = metadata(":other", true, Map.of(), List.of());
    SonarProjectMetadata skippedDescendant = metadata(":other:leaf", false, Map.of("sonar.sources", "leaf"), List.of());

    ComputedProperties result = SonarPropertyComputer.aggregateIsolatedProperties(
      List.of(root, child, nested, skipped, skippedDescendant), ":");

    assertThat(result.properties).containsEntry("sonar.modules", ":app")
      .containsEntry(":app.sonar.moduleKey", "acme:main:app")
      .containsEntry(":app.sonar.modules", ":app:core")
      .containsEntry(":app.:app:core.sonar.moduleKey", "acme:main:app:core")
      .doesNotContainKey(":other.sonar.sources")
      .doesNotContainKey(":other:leaf.sonar.sources");
    assertThat(result.userDefinedKeys).contains(":app.sonar.sources");
  }

  @Test
  void roundTripsMetadataAndAddsKotlinBuildScripts() throws Exception {
    SonarProjectMetadata root = metadata(":", false, Map.of("sonar.projectKey", "main", "sonar.sources", "src"), List.of());
    SonarProjectMetadata child = metadata(":lib", false, Map.of("sonar.sources", ""), List.of());
    File file = tempDir.resolve("metadata.json").toFile();
    SonarMetadataSerializer.write(file, child);

    SonarProjectMetadata restored = SonarMetadataSerializer.read(file);
    ComputedProperties result = SonarPropertyComputer.aggregateIsolatedProperties(List.of(root, restored), ":");

    assertThat(restored.projectPath).isEqualTo(":lib");
    assertThat(result.properties.get("sonar.sources").toString()).contains("src", "settings.gradle.kts", "build.gradle.kts");
  }

  @Test
  void metadataTaskWritesBothPropertyViewsAndAnalysisFlags() throws Exception {
    Project project = ProjectBuilder.builder().withName("module").withProjectDir(tempDir.toFile()).build();
    SonarMetadataTask task = project.getTasks().create("sonarMetadata", SonarMetadataTask.class);
    File output = tempDir.resolve("nested/metadata.json").toFile();
    task.getProjectPath().set(":module");
    task.getSkipped().set(true);
    task.getProperties().set(Map.of("sonar.sources", "module-src"));
    task.getUserDefinedKeys().set(List.of("sonar.sources"));
    task.getRootProperties().set(Map.of("sonar.sources", "owner-src"));
    task.getRootUserDefinedKeys().set(List.of("sonar.projectName"));
    task.getDefaultProjectKey().set("org:root:module");
    task.getScanAllSourcesOverridden().set(true);
    task.getProjectDirectory().set(tempDir.toString());
    task.getBuildFile().set(tempDir.resolve("build.gradle").toString());
    task.getMetadataFile().set(output);

    task.writeMetadata();
    SonarProjectMetadata restored = SonarMetadataSerializer.read(output);

    assertThat(restored.version).isEqualTo(3);
    assertThat(restored.projectPath).isEqualTo(":module");
    assertThat(restored.skipped).isTrue();
    assertThat(restored.properties).containsEntry("sonar.sources", "module-src");
    assertThat(restored.userDefinedKeys).containsExactly("sonar.sources");
    assertThat(restored.rootProperties).containsEntry("sonar.sources", "owner-src");
    assertThat(restored.rootUserDefinedKeys).containsExactly("sonar.projectName");
    assertThat(restored.defaultProjectKey).isEqualTo("org:root:module");
    assertThat(restored.scanAllSourcesOverridden).isTrue();
    assertThat(restored.projectDirectory).isEqualTo(tempDir.toString());
    assertThat(restored.buildFile).isEqualTo(tempDir.resolve("build.gradle").toString());
  }

  @Test
  void serializerRejectsMalformedOrIncompleteMetadataAndUnwritableParent() throws Exception {
    Path invalid = tempDir.resolve("invalid.json");
    Files.writeString(invalid, "{");
    assertThatThrownBy(() -> SonarMetadataSerializer.read(invalid.toFile()))
      .isInstanceOf(java.io.IOException.class).hasMessageContaining("Invalid Sonar project metadata");

    Files.writeString(invalid, "{\"version\":2,\"projectPath\":\":\"}");
    assertThatThrownBy(() -> SonarMetadataSerializer.read(invalid.toFile()))
      .isInstanceOf(java.io.IOException.class).hasMessageContaining("Invalid Sonar project metadata");

    Path parentFile = tempDir.resolve("parent-file");
    Files.writeString(parentFile, "occupied");
    SonarProjectMetadata metadata = builder(":", tempDir).build();
    assertThatThrownBy(() -> SonarMetadataSerializer.write(parentFile.resolve("metadata.json").toFile(), metadata))
      .isInstanceOf(java.io.IOException.class).hasMessageContaining("Cannot create Sonar metadata directory");
  }

  @Test
  void directChildAnalysisUsesItsRootViewAndGradleRootIdentity() {
    SonarProjectMetadata gradleRoot = builder(":", tempDir.resolve("root"))
      .defaultProjectKey("org:root").build();
    SonarProjectMetadata child = builder(":child", tempDir.resolve("child"))
      .properties(Map.of("sonar.sources", "module-src"), List.of())
      .rootProperties(Map.of("sonar.sources", "owner-src", "sonar.working.directory", "build/sonar"), List.of("sonar.sources"))
      .build();
    SonarProjectMetadata nested = metadata(":child:nested", false, Map.of("sonar.sources", "nested-src"), List.of());

    ComputedProperties result = SonarPropertyComputer.aggregateIsolatedProperties(List.of(gradleRoot, child, nested), ":child");

    assertThat(result.properties).containsEntry("sonar.projectKey", "org:root:child")
      .containsEntry("sonar.kotlin.gradleProjectRoot", tempDir.resolve("root").toString())
      .containsEntry(":child:nested.sonar.moduleKey", "org:root:child:child:nested")
      .doesNotContainKey("sonar.working.directory.sonar.sources")
      .containsEntry("sonar.sources", "owner-src");
    assertThat(result.userDefinedKeys).contains("sonar.sources");
  }

  @Test
  void scanAllHonorsExplicitSourcesEvenWhenValueMatchesTheDefault() throws Exception {
    Path projectDir = tempDir.resolve("scan-all");
    Files.createDirectories(projectDir);
    Files.writeString(projectDir.resolve("extra.txt"), "content");
    Map<String, String> properties = Map.of("sonar.projectKey", "root", "sonar.sources", "src", "sonar.gradle.scanAll", "true");
    SonarProjectMetadata root = builder(":", projectDir)
      .properties(properties, List.of()).rootProperties(properties, List.of())
      .defaultProjectKey("root").scanAllSourcesOverridden(true).build();

    ComputedProperties result = SonarPropertyComputer.aggregateIsolatedProperties(List.of(root), ":");

    assertThat(result.properties.get("sonar.sources").toString()).doesNotContain("extra.txt");
  }

  @Test
  void globalOverridesControlScanAllBeforeCollection() throws Exception {
    Path projectDir = tempDir.resolve("global-scan-all");
    Files.createDirectories(projectDir);
    Files.writeString(projectDir.resolve("extra.txt"), "content");
    Map<String, String> properties = Map.of("sonar.projectKey", "root", "sonar.sources", "src");
    SonarProjectMetadata root = builder(":", projectDir)
      .properties(properties, List.of()).rootProperties(properties, List.of())
      .defaultProjectKey("root").build();

    ComputedProperties result = SonarPropertyComputer.aggregateIsolatedProperties(List.of(root), ":",
      Map.of("sonar.gradle.scanAll", "true", "sonar.sources", "configured"));

    assertThat(result.properties).containsEntry("sonar.sources", "configured")
      .containsEntry("sonar.gradle.scanAll", "true");
    assertThat(result.userDefinedKeys).contains("sonar.sources", "sonar.gradle.scanAll");
  }

  @Test
  void scanAllCollectsUnconfiguredSourcesButIgnoresSkippedProjectDirectories() throws Exception {
    Path rootDir = tempDir.resolve("scan-all-with-skipped-module");
    Path skippedDir = rootDir.resolve("skipped");
    Files.createDirectories(skippedDir);
    Files.writeString(rootDir.resolve("extra.txt"), "analyze");
    Files.writeString(skippedDir.resolve("ignored.txt"), "skip");
    Map<String, String> properties = Map.of("sonar.projectKey", "root", "sonar.projectBaseDir", rootDir.toString(),
      "sonar.sources", "", "sonar.gradle.scanAll", "true");
    SonarProjectMetadata root = builder(":", rootDir)
      .properties(properties, List.of()).rootProperties(properties, List.of())
      .defaultProjectKey("root").build();
    SonarProjectMetadata skipped = builder(":skipped", skippedDir).skipped(true).build();

    ComputedProperties result = SonarPropertyComputer.aggregateIsolatedProperties(List.of(root, skipped), ":");

    assertThat(result.properties.get("sonar.sources").toString()).contains("extra.txt").doesNotContain("ignored.txt");
    assertThat(result.properties).doesNotContainKey("sonar.modules");
  }

  @Test
  void nestedAnalysisExcludesSiblingMetadataAndUsesGlobalOverride() {
    SonarProjectMetadata root = builder(":", tempDir.resolve("root"))
      .properties(Map.of("sonar.projectKey", "main"), List.of())
      .rootProperties(Map.of("sonar.projectKey", "main"), List.of())
      .defaultProjectKey("main").build();
    SonarProjectMetadata app = metadata(":app", false, Map.of("sonar.projectName", "app"), List.of());
    SonarProjectMetadata child = metadata(":app:core", false, Map.of("sonar.projectName", "core"), List.of());
    SonarProjectMetadata sibling = metadata(":other", false, Map.of("sonar.projectName", "other"), List.of());

    ComputedProperties result = SonarPropertyComputer.aggregateIsolatedProperties(
      List.of(root, app, child, sibling), ":app", Map.of("sonar.projectName", "overridden"));

    assertThat(result.properties).containsEntry("sonar.projectName", "overridden")
      .containsEntry("sonar.modules", ":app:core")
      .doesNotContainKey(":other.sonar.projectName");
    assertThat(result.userDefinedKeys).contains("sonar.projectName");
  }

  @Test
  void globalProjectKeyOverrideAlsoControlsModuleKeys() {
    SonarProjectMetadata root = metadata(":", false, Map.of("sonar.projectKey", "configured"), List.of());
    SonarProjectMetadata child = metadata(":child", false, Map.of("sonar.projectName", "child"), List.of());

    ComputedProperties result = SonarPropertyComputer.aggregateIsolatedProperties(
      List.of(root, child), ":", Map.of("sonar.projectKey", "overridden"));

    assertThat(result.properties).containsEntry("sonar.projectKey", "overridden")
      .containsEntry(":child.sonar.moduleKey", "overridden:child");
  }

  @Test
  void missingAndSkippedAnalysisRootsAreHandledExplicitly() {
    SonarProjectMetadata root = metadata(":", false, Map.of("sonar.projectKey", "main"), List.of());
    SonarProjectMetadata skipped = metadata(":skipped", true, Map.of("sonar.projectName", "skipped"), List.of());
    List<SonarProjectMetadata> onlyRoot = List.of(root);

    assertThatThrownBy(() -> SonarPropertyComputer.aggregateIsolatedProperties(onlyRoot, ":missing"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Missing Sonar metadata for analysis project :missing");
    assertThat(SonarPropertyComputer.aggregateIsolatedProperties(List.of(root, skipped), ":skipped").properties).isEmpty();
  }

  @Test
  void serializedMetadataOmitsSystemAndDslCredentials() throws Exception {
    String previous = System.getProperty("sonar.token");
    System.setProperty("sonar.token", "system-secret-value");
    try {
      Project project = ProjectBuilder.builder().withName("root").withProjectDir(tempDir.toFile()).build();
      ActionBroadcast<SonarProperties> actions = new ActionBroadcast<>();
      project.getExtensions().create("sonar", SonarExtension.class, actions);
      project.getExtensions().create("sonarqube", SonarExtension.class, actions);
      actions.add(properties -> {
        properties.property("sonar.login", "dsl-secret-value");
        properties.property("sonar.host.url", "https://example.test");
        properties.property("sonar.cpd.java.minimumTokens", "100");
      });
      SonarPropertyComputer computer = new SonarPropertyComputer(Map.of(":", actions), new HashMap<>(), project);
      ComputedProperties computed = computer.computeLocalSonarProperties(true);
      Map<String, String> values = computed.properties.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> (String) entry.getValue()));
      File file = tempDir.resolve("secrets-metadata.json").toFile();
      SonarMetadataSerializer.write(file, builder(":", tempDir)
        .properties(values, new ArrayList<>(computed.userDefinedKeys))
        .rootProperties(values, new ArrayList<>(computed.userDefinedKeys))
        .defaultProjectKey("root").build());

      assertThat(Files.readString(file.toPath())).doesNotContain("system-secret-value", "dsl-secret-value", "sonar.token", "sonar.login");
      assertThat(computer.computeLocalSensitiveProperties()).containsEntry("sonar.login", "dsl-secret-value")
        .doesNotContainKey("sonar.token");
      assertThat(values).containsEntry("sonar.host.url", "https://example.test");
      assertThat(values).containsEntry("sonar.cpd.java.minimumTokens", "100");
      assertThat(Files.readString(file.toPath())).contains("sonar.cpd.java.minimumTokens");
    } finally {
      if (previous == null) {
        System.clearProperty("sonar.token");
      } else {
        System.setProperty("sonar.token", previous);
      }
    }
  }

  @Test
  void filtersOnlyKnownCredentialProperties() {
    assertThat(SonarPropertyComputer.isSensitiveProperty("sonar.token")).isTrue();
    assertThat(SonarPropertyComputer.isSensitiveProperty("sonar.scanner.proxyPassword")).isTrue();
    assertThat(SonarPropertyComputer.isSensitiveProperty("sonar.scanner.truststorePassword")).isTrue();
    assertThat(SonarPropertyComputer.isSensitiveProperty("sonar.scanner.keystorePassword")).isTrue();
    assertThat(SonarPropertyComputer.isSensitiveProperty("sonar.cpd.java.minimumTokens")).isFalse();
  }

  private SonarProjectMetadata metadata(String path, boolean skipped, Map<String, String> properties, List<String> userDefinedKeys) {
    Path directory = tempDir.resolve(path.equals(":") ? "root" : path.substring(1).replace(':', '/'));
    return SonarProjectMetadata.builder(path, directory.toString(), directory.resolve("build.gradle.kts").toString())
      .skipped(skipped).properties(properties, userDefinedKeys).rootProperties(properties, userDefinedKeys)
      .defaultProjectKey(path.equals(":") ? properties.getOrDefault("sonar.projectKey", "root") : "").build();
  }

  private static SonarProjectMetadata.Builder builder(String path, Path directory) {
    return SonarProjectMetadata.builder(path, directory.toString(), directory.resolve("build.gradle").toString());
  }
}
