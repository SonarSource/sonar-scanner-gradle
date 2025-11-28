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

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.DynamicFeaturePlugin;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.TestExtension;
import com.android.build.gradle.TestPlugin;
import com.android.build.gradle.api.ApkVariant;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.api.UnitTestVariant;
import com.android.build.gradle.internal.api.TestedVariant;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.lint.AndroidLintTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.tasks.factory.AndroidUnitTest;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.SourceProvider;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.PluginCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.util.GradleVersion;
import org.sonarqube.gradle.properties.SonarProperty;

import static com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION;
import static org.sonarqube.gradle.SonarQubePlugin.getConfiguredAndroidVariant;
import static org.sonarqube.gradle.SonarUtils.appendProps;
import static org.sonarqube.gradle.SonarUtils.appendSourcesProp;

/**
 * Only access this class when running on an Android application
 */
class AndroidUtils {
  private static final Logger LOGGER = Logging.getLogger(AndroidUtils.class);
  private static final String SONAR_ANDROID_LINT_REPORT_PATHS_PROP = SonarProperty.ANDROID_LINT_REPORT_PATHS;

  private AndroidUtils() {
  }

  static void configureForAndroid(Project project, @Nullable String userConfiguredBuildVariantName, final Map<String, Object> properties) {
    AndroidVariantAndExtension android = findVariantAndExtension(project, userConfiguredBuildVariantName);
    if (android != null && android.getVariant() != null) {
      configureForAndroid(project, android, properties);
    } else {
      LOGGER.warn("No variant found for '{}'. No android specific configuration will be done", project.getName());
    }
  }

  static Version getAndroidPluginVersion() {
    return Version.of(ANDROID_GRADLE_PLUGIN_VERSION);
  }

  /**
   * Get the variants used for testing for a given variant.
   */
  public static List<BaseVariant> getTestVariants(BaseVariant variant) {
    if (variant instanceof TestedVariant) {
      TestedVariant testedVariant = (TestedVariant) variant;
      return Stream.of(
          // Local tests
          testedVariant.getUnitTestVariant(),
          // Instrumentation tests
          testedVariant.getTestVariant())
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    }
    return Collections.emptyList();
  }

  private static void configureForAndroid(Project project, AndroidVariantAndExtension android, Map<String, Object> properties) {
    BaseVariant variant = android.getVariant();

    populateSonarQubeAndroidProperties(android, properties);

    configureTestReports(project, variant, properties);
    configureLintReports(project, variant, properties);
    if (project.getPlugins().hasPlugin("com.android.test")) {
      // Instrumentation tests only
      populateSonarQubeProps(properties, variant, true);
      return;
    }
    populateSonarQubeProps(properties, variant, false);
    getTestVariants(variant)
      .forEach(testVariant -> populateSonarQubeProps(properties, testVariant, true));
  }

  private static void populateSonarQubeAndroidProperties(AndroidVariantAndExtension android, Map<String, Object> properties) {
    properties.put(AndroidProperties.ANDROID_DETECTED, true);

    if (!isMinSdkSupported()) {
      return;
    }

    if (android.isVariantProvidedByUser()) {
      ApiVersion minSdkVersion = android.getVariant().getMergedFlavor().getMinSdkVersion();
      if (minSdkVersion != null) {
        properties.put(AndroidProperties.MIN_SDK_VERSION_MIN, minSdkVersion.getApiLevel());
        properties.put(AndroidProperties.MIN_SDK_VERSION_MAX, minSdkVersion.getApiLevel());
      }
    } else {
      Set<Integer> minSdks = Stream.concat(android.getExtension().getProductFlavors().stream().map(ProductFlavor::getMinSdk),
          Stream.of(android.getExtension().getDefaultConfig().getMinSdk()))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
      if (!minSdks.isEmpty()) {
        properties.put(AndroidProperties.MIN_SDK_VERSION_MIN, Collections.min(minSdks));
        properties.put(AndroidProperties.MIN_SDK_VERSION_MAX, Collections.max(minSdks));
      }
    }
  }

