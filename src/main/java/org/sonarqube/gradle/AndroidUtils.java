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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.compile.JavaCompile;
import org.sonarqube.gradle.properties.SonarProperty;

import static org.sonarqube.gradle.SonarUtils.appendProps;
import static org.sonarqube.gradle.SonarUtils.appendSourcesProp;

/**
 * Android utilities for SonarQube analysis.
 * <p>
 * This class has <b>zero</b> old-API imports ({@code BaseVariant}, {@code AppExtension}, &hellip;).
 * It reads the output of {@link AndroidConfigCollectorTask} (which collects variant metadata via
 * {@code onVariants} callbacks during configuration) and derives source dirs, binaries, and
 * classpath from compile tasks by name convention at execution time.
 */
class AndroidUtils {
  private static final Logger LOGGER = Logging.getLogger(AndroidUtils.class);

  private AndroidUtils() {
  }

  // no hasLegacyApi() — we use try/catch around legacy calls instead

  // ------------------------------------------------------------------
  //  Public entry-points (called from SonarPropertyComputer / SonarQubePlugin)
  // ------------------------------------------------------------------

  static void configureForAndroid(Project project, @Nullable String userConfiguredBuildVariantName, final Map<String, Object> properties) {
    Optional<AndroidVariantData> opt = readCollectorOutput(project);
    if (!hasUsableCollectorData(opt)) {
      // Collector task produced no usable data (e.g. old AGP where getSources() doesn't exist).
      // Try legacy code path which uses BaseVariant/BaseExtension directly.
      try {
        AndroidUtilsLegacy.configureForAndroid(project, userConfiguredBuildVariantName, properties);
      } catch (Exception | NoClassDefFoundError e) {
        LOGGER.warn("No Android variant data found for '{}'. No android specific configuration will be done", project.getName());
      }
      return;
    }
    AndroidVariantData data = opt.get();

    String selectedVariant = selectVariantName(data, userConfiguredBuildVariantName);
    if (selectedVariant == null) {
      LOGGER.warn("No variant found for '{}'. No android specific configuration will be done", project.getName());
      return;
    }

    boolean isVariantProvidedByUser = selectedVariant.equals(userConfiguredBuildVariantName);

    properties.put(AndroidProperties.ANDROID_DETECTED, true);

    // minSdk
    populateMinSdkProperties(data, selectedVariant, isVariantProvidedByUser, properties);

    boolean isTestPlugin = project.getPlugins().hasPlugin("com.android.test");

    // Main variant sources + binaries
    populateSourcesAndBinariesFromTasks(project, data, selectedVariant, isTestPlugin, properties);

    if (!isTestPlugin) {
      // Test variant sources + binaries
      populateTestSourcesAndBinariesFromTasks(project, data, selectedVariant, properties);
    }

    // Test reports
    configureTestReports(project, selectedVariant, properties);

    // Lint reports
    configureLintReports(project, selectedVariant, properties);
  }

  @Nullable
  static String getSelectedVariantName(Project project, @Nullable String configuredVariant) {
    Optional<AndroidVariantData> opt = readCollectorOutput(project);
    if (hasUsableCollectorData(opt)) {
      return selectVariantName(opt.get(), configuredVariant);
    }
    try {
      return AndroidUtilsLegacy.getSelectedVariantName(project, configuredVariant);
    } catch (Exception | NoClassDefFoundError e) {
      return null;
    }
  }

  public static FileCollection findMainLibraries(Project project) {
    Optional<AndroidVariantData> opt = readCollectorOutput(project);
    if (hasUsableCollectorData(opt)) {
      return findMainLibrariesModern(project, opt.get());
    }
    try {
      return AndroidUtilsLegacy.findMainLibraries(project);
    } catch (Exception | NoClassDefFoundError e) {
      return project.files();
    }
  }

  public static FileCollection findTestLibraries(Project project) {
    Optional<AndroidVariantData> opt = readCollectorOutput(project);
    if (hasUsableCollectorData(opt)) {
      return findTestLibrariesModern(project, opt.get());
    }
    try {
      return AndroidUtilsLegacy.findTestLibraries(project);
    } catch (Exception | NoClassDefFoundError e) {
      return project.files();
    }
  }

