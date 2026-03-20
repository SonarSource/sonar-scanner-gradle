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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Gradle task that collects Android variant metadata during execution and writes it to a JSON file.
 * <p>
 * During the <strong>configuration phase</strong>, {@code onVariants} callbacks populate this task
 * via {@link #addVariant} and {@link #addVariantSourceProvider}.
 * During the <strong>execution phase</strong>, the task resolves all lazy providers and serialises
 * an {@link AndroidVariantData} snapshot.
 */
public abstract class AndroidConfigCollectorTask extends DefaultTask {
  public static final String TASK_NAME = "sonarCollectAndroidConfig";
  public static final String TASK_DESCRIPTION = "Collects Android variant configuration for SonarQube analysis.";

  private static final Logger LOGGER = Logger.getLogger(AndroidConfigCollectorTask.class.getName());
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  // ---- mutable collectors filled during configuration ----
  private final List<String> variantNames = new ArrayList<>();
  private final Map<String, String> variantBuildTypes = new LinkedHashMap<>();
  private final Map<String, Integer> variantMinSdks = new LinkedHashMap<>();
  private final List<Integer> allMinSdks = new ArrayList<>();
  @Nullable
  private String testBuildType;
  private File outputDirectory;

  /** Lazy source-dir providers per variant, resolved at execution time. */
  private final Map<String, List<Provider<?>>> variantSourceProviders = new LinkedHashMap<>();

  /** Lazy boot classpath provider, set during config, resolved at execution time. */
  @Nullable
  private Provider<List<File>> bootClasspathProvider;

  @Inject
  public AndroidConfigCollectorTask() {
    super();
    this.getOutputs().upToDateWhen(task -> false);
  }

  // ---- configuration-phase API ----

  public void addVariant(String name, @Nullable String buildType, @Nullable Integer minSdk) {
    variantNames.add(name);
    if (buildType != null) {
      variantBuildTypes.put(name, buildType);
    }
    if (minSdk != null) {
      variantMinSdks.put(name, minSdk);
      if (!allMinSdks.contains(minSdk)) {
        allMinSdks.add(minSdk);
      }
    }
  }

  /**
   * Add a lazy source directory provider for a variant.
   * The provider may return {@code List<Directory>} (flat — e.g. java, aidl) or
   * {@code List<List<Directory>>} (layered — e.g. res, assets). Both are handled at execution time.
   */
  public void addVariantSourceProvider(String variantName, Provider<?> provider) {
    variantSourceProviders.computeIfAbsent(variantName, k -> new ArrayList<>()).add(provider);
  }

  public void addMinSdk(int minSdk) {
    if (!allMinSdks.contains(minSdk)) {
      allMinSdks.add(minSdk);
    }
  }

  public void setTestBuildType(@Nullable String testBuildType) {
    this.testBuildType = testBuildType;
  }

  public void setBootClasspathProvider(Provider<List<File>> provider) {
    this.bootClasspathProvider = provider;
  }

  public void setOutputDirectory(File outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  // ---- inputs / outputs ----

  @Input
  public List<String> getVariantNames() {
    return variantNames;
  }

  @Internal
  public Map<String, String> getVariantBuildTypes() {
    return variantBuildTypes;
  }

  @Internal
  public Map<String, Integer> getVariantMinSdks() {
    return variantMinSdks;
  }

  @Internal
  public List<Integer> getAllMinSdks() {
    return allMinSdks;
  }

  @Internal
  @Nullable
  public String getTestBuildType() {
    return testBuildType;
  }

  @OutputFile
  public File getOutputFile() {
    return new File(outputDirectory, "android-config.json");
  }

  // ---- execution ----

  @TaskAction
  void run() throws IOException {
    if (variantNames.isEmpty()) {
      LOGGER.info("No Android variants collected – skipping.");
      Files.deleteIfExists(getOutputFile().toPath());
      return;
    }

    // Resolve source directory providers
    Map<String, List<String>> resolvedSourceDirs = resolveSourceDirs();

    // Resolve boot classpath
    List<String> bootClasspath = resolveBootClasspath();

    AndroidVariantData data = new AndroidVariantData(
      new ArrayList<>(variantNames),
      new LinkedHashMap<>(variantBuildTypes),
      new LinkedHashMap<>(variantMinSdks),
      new ArrayList<>(allMinSdks),
      testBuildType,
      resolvedSourceDirs,
      bootClasspath
    );

    try (FileWriter writer = new FileWriter(getOutputFile(), StandardCharsets.UTF_8)) {
      GSON.toJson(data, writer);
    }

    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.info("Wrote Android config for " + variantNames.size() + " variant(s) to " + getOutputFile());
    }
  }

  private Map<String, List<String>> resolveSourceDirs() {
    Map<String, List<String>> result = new LinkedHashMap<>();
    for (Map.Entry<String, List<Provider<?>>> entry : variantSourceProviders.entrySet()) {
      List<String> dirs = new ArrayList<>();
      for (Provider<?> provider : entry.getValue()) {
        try {
          Object value = provider.getOrNull();
          if (value != null) {
            collectDirectoryPaths(value, dirs);
          }
        } catch (Exception e) {
          LOGGER.fine("Could not resolve source provider for variant " + entry.getKey() + ": " + e.getMessage());
        }
      }
      result.put(entry.getKey(), dirs);
    }
    return result;
  }

  /**
   * Recursively extract Directory paths from the resolved provider value.
   * Handles:
   * - {@code List<Directory>} (flat, from SourceDirectories.getAll())
   * - {@code List<List<Directory>>} (layered, from LayeredSourceDirectories.getAll())
   * - Single {@code Directory}
   */
  private static void collectDirectoryPaths(Object value, List<String> paths) {
    if (value instanceof Directory) {
      String path = ((Directory) value).getAsFile().getAbsolutePath();
      if (!paths.contains(path)) {
        paths.add(path);
      }
    } else if (value instanceof Iterable) {
      for (Object item : (Iterable<?>) value) {
        collectDirectoryPaths(item, paths);
      }
    }
  }

  private List<String> resolveBootClasspath() {
    if (bootClasspathProvider == null) {
      return new ArrayList<>();
    }
    List<File> files = bootClasspathProvider.getOrNull();
    if (files == null || files.isEmpty()) {
      return new ArrayList<>();
    }
    return files.stream()
      .map(File::getAbsolutePath)
      .collect(Collectors.toList());
  }

  // ---- in-memory access (used during same build, before task execution) ----

  /**
   * Build variant data from in-memory state. Resolves source directory and boot classpath providers
   * eagerly. Safe to call during execution phase or late configuration.
   */
  public Optional<AndroidVariantData> buildVariantData() {
    if (variantNames.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new AndroidVariantData(
      new ArrayList<>(variantNames),
      new LinkedHashMap<>(variantBuildTypes),
      new LinkedHashMap<>(variantMinSdks),
      new ArrayList<>(allMinSdks),
      testBuildType,
      resolveSourceDirs(),
      resolveBootClasspath()
    ));
  }

  // ---- reading back from file ----

  public static Optional<AndroidVariantData> read(File input) {
    if (!input.exists()) {
      return Optional.empty();
    }
    try (FileReader reader = new FileReader(input, StandardCharsets.UTF_8)) {
      return Optional.ofNullable(GSON.fromJson(reader, AndroidVariantData.class));
    } catch (IOException e) {
      LOGGER.warning("Could not read Android config from " + input + ": " + e.getMessage());
      return Optional.empty();
    }
  }
}