  private static void configureTestReports(Project project, BaseVariant variant, Map<String, Object> map) {
    if (!(variant instanceof TestedVariant)) {
      return;
    }
    if (getAndroidPluginVersion().compareTo(Version.of("3.3")) < 0 || GradleVersion.current().compareTo(GradleVersion.version("6.0")) < 0) {
      // API to get task variant name is not available
      return;
    }

    List<DirectoryProperty> directories = new LinkedList<>();

    // junit tests
    UnitTestVariant unitTestVariant = ((TestedVariant) variant).getUnitTestVariant();
    if (unitTestVariant != null) {
      directories.addAll(project.getTasks().withType(AndroidUnitTest.class).stream()
        .filter(task -> unitTestVariant.getName().equals(task.getVariantName()))
        .map(task -> task.getReports().getJunitXml().getOutputLocation())
        .collect(Collectors.toList()));
    }

    // instrumentation tests
    TestVariant testVariant = ((TestedVariant) variant).getTestVariant();
    if (testVariant != null) {

      Function<DeviceProviderInstrumentTestTask, DirectoryProperty> testTaskToDirectoryProperty;
      if (getAndroidPluginVersion().compareTo(Version.of("4.2")) < 0) {
        // SONARGRADL-101 a File is returned instead of a DirectoryProperty
        testTaskToDirectoryProperty = AndroidUtils::getReportsDirBeforeGradle42;
      } else {
        testTaskToDirectoryProperty = DeviceProviderInstrumentTestTask::getReportsDir;
      }

      project.getTasks().withType(DeviceProviderInstrumentTestTask.class).stream()
        .filter(t -> testVariant.getName().equals(t.getVariantName()))
        .map(testTaskToDirectoryProperty)
        .filter(DirectoryProperty::isPresent)
        .forEach(directories::add);
    }

    if (directories.isEmpty()) {
      return;
    }

    List<File> value = directories.stream()
      .filter(Provider::isPresent)
      .map(d -> d.get().getAsFile())
      .collect(Collectors.toList());
    map.put(SonarProperty.JUNIT_REPORT_PATHS, value);
  }

  private static DirectoryProperty getReportsDirBeforeGradle42(DeviceProviderInstrumentTestTask testTask) {
    try {
      Method getReportsDir = testTask.getClass().getMethod("getReportsDir");
      File dir = (File) getReportsDir.invoke(testTask);
      return testTask.getProject().getObjects().directoryProperty().fileValue(dir);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Unable to get tests directory", e);
    }
  }

  private static void configureLintReports(Project project, BaseVariant variant, Map<String, Object> properties) {
    if (getAndroidPluginVersion().compareTo(Version.of("7.0")) >= 0) {
      project.getTasks().withType(AndroidLintTask.class).stream()
        .filter(a -> a.getXmlReportOutputFile().isPresent())
        .filter(a -> a.getVariantName().equals(variant.getName()))
        .map(a -> a.getXmlReportOutputFile().get().getAsFile())
        .findFirst()
        .ifPresent(output -> properties.put(SONAR_ANDROID_LINT_REPORT_PATHS_PROP, output));
    }
  }

  @Nullable
  private static List<File> getBootClasspath(Project project) {
    PluginCollection<AppPlugin> appPlugins = project.getPlugins().withType(AppPlugin.class);
    if (!appPlugins.isEmpty()) {
      AppExtension androidExtension = project.getExtensions().getByType(AppExtension.class);
      return androidExtension.getBootClasspath();
    }
    PluginCollection<LibraryPlugin> libPlugins = project.getPlugins().withType(LibraryPlugin.class);
    if (!libPlugins.isEmpty()) {
      LibraryExtension androidExtension = project.getExtensions().getByType(LibraryExtension.class);
      return androidExtension.getBootClasspath();
    }
    PluginCollection<TestPlugin> testPlugins = project.getPlugins().withType(TestPlugin.class);
    if (!testPlugins.isEmpty()) {
      TestExtension androidExtension = project.getExtensions().getByType(TestExtension.class);
      return androidExtension.getBootClasspath();
    }
    PluginCollection<DynamicFeaturePlugin> dynamicFeaturePlugins = project.getPlugins().withType(DynamicFeaturePlugin.class);
    if (!dynamicFeaturePlugins.isEmpty()) {
      AppExtension androidExtension = project.getExtensions().getByType(AppExtension.class);
      return androidExtension.getBootClasspath();
    }
    return null;
  }

  @Nullable
  private static String getTestBuildType(Project project) {
    PluginCollection<AppPlugin> appPlugins = project.getPlugins().withType(AppPlugin.class);
    if (!appPlugins.isEmpty()) {
      AppExtension androidExtension = project.getExtensions().getByType(AppExtension.class);
      return androidExtension.getTestBuildType();
    }
    PluginCollection<LibraryPlugin> libPlugins = project.getPlugins().withType(LibraryPlugin.class);
    if (!libPlugins.isEmpty()) {
      LibraryExtension androidExtension = project.getExtensions().getByType(LibraryExtension.class);
      return androidExtension.getTestBuildType();
    }
    PluginCollection<DynamicFeaturePlugin> dynamicFeaturePlugins = project.getPlugins().withType(DynamicFeaturePlugin.class);
    if (!dynamicFeaturePlugins.isEmpty()) {
      AppExtension androidExtension = project.getExtensions().getByType(AppExtension.class);
      return androidExtension.getTestBuildType();
    }
    return null;
  }