  private static FileCollection findMainLibrariesModern(Project project, AndroidVariantData data) {
    String selectedVariant = selectVariantName(data, SonarQubePlugin.getConfiguredAndroidVariant(project));
    if (selectedVariant == null) {
      return project.files();
    }
    FileCollection bootClasspath = getBootClasspath(project);
    JavaCompile javaCompile = findJavaCompileTask(project, "compile" + SonarUtils.capitalize(selectedVariant) + "JavaWithJavac");
    if (javaCompile != null) {
      return bootClasspath.plus(javaCompile.getClasspath());
    }
    return bootClasspath;
  }

  private static FileCollection findTestLibrariesModern(Project project, AndroidVariantData data) {
    String selectedVariant = selectVariantName(data, SonarQubePlugin.getConfiguredAndroidVariant(project));
    if (selectedVariant == null) {
      return project.files();
    }
    String cap = SonarUtils.capitalize(selectedVariant);
    FileCollection bootClasspath = getBootClasspath(project);
    FileCollection result = project.files();

    JavaCompile unitTest = findJavaCompileTask(project, "compile" + cap + "UnitTestJavaWithJavac");
    if (unitTest != null) {
      result = result.plus(bootClasspath).plus(unitTest.getClasspath());
    }
    JavaCompile androidTest = findJavaCompileTask(project, "compile" + cap + "AndroidTestJavaWithJavac");
    if (androidTest != null) {
      result = result.plus(bootClasspath).plus(androidTest.getClasspath());
    }
    return result.isEmpty() ? bootClasspath : result;
  }

  /**
   * Returns true if the collector produced usable data: variants were found AND source dirs were captured.
   * On older AGP (e.g. 7.1) onVariants works but variant.getSources() doesn't exist, so we get
   * variants with empty source dirs — that's not usable, fall back to legacy.
   */
  private static boolean hasUsableCollectorData(Optional<AndroidVariantData> opt) {
    if (opt.isEmpty() || opt.get().variantNames.isEmpty()) {
      return false;
    }
    AndroidVariantData data = opt.get();
    if (data.variantSourceDirs == null || data.variantSourceDirs.isEmpty()) {
      return false;
    }
    // Check that at least one variant has non-empty source dirs
    return data.variantSourceDirs.values().stream().anyMatch(dirs -> dirs != null && !dirs.isEmpty());
  }

  // ------------------------------------------------------------------
  //  Read AndroidConfigCollectorTask output
  // ------------------------------------------------------------------

  static final String EXTRA_PROP_COLLECTOR_TASK = "sonar.android.collectorTask";

  private static Optional<AndroidVariantData> readCollectorOutput(Project project) {
    // First try to get the collector task directly (available when task was eagerly created in same build)
    if (project.getExtensions().getExtraProperties().has(EXTRA_PROP_COLLECTOR_TASK)) {
      AndroidConfigCollectorTask task = (AndroidConfigCollectorTask) project.getExtensions().getExtraProperties().get(EXTRA_PROP_COLLECTOR_TASK);
      // Build the data snapshot from the task's in-memory state (no file I/O needed)
      return task.buildVariantData();
    }
    // Fall back to reading from file (e.g. config cache reuse where task wasn't recreated)
    File outputDir = new File(project.getLayout().getBuildDirectory().getAsFile().get(), "sonar-android-config");
    File outputFile = new File(outputDir, "android-config.json");
    return AndroidConfigCollectorTask.read(outputFile);
  }

  @Nullable
  private static String selectVariantName(AndroidVariantData data, @Nullable String userConfiguredVariant) {
    if (data.variantNames.isEmpty()) {
      return null;
    }
    if (userConfiguredVariant != null) {
      if (data.variantNames.contains(userConfiguredVariant)) {
        return userConfiguredVariant;
      }
      throw new IllegalArgumentException("Unable to find variant '" + userConfiguredVariant +
        "' to use for SonarQube analysis. Candidates are: " + String.join(", ", data.variantNames));
    }
    // Prefer test build type variant (usually "debug")
    if (data.testBuildType != null) {
      for (String name : data.variantNames) {
        String buildType = data.variantBuildTypes.get(name);
        if (data.testBuildType.equals(buildType)) {
          LOGGER.info("No variant name specified to be used by SonarQube. Default to '{}'", name);
          return name;
        }
      }
    }
    String first = data.variantNames.get(0);
    LOGGER.info("No variant name specified to be used by SonarQube. Default to '{}'", first);
    return first;
  }

