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
 * Variant data is collected eagerly during configuration into a {@link SharedCollector} (via
 * {@code onVariants} callbacks). The task itself is registered lazily; at execution time it resolves
 * lazy providers (source dirs, boot classpath) and serialises an {@link AndroidVariantData} snapshot.
 */
public abstract class AndroidConfigCollectorTask extends DefaultTask {
  public static final String TASK_NAME = "sonarCollectAndroidConfig";
  public static final String TASK_DESCRIPTION = "Collects Android variant configuration for SonarQube analysis.";

  private static final Logger LOGGER = Logger.getLogger(AndroidConfigCollectorTask.class.getName());
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  @Nullable
  private SharedCollector sharedCollector;
  private final List<Integer> allMinSdks = new ArrayList<>();
  @Nullable
  private String testBuildType;
  @Nullable
  private Provider<List<File>> bootClasspathProvider;
  private File outputDirectory;

  @Inject
  public AndroidConfigCollectorTask() {
    super();
    this.getOutputs().upToDateWhen(task -> false);
  }

  // ---- configuration-phase API (called from task registration lambda) ----

  public void setSharedCollector(SharedCollector collector) {
    this.sharedCollector = collector;
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
    return sharedCollector != null ? sharedCollector.variantNames : List.of();
  }

  @OutputFile
  public File getOutputFile() {
    return new File(outputDirectory, "android-config.json");
  }

  // ---- execution ----

  @TaskAction
  void run() throws IOException {
    if (sharedCollector == null || sharedCollector.variantNames.isEmpty()) {
      LOGGER.info("No Android variants collected – skipping.");
      Files.deleteIfExists(getOutputFile().toPath());
      return;
    }

    Map<String, List<String>> resolvedSourceDirs = sharedCollector.resolveSourceDirs();
    List<String> bootClasspath = resolveBootClasspath();

    // Merge allMinSdks from shared collector (per-variant) and from task (DSL-level)
    List<Integer> mergedMinSdks = new ArrayList<>(sharedCollector.allMinSdks);
    for (Integer sdk : allMinSdks) {
      if (!mergedMinSdks.contains(sdk)) {
        mergedMinSdks.add(sdk);
      }
    }

    AndroidVariantData data = new AndroidVariantData(
      new ArrayList<>(sharedCollector.variantNames),
      new LinkedHashMap<>(sharedCollector.variantBuildTypes),
      new LinkedHashMap<>(sharedCollector.variantMinSdks),
      mergedMinSdks,
      testBuildType,
      resolvedSourceDirs,
      bootClasspath
    );

    try (FileWriter writer = new FileWriter(getOutputFile(), StandardCharsets.UTF_8)) {
      GSON.toJson(data, writer);
    }

    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.info("Wrote Android config for " + sharedCollector.variantNames.size() + " variant(s) to " + getOutputFile());
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

  // ---- Shared mutable state populated eagerly during config, before task is realized ----

  /**
   * Holds variant metadata collected by {@code onVariants} callbacks during configuration phase.
   * This object is created eagerly (before task registration) so that {@code onVariants} can
   * populate it. It is also stored in project extra properties so {@link AndroidUtils} can
   * access the data in-memory without waiting for the task to execute and write a file.
   */
  public static class SharedCollector {
    final List<String> variantNames = new ArrayList<>();
    final Map<String, String> variantBuildTypes = new LinkedHashMap<>();
    final Map<String, Integer> variantMinSdks = new LinkedHashMap<>();
    final List<Integer> allMinSdks = new ArrayList<>();
    private final Map<String, List<Provider<?>>> variantSourceProviders = new LinkedHashMap<>();

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

    public void addVariantSourceProvider(String variantName, Provider<?> provider) {
      variantSourceProviders.computeIfAbsent(variantName, k -> new ArrayList<>()).add(provider);
    }

    /**
     * Build an {@link AndroidVariantData} snapshot, resolving source dir providers eagerly.
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
        null, // testBuildType not available here (read later from DSL)
        resolveSourceDirs(),
        new ArrayList<>() // boot classpath not available here (resolved in task)
      ));
    }

    Map<String, List<String>> resolveSourceDirs() {
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
  }
}