  @Nullable
  static AndroidVariantAndExtension findVariantAndExtension(Project project, @Nullable String userConfiguredBuildVariantName) {
    String testBuildType = getTestBuildType(project);
    PluginCollection<AppPlugin> appPlugins = project.getPlugins().withType(AppPlugin.class);
    if (!appPlugins.isEmpty()) {
      AppExtension androidExtension = project.getExtensions().getByType(AppExtension.class);
      BaseVariant variant = findVariant(new ArrayList<>(androidExtension.getApplicationVariants()), testBuildType, userConfiguredBuildVariantName);
      return new AndroidVariantAndExtension(androidExtension, variant, userConfiguredBuildVariantName);
    }
    PluginCollection<LibraryPlugin> libPlugins = project.getPlugins().withType(LibraryPlugin.class);
    if (!libPlugins.isEmpty()) {
      LibraryExtension androidExtension = project.getExtensions().getByType(LibraryExtension.class);
      BaseVariant variant = findVariant(new ArrayList<>(androidExtension.getLibraryVariants()), testBuildType, userConfiguredBuildVariantName);
      return new AndroidVariantAndExtension(androidExtension, variant, userConfiguredBuildVariantName);
    }
    PluginCollection<TestPlugin> testPlugins = project.getPlugins().withType(TestPlugin.class);
    if (!testPlugins.isEmpty()) {
      TestExtension androidExtension = project.getExtensions().getByType(TestExtension.class);
      BaseVariant variant = findVariant(new ArrayList<>(androidExtension.getApplicationVariants()), testBuildType, userConfiguredBuildVariantName);
      return new AndroidVariantAndExtension(androidExtension, variant, userConfiguredBuildVariantName);

    }
    PluginCollection<DynamicFeaturePlugin> dynamicFeaturePlugins = project.getPlugins().withType(DynamicFeaturePlugin.class);
    if (!dynamicFeaturePlugins.isEmpty()) {
      AppExtension androidExtension = project.getExtensions().getByType(AppExtension.class);
      BaseVariant variant = findVariant(new ArrayList<>(androidExtension.getApplicationVariants()), testBuildType, userConfiguredBuildVariantName);
      return new AndroidVariantAndExtension(androidExtension, variant, userConfiguredBuildVariantName);
    }
    return null;
  }

  private static Optional<AndroidVariantAndExtension> findVariantAndExtension(Project project) {
    return Optional.ofNullable(AndroidUtils.findVariantAndExtension(project, getConfiguredAndroidVariant(project)));
  }

  @Nullable
  private static BaseVariant findVariant(List<BaseVariant> candidates, @Nullable String testBuildType, @Nullable String userConfiguredBuildVariantName) {
    if (candidates.isEmpty()) {
      return null;
    }
    if (userConfiguredBuildVariantName == null) {
      // Take first "test" buildType when there is no provided variant name
      // Release variant may be obfuscated using proguard. Also unit tests and coverage reports are usually collected in debug mode.
      Optional<BaseVariant> firstDebug = candidates.stream().filter(v -> testBuildType != null && testBuildType.equals(v.getBuildType().getName())).findFirst();
      // No debug variant? Then use first variant whatever is the type
      BaseVariant result = firstDebug.orElse(candidates.get(0));
      LOGGER.info("No variant name specified to be used by SonarQube. Default to '{}'", result.getName());
      return result;
    } else {
      Optional<BaseVariant> result = candidates.stream().filter(v -> userConfiguredBuildVariantName.equals(v.getName())).findFirst();
      if (result.isPresent()) {
        return result.get();
      } else {
        throw new IllegalArgumentException("Unable to find variant '" + userConfiguredBuildVariantName +
          "' to use for SonarQube analysis. Candidates are: " + candidates.stream().map(BaseVariant::getName).collect(Collectors.joining(", ")));
      }
    }
  }