  // ------------------------------------------------------------------
  //  Populate properties from compile tasks
  // ------------------------------------------------------------------

  private static void populateMinSdkProperties(AndroidVariantData data, String selectedVariant,
    boolean isVariantProvidedByUser, Map<String, Object> properties) {
    if (isVariantProvidedByUser) {
      Integer minSdk = data.variantMinSdks.get(selectedVariant);
      if (minSdk != null) {
        properties.put(AndroidProperties.MIN_SDK_VERSION_MIN, minSdk);
        properties.put(AndroidProperties.MIN_SDK_VERSION_MAX, minSdk);
      }
    } else {
      if (!data.allMinSdks.isEmpty()) {
        properties.put(AndroidProperties.MIN_SDK_VERSION_MIN, Collections.min(data.allMinSdks));
        properties.put(AndroidProperties.MIN_SDK_VERSION_MAX, Collections.max(data.allMinSdks));
      }
    }
  }

  private static void populateSourcesAndBinariesFromTasks(Project project, AndroidVariantData data, String variantName,
    boolean isTest, Map<String, Object> properties) {
    String capitalizedVariant = SonarUtils.capitalize(variantName);
    String compileTaskName = "compile" + capitalizedVariant + "JavaWithJavac";

    JavaCompile javaCompile = findJavaCompileTask(project, compileTaskName);
    if (javaCompile == null) {
      LOGGER.warn("Unable to find Java compile task '{}' for variant '{}'. SonarQube analysis will be less accurate without bytecode.",
        compileTaskName, variantName);
    } else {
      SonarUtils.populateJdkProperties(properties, JavaCompilerUtils.extractConfiguration(javaCompile));
    }

    Collection<File> destinationDirs = (javaCompile != null)
      ? Collections.singleton(javaCompile.getDestinationDirectory().getAsFile().get())
      : Collections.emptySet();

    if (isTest) {
      appendProps(properties, SonarProperty.JAVA_TEST_BINARIES, destinationDirs);
    } else {
      appendProps(properties, SonarProperty.JAVA_BINARIES, destinationDirs);
      appendProps(properties, SonarProperty.BINARIES, destinationDirs);
    }

    // Source dirs from collected variant data
    List<File> srcDirs = getSourceDirsFromData(data, variantName);
    if (!srcDirs.isEmpty()) {
      appendSourcesProp(properties, srcDirs, isTest);
    }
  }

  private static void populateTestSourcesAndBinariesFromTasks(Project project, AndroidVariantData data, String variantName, Map<String, Object> properties) {
    String capitalizedVariant = SonarUtils.capitalize(variantName);

    // Unit test variant
    JavaCompile unitTestCompile = findJavaCompileTask(project, "compile" + capitalizedVariant + "UnitTestJavaWithJavac");
    if (unitTestCompile != null) {
      appendProps(properties, SonarProperty.JAVA_TEST_BINARIES,
        Collections.singleton(unitTestCompile.getDestinationDirectory().getAsFile().get()));
    }
    // Test source dirs by convention (test variants aren't in onVariants data)
    List<File> unitTestSrcDirs = getTestSourceDirs(project, "test");
    if (!unitTestSrcDirs.isEmpty()) {
      appendSourcesProp(properties, unitTestSrcDirs, true);
    }

    // Android test (instrumented) variant
    JavaCompile androidTestCompile = findJavaCompileTask(project, "compile" + capitalizedVariant + "AndroidTestJavaWithJavac");
    if (androidTestCompile != null) {
      appendProps(properties, SonarProperty.JAVA_TEST_BINARIES,
        Collections.singleton(androidTestCompile.getDestinationDirectory().getAsFile().get()));
    }
    List<File> androidTestSrcDirs = getTestSourceDirs(project, "androidTest");
    if (!androidTestSrcDirs.isEmpty()) {
      appendSourcesProp(properties, androidTestSrcDirs, true);
    }
  }