  private static void populateSonarQubeProps(Map<String, Object> properties, BaseVariant variant, boolean isTest) {
    List<File> srcDirs = variant.getSourceSets().stream().map(AndroidUtils::getFilesFromSourceSet).collect(
      ArrayList::new,
      ArrayList::addAll,
      ArrayList::addAll);
    appendSourcesProp(properties, srcDirs, isTest);

    JavaCompile javaCompile = getJavaCompiler(variant);
    if (javaCompile == null) {
      LOGGER.warn("Unable to find Java compiler on variant '{}'. Is Jack toolchain used? SonarQube analysis will be less accurate without bytecode.", variant.getName());
    } else {
      SonarUtils.populateJdkProperties(properties, JavaCompilerUtils.extractConfiguration(javaCompile));
      JavaCompilerUtils.extractConfiguration(javaCompile);
    }

    Collection<File> destinationDirs = (javaCompile != null)
      ? Collections.singleton(javaCompile.getDestinationDirectory().getAsFile().get())
      : Collections.emptySet();

    if (isTest) {
      appendProps(properties, SonarProperty.JAVA_TEST_BINARIES, destinationDirs);
    } else {
      appendProps(properties, SonarProperty.JAVA_BINARIES, destinationDirs);
      // Populate deprecated properties for backward compatibility
      appendProps(properties, SonarProperty.BINARIES, destinationDirs);
    }
  }

  /**
   * Get the libraries FileCollection for an Android variant without resolving it.
   * This allows the FileCollection to be attached as a task input and resolved later at execution time.
   */
  static FileCollection getLibrariesFileCollection(Project project, BaseVariant variant) {
    // Get boot classpath
    List<File> bootClassPath = getBootClasspath(project);
    FileCollection bootClassPathFiles = bootClassPath != null ? project.files(bootClassPath) : project.files();

    // Get variant libraries
    if (variant instanceof ApkVariant) {
      ApkVariant apkVariant = (ApkVariant) variant;
      FileCollection compileClasspath = apkVariant.getCompileClasspath(null);
      if (compileClasspath != null) {
        return bootClassPathFiles.plus(compileClasspath);
      }
    }

    // For non-ApkVariant or if we couldn't get libraries, try getJavaCompiler
    JavaCompile javaCompile = getJavaCompiler(variant);
    if (javaCompile != null) {
      return bootClassPathFiles.plus(javaCompile.getClasspath());
    }

    return bootClassPathFiles;
  }

  private static boolean isMinSdkSupported() {
    // Retrieving minSdk was introduced in Android Gradle plugin 4.1+. 4.1+ runs only with Gradle 6.5+
    return GradleVersion.current().compareTo(GradleVersion.version("6.5")) >= 0;
  }

  @Nullable
  private static JavaCompile getJavaCompiler(BaseVariant variant) {
    return variant.getJavaCompileProvider().getOrNull();
  }

  private static List<File> getFilesFromSourceSet(SourceProvider sourceSet) {
    List<File> srcDirs = new ArrayList<>();
    srcDirs.add(sourceSet.getManifestFile());
    srcDirs.addAll(sourceSet.getCDirectories());
    srcDirs.addAll(sourceSet.getAidlDirectories());
    srcDirs.addAll(sourceSet.getAssetsDirectories());
    srcDirs.addAll(sourceSet.getCppDirectories());
    srcDirs.addAll(sourceSet.getJavaDirectories());
    srcDirs.addAll(sourceSet.getRenderscriptDirectories());
    srcDirs.addAll(sourceSet.getResDirectories());
    srcDirs.addAll(sourceSet.getResourcesDirectories());
    return srcDirs;
  }

  static class AndroidVariantAndExtension {

    private final BaseExtension extension;
    private final BaseVariant variant;
    private final boolean variantProvidedByUser;

    AndroidVariantAndExtension(BaseExtension baseExtension, @Nullable BaseVariant baseVariant, @Nullable String userConfiguredVariantName) {
      this.extension = baseExtension;
      this.variant = baseVariant;
      this.variantProvidedByUser = variant != null && variant.getName().equals(userConfiguredVariantName);
    }

    BaseExtension getExtension() {
      return extension;
    }

    BaseVariant getVariant() {
      return variant;
    }

    boolean isVariantProvidedByUser() {
      return variantProvidedByUser;
    }
  }

  public static FileCollection findMainLibraries(Project project) {
    return findVariantAndExtension(project)
      .map(AndroidVariantAndExtension::getVariant)
      .map(variant -> AndroidUtils.getLibrariesFileCollection(project, variant))
      .orElse(project.files());
  }

  public static FileCollection findTestLibraries(Project project) {
    var variantAndExtension = findVariantAndExtension(project);
    if (variantAndExtension.isEmpty()) {
      return project.files();
    }
    var testVariants = getTestVariants(variantAndExtension.get().getVariant());
    return testVariants.stream()
      .map(testVariant -> AndroidUtils.getLibrariesFileCollection(project, testVariant))
      .filter(Objects::nonNull)
      .reduce(project.files(), FileCollection::plus);
  }
}