  /**
   * Extract source directories from a JavaCompile task's source set.
   * Returns the source directories (not individual files), plus sibling res/manifest dirs.
   */
  /**
   * Get test source directories from the variant data or by convention.
   * Test variants (UnitTest, AndroidTest) are NOT collected by onVariants.
   * We look them up by convention from the project directory.
   */
  private static List<File> getTestSourceDirs(Project project, String testSourceSetName) {
    File projectDir = project.getProjectDir();
    List<File> result = new ArrayList<>();
    // Standard test source set: src/test/java, src/test/resources
    File javaDir = new File(projectDir, "src/" + testSourceSetName + "/java");
    if (javaDir.isDirectory()) {
      result.add(javaDir);
    }
    File kotlinDir = new File(projectDir, "src/" + testSourceSetName + "/kotlin");
    if (kotlinDir.isDirectory()) {
      result.add(kotlinDir);
    }
    File resourcesDir = new File(projectDir, "src/" + testSourceSetName + "/resources");
    if (resourcesDir.isDirectory()) {
      result.add(resourcesDir);
    }
    File manifest = new File(projectDir, "src/" + testSourceSetName + "/AndroidManifest.xml");
    if (manifest.isFile()) {
      result.add(manifest);
    }
    return result;
  }

  private static List<File> getSourceDirsFromData(AndroidVariantData data, String variantName) {
    if (data.variantSourceDirs == null) {
      return Collections.emptyList();
    }
    List<String> paths = data.variantSourceDirs.get(variantName);
    if (paths == null || paths.isEmpty()) {
      return Collections.emptyList();
    }
    List<File> result = new ArrayList<>();
    for (String path : paths) {
      File dir = new File(path);
      result.add(dir);
      // For each java/kotlin source dir, also add sibling res dir and AndroidManifest.xml.
      // e.g. src/main/java -> also add src/main/res and src/main/AndroidManifest.xml
      File parent = dir.getParentFile();
      if (parent != null && (dir.getName().equals("java") || dir.getName().equals("kotlin"))) {
        File resDir = new File(parent, "res");
        if (resDir.isDirectory() && !result.contains(resDir)) {
          result.add(resDir);
        }
        File manifest = new File(parent, "AndroidManifest.xml");
        if (manifest.isFile() && !result.contains(manifest)) {
          result.add(manifest);
        }
      }
    }
    return result;
  }

  private static void configureTestReports(Project project, String variantName, Map<String, Object> properties) {
    String capitalizedVariant = SonarUtils.capitalize(variantName);
    File testResultsDir = new File(project.getLayout().getBuildDirectory().getAsFile().get(),
      "test-results/test" + capitalizedVariant + "UnitTest");
    // Always set the path — existence isn't guaranteed during config phase (tests may not have run yet)
    List<File> dirs = new ArrayList<>();
    dirs.add(testResultsDir);
    properties.put(SonarProperty.JUNIT_REPORT_PATHS, dirs);
  }

  private static void configureLintReports(Project project, String variantName, Map<String, Object> properties) {
    File lintReport = new File(project.getLayout().getBuildDirectory().getAsFile().get(),
      "reports/lint-results-" + variantName + ".xml");
    if (lintReport.exists()) {
      properties.put(SonarProperty.ANDROID_LINT_REPORT_PATHS, lintReport);
    }
  }

  @Nullable
  private static JavaCompile findJavaCompileTask(Project project, String taskName) {
    try {
      Object task = project.getTasks().getByName(taskName);
      if (task instanceof JavaCompile) {
        return (JavaCompile) task;
      }
    } catch (Exception e) {
      // Task not found
    }
    return null;
  }

  // ------------------------------------------------------------------
  //  Boot classpath from collector output
  // ------------------------------------------------------------------

  private static FileCollection getBootClasspath(Project project) {
    return readCollectorOutput(project)
      .map(data -> {
        if (data.bootClasspath != null && !data.bootClasspath.isEmpty()) {
          List<File> files = data.bootClasspath.stream().map(File::new).collect(Collectors.toList());
          return project.files(files);
        }
        return project.files();
      })
      .orElse(project.files());
  }
}
