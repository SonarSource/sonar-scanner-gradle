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

import com.android.build.api.dsl.AndroidSourceSet;
import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.api.dsl.BuildType;
import com.android.build.api.dsl.CommonExtension;
import com.android.build.api.dsl.DefaultConfig;
import com.android.build.api.dsl.ProductFlavor;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.util.GradleVersion;
import org.sonarqube.gradle.properties.SonarProperty;

import static org.sonarqube.gradle.SonarQubePlugin.getConfiguredAndroidVariant;
import static org.sonarqube.gradle.SonarUtils.appendProps;
import static org.sonarqube.gradle.SonarUtils.appendSourcesProp;
import static org.sonarqube.gradle.SonarUtils.capitalize;

/**
 * Only access this class when running on an Android application.
 * Uses the stable com.android.build.api.dsl API that works across AGP 7+ through 9+.
 */
class AndroidUtils {
  private static final Logger LOGGER = Logging.getLogger(AndroidUtils.class);
  private static final String SONAR_ANDROID_LINT_REPORT_PATHS_PROP = SonarProperty.ANDROID_LINT_REPORT_PATHS;

  private AndroidUtils() {
  }

  static void configureForAndroid(Project project, @Nullable String userConfiguredBuildVariantName, final Map<String, Object> properties) {
    AndroidVariantAndExtension android = findVariantAndExtension(project, userConfiguredBuildVariantName);
    if (android != null && android.getVariantName() != null) {
      configureForAndroid(project, android, properties);
    } else {
      LOGGER.warn("No variant found for '{}'. No android specific configuration will be done", project.getName());
    }
  }

  static Version getAndroidPluginVersion() {
    return Version.of(getAndroidPluginVersionString());
  }

  private static String getAndroidPluginVersionString() {
    for (String className : new String[]{
      "com.android.builder.model.Version",
      "com.android.Version"
    }) {
      try {
        Class<?> c = Class.forName(className);
        Field f = c.getField("ANDROID_GRADLE_PLUGIN_VERSION");
        return (String) f.get(null);
      } catch (Exception ignored) {
        // try next
      }
    }
    LOGGER.warn("Unable to determine Android Gradle Plugin version");
    return "0.0.0";
  }

  @Nullable
  @SuppressWarnings({"rawtypes", "unchecked"})
  static AndroidVariantAndExtension findVariantAndExtension(Project project, @Nullable String userConfiguredBuildVariantName) {
    CommonExtension extension = findExtension(project);
    if (extension == null) {
      return null;
    }

    String testBuildType = getTestBuildType(project, extension);
    List<String> flavorDimensions = extension.getFlavorDimensions();

    Map<String, List<String>> flavorsByDimension = getFlavorsByDimension(extension, flavorDimensions);
    List<String> allVariantNames = computeAllVariantNames(flavorDimensions, flavorsByDimension, extension);

    if (allVariantNames.isEmpty()) {
      return null;
    }

    String selectedVariantName;

    if (userConfiguredBuildVariantName != null) {
      if (!allVariantNames.contains(userConfiguredBuildVariantName)) {
        throw new IllegalArgumentException("Unable to find variant '" + userConfiguredBuildVariantName +
          "' to use for SonarQube analysis. Candidates are: " + String.join(", ", allVariantNames));
      }
      selectedVariantName = userConfiguredBuildVariantName;
    } else {
      if (testBuildType != null) {
        Optional<String> firstMatch = allVariantNames.stream()
          .filter(v -> v.endsWith(capitalize(testBuildType)) || v.equals(testBuildType))
          .findFirst();
        selectedVariantName = firstMatch.orElse(allVariantNames.get(0));
      } else {
        selectedVariantName = allVariantNames.get(0);
      }
      LOGGER.info("No variant name specified to be used by SonarQube. Default to '{}'", selectedVariantName);
    }

    List<String> selectedFlavorNames = extractFlavorNames(selectedVariantName, flavorDimensions, flavorsByDimension, extension);
    String selectedBuildTypeName = extractBuildTypeName(selectedVariantName, extension);

    return new AndroidVariantAndExtension(extension, selectedVariantName, selectedFlavorNames, selectedBuildTypeName, userConfiguredBuildVariantName);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Map<String, List<String>> getFlavorsByDimension(CommonExtension extension, List<String> flavorDimensions) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    for (String dim : flavorDimensions) {
      result.put(dim, new ArrayList<>());
    }
    NamedDomainObjectContainer flavors = extension.getProductFlavors();
    for (Object obj : flavors) {
      ProductFlavor flavor = (ProductFlavor) obj;
      String dim = flavor.getDimension();
      if (dim != null && result.containsKey(dim)) {
        result.get(dim).add(flavor.getName());
      }
    }
    return result;
  }

  @Nullable
  private static Method findMethod(Class<?> clazz, String name) {
    while (clazz != null) {
      for (Method m : clazz.getDeclaredMethods()) {
        if (m.getName().equals(name) && m.getParameterCount() == 0) {
          return m;
        }
      }
      clazz = clazz.getSuperclass();
    }
    return null;
  }

  /**
   * Compute variant names from DSL configuration.
   * Note: NamedDomainObjectContainer sorts alphabetically.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static List<String> computeAllVariantNames(List<String> flavorDimensions, Map<String, List<String>> flavorsByDimension, CommonExtension extension) {
    NamedDomainObjectContainer buildTypesContainer = extension.getBuildTypes();
    List<String> buildTypeNames = new ArrayList<>();
    for (Object obj : buildTypesContainer) {
      buildTypeNames.add(((BuildType) obj).getName());
    }

    if (flavorDimensions.isEmpty() || flavorsByDimension.values().stream().allMatch(List::isEmpty)) {
      return buildTypeNames;
    }

    List<List<String>> dimensionFlavors = flavorDimensions.stream()
      .map(dim -> flavorsByDimension.getOrDefault(dim, Collections.emptyList()))
      .collect(Collectors.toList());

    List<List<String>> flavorCombinations = cartesianProduct(dimensionFlavors);

    List<String> variants = new ArrayList<>();
    for (List<String> flavorCombo : flavorCombinations) {
      String flavorPart = joinFlavorNames(flavorCombo);
      for (String bt : buildTypeNames) {
        variants.add(flavorPart + capitalize(bt));
      }
    }

    return variants;
  }

  private static List<List<String>> cartesianProduct(List<List<String>> lists) {
    List<List<String>> result = new ArrayList<>();
    if (lists.isEmpty()) {
      result.add(Collections.emptyList());
      return result;
    }
    cartesianProductHelper(lists, 0, new ArrayList<>(), result);
    return result;
  }

  private static void cartesianProductHelper(List<List<String>> lists, int depth, List<String> current, List<List<String>> result) {
    if (depth == lists.size()) {
      result.add(new ArrayList<>(current));
      return;
    }
    for (String item : lists.get(depth)) {
      current.add(item);
      cartesianProductHelper(lists, depth + 1, current, result);
      current.remove(current.size() - 1);
    }
  }

  private static String joinFlavorNames(List<String> flavorNames) {
    if (flavorNames.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(flavorNames.get(0));
    for (int i = 1; i < flavorNames.size(); i++) {
      sb.append(capitalize(flavorNames.get(i)));
    }
    return sb.toString();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static List<String> extractFlavorNames(String variantName, List<String> flavorDimensions, Map<String, List<String>> flavorsByDimension, CommonExtension extension) {
    if (flavorDimensions.isEmpty()) {
      return Collections.emptyList();
    }

    List<List<String>> dimensionFlavors = flavorDimensions.stream()
      .map(dim -> flavorsByDimension.getOrDefault(dim, Collections.emptyList()))
      .collect(Collectors.toList());

    NamedDomainObjectContainer buildTypesContainer = extension.getBuildTypes();
    for (List<String> combo : cartesianProduct(dimensionFlavors)) {
      String flavorPart = joinFlavorNames(combo);
      for (Object obj : buildTypesContainer) {
        String btName = ((BuildType) obj).getName();
        if (variantName.equals(flavorPart + capitalize(btName))) {
          return combo;
        }
      }
    }

    return Collections.emptyList();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static String extractBuildTypeName(String variantName, CommonExtension extension) {
    NamedDomainObjectContainer buildTypesContainer = extension.getBuildTypes();
    for (Object obj : buildTypesContainer) {
      String btName = ((BuildType) obj).getName();
      if (variantName.endsWith(capitalize(btName)) || variantName.equals(btName)) {
        return btName;
      }
    }
    return "debug";
  }

  @SuppressWarnings("rawtypes")
  @Nullable
  private static CommonExtension findExtension(Project project) {
    if (project.getPlugins().hasPlugin("com.android.application")
      || project.getPlugins().hasPlugin("com.android.dynamic-feature")) {
      try {
        return (CommonExtension) project.getExtensions().getByType(ApplicationExtension.class);
      } catch (Exception e) {
        LOGGER.warn("Unable to find ApplicationExtension: {}", e.getMessage());
        return null;
      }
    }
    if (project.getPlugins().hasPlugin("com.android.library")) {
      try {
        return (CommonExtension) project.getExtensions().getByType(com.android.build.api.dsl.LibraryExtension.class);
      } catch (Exception e) {
        LOGGER.warn("Unable to find LibraryExtension: {}", e.getMessage());
        return null;
      }
    }
    if (project.getPlugins().hasPlugin("com.android.test")) {
      try {
        return (CommonExtension) project.getExtensions().getByType(com.android.build.api.dsl.TestExtension.class);
      } catch (Exception e) {
        LOGGER.warn("Unable to find TestExtension: {}", e.getMessage());
        return null;
      }
    }
    return null;
  }

  @SuppressWarnings("rawtypes")
  @Nullable
  private static String getTestBuildType(Project project, CommonExtension extension) {
    if (project.getPlugins().hasPlugin("com.android.test")) {
      return null;
    }
    try {
      Method m = extension.getClass().getMethod("getTestBuildType");
      return (String) m.invoke(extension);
    } catch (Exception e) {
      return null;
    }
  }

  private static void configureForAndroid(Project project, AndroidVariantAndExtension android, Map<String, Object> properties) {
    String variantName = android.getVariantName();

    populateSonarQubeAndroidProperties(android, properties);
    configureTestReports(project, variantName, properties);
    configureLintReports(project, variantName, properties);

    if (project.getPlugins().hasPlugin("com.android.test")) {
      populateSonarQubeProps(project, properties, android, true);
      return;
    }
    populateSonarQubeProps(project, properties, android, false);

    // Unit test variant
    String unitTestVariantName = variantName + "UnitTest";
    populateSonarQubePropsForTestVariant(project, properties, android, unitTestVariantName, "test");

    // Android test variant
    String androidTestVariantName = variantName + "AndroidTest";
    populateSonarQubePropsForTestVariant(project, properties, android, androidTestVariantName, "androidTest");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void populateSonarQubeAndroidProperties(AndroidVariantAndExtension android, Map<String, Object> properties) {
    properties.put(AndroidProperties.ANDROID_DETECTED, true);

    if (!isMinSdkSupported()) {
      return;
    }

    CommonExtension extension = android.getExtension();

    if (android.isVariantProvidedByUser()) {
      Integer minSdk = findMinSdkForVariant(extension, android.getFlavorNames());
      if (minSdk != null) {
        properties.put(AndroidProperties.MIN_SDK_VERSION_MIN, minSdk);
        properties.put(AndroidProperties.MIN_SDK_VERSION_MAX, minSdk);
      }
    } else {
      Set<Integer> minSdks = Stream.concat(
          ((NamedDomainObjectContainer<? extends ProductFlavor>) extension.getProductFlavors()).stream()
            .map(ProductFlavor::getMinSdk),
          Stream.of(((DefaultConfig) extension.getDefaultConfig()).getMinSdk()))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

      if (!minSdks.isEmpty()) {
        properties.put(AndroidProperties.MIN_SDK_VERSION_MIN, Collections.min(minSdks));
        properties.put(AndroidProperties.MIN_SDK_VERSION_MAX, Collections.max(minSdks));
      }
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Nullable
  private static Integer findMinSdkForVariant(CommonExtension extension, List<String> flavorNames) {
    NamedDomainObjectContainer flavors = extension.getProductFlavors();
    for (String flavorName : flavorNames) {
      Object flavorObj = flavors.findByName(flavorName);
      if (flavorObj instanceof ProductFlavor) {
        Integer minSdk = ((ProductFlavor) flavorObj).getMinSdk();
        if (minSdk != null) {
          return minSdk;
        }
      }
    }
    Object defaultConfig = extension.getDefaultConfig();
    if (defaultConfig instanceof DefaultConfig) {
      return ((DefaultConfig) defaultConfig).getMinSdk();
    }
    return null;
  }

  private static void configureTestReports(Project project, String variantName, Map<String, Object> properties) {
    List<File> directories = new LinkedList<>();

    // Unit test reports
    String unitTestTaskName = "test" + capitalize(variantName) + "UnitTest";
    project.getTasks().withType(org.gradle.api.tasks.testing.Test.class).stream()
      .filter(t -> unitTestTaskName.equals(t.getName()))
      .forEach(t -> {
        try {
          DirectoryProperty outputLocation = t.getReports().getJunitXml().getOutputLocation();
          if (outputLocation.isPresent()) {
            directories.add(outputLocation.get().getAsFile());
          }
        } catch (Exception e) {
          LOGGER.debug("Unable to get unit test reports directory: {}", e.getMessage());
        }
      });

    // Instrumentation test reports
    try {
      @SuppressWarnings("unchecked")
      Class<? extends Task> instrumentTestClass =
        (Class<? extends Task>) Class.forName("com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask");
      String androidTestVariantName = variantName + "AndroidTest";

      project.getTasks().withType(instrumentTestClass).stream()
        .filter(t -> {
          try {
            Method getVariantName = t.getClass().getMethod("getVariantName");
            return androidTestVariantName.equals(getVariantName.invoke(t));
          } catch (Exception e) {
            return false;
          }
        })
        .forEach(t -> {
          try {
            Method getReportsDir = t.getClass().getMethod("getReportsDir");
            Object result = getReportsDir.invoke(t);
            if (result instanceof DirectoryProperty) {
              DirectoryProperty dp = (DirectoryProperty) result;
              if (dp.isPresent()) {
                directories.add(dp.get().getAsFile());
              }
            }
          } catch (Exception e) {
            LOGGER.debug("Unable to get instrumentation test reports: {}", e.getMessage());
          }
        });
    } catch (ClassNotFoundException ignored) {
      // DeviceProviderInstrumentTestTask not available in this AGP version
    }

    if (!directories.isEmpty()) {
      properties.put(SonarProperty.JUNIT_REPORT_PATHS, directories);
    }
  }

  private static void configureLintReports(Project project, String variantName, Map<String, Object> properties) {
    if (getAndroidPluginVersion().compareTo(Version.of("7.0")) < 0) {
      return;
    }

    try {
      @SuppressWarnings("unchecked")
      Class<? extends Task> lintTaskClass =
        (Class<? extends Task>) Class.forName("com.android.build.gradle.internal.lint.AndroidLintTask");

      project.getTasks().withType(lintTaskClass).stream()
        .filter(task -> {
          try {
            Method getVariantName = task.getClass().getMethod("getVariantName");
            return variantName.equals(getVariantName.invoke(task));
          } catch (Exception e) {
            return false;
          }
        })
        .findFirst()
        .ifPresent(task -> {
          try {
            Method getXmlReport = task.getClass().getMethod("getXmlReportOutputFile");
            @SuppressWarnings("unchecked")
            Provider<Object> provider = (Provider<Object>) getXmlReport.invoke(task);
            if (provider.isPresent()) {
              Object fileLocation = provider.get();
              Method getAsFile = fileLocation.getClass().getMethod("getAsFile");
              File output = (File) getAsFile.invoke(fileLocation);
              properties.put(SONAR_ANDROID_LINT_REPORT_PATHS_PROP, output);
            }
          } catch (Exception e) {
            LOGGER.debug("Unable to get lint report output: {}", e.getMessage());
          }
        });
    } catch (ClassNotFoundException ignored) {
      LOGGER.debug("AndroidLintTask class not found, skipping lint report configuration");
    }
  }

  private static void populateSonarQubeProps(Project project, Map<String, Object> properties, AndroidVariantAndExtension android, boolean isTest) {
    List<String> sourceSetNames = computeSourceSetNames(null, android.getFlavorNames(), android.getBuildTypeName());
    List<File> srcDirs = collectSourceDirs(android.getExtension(), sourceSetNames);
    appendSourcesProp(properties, srcDirs, isTest);

    String compileTaskName = "compile" + capitalize(android.getVariantName()) + "JavaWithJavac";
    JavaCompile javaCompile = findJavaCompileTask(project, compileTaskName);

    if (javaCompile == null) {
      LOGGER.warn("Unable to find Java compiler for variant '{}'. SonarQube analysis will be less accurate without bytecode.", android.getVariantName());
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
  }

  private static void populateSonarQubePropsForTestVariant(Project project, Map<String, Object> properties, AndroidVariantAndExtension android, String testVariantName, String testPrefix) {
    String compileTaskName = "compile" + capitalize(testVariantName) + "JavaWithJavac";
    JavaCompile javaCompile = findJavaCompileTask(project, compileTaskName);

    if (javaCompile == null) {
      return;
    }

    List<String> sourceSetNames = computeSourceSetNames(testPrefix, android.getFlavorNames(), android.getBuildTypeName());
    List<File> srcDirs = collectSourceDirs(android.getExtension(), sourceSetNames);
    appendSourcesProp(properties, srcDirs, true);

    Collection<File> destinationDirs = Collections.singleton(javaCompile.getDestinationDirectory().getAsFile().get());
    appendProps(properties, SonarProperty.JAVA_TEST_BINARIES, destinationDirs);
  }

  @Nullable
  private static JavaCompile findJavaCompileTask(Project project, String taskName) {
    Task task = project.getTasks().findByName(taskName);
    return (task instanceof JavaCompile) ? (JavaCompile) task : null;
  }

  /**
   * Compute the source set names that contribute to a variant.
   *
   * @param prefix      null for main variant, "test" for unit tests, "androidTest" for instrumented tests
   * @param flavorNames the flavor names for this variant (in dimension order)
   * @param buildTypeName the build type name
   * @return list of source set names
   */
  private static List<String> computeSourceSetNames(@Nullable String prefix, List<String> flavorNames, String buildTypeName) {
    List<String> names = new ArrayList<>();

    // Base source set
    names.add(prefix == null ? "main" : prefix);

    // Per-flavor source sets
    for (String flavor : flavorNames) {
      names.add(prefix == null ? flavor : prefix + capitalize(flavor));
    }

    // Multi-flavor source set (only if more than one flavor)
    if (flavorNames.size() > 1) {
      String multiFlavor = joinFlavorNames(flavorNames);
      names.add(prefix == null ? multiFlavor : prefix + capitalize(multiFlavor));
    }

    // Build type source set
    names.add(prefix == null ? buildTypeName : prefix + capitalize(buildTypeName));

    // Full variant source set
    String variantPart = computeVariantNamePart(flavorNames, buildTypeName);
    String fullName = prefix == null ? variantPart : prefix + capitalize(variantPart);
    if (!names.contains(fullName)) {
      names.add(fullName);
    }

    return names;
  }

  private static String computeVariantNamePart(List<String> flavorNames, String buildTypeName) {
    if (flavorNames.isEmpty()) {
      return buildTypeName;
    }
    return joinFlavorNames(flavorNames) + capitalize(buildTypeName);
  }

  @SuppressWarnings("rawtypes")
  private static List<File> collectSourceDirs(CommonExtension extension, List<String> sourceSetNames) {
    List<File> allDirs = new ArrayList<>();
    NamedDomainObjectContainer sourceSets = extension.getSourceSets();

    for (String name : sourceSetNames) {
      Object sourceSetObj = sourceSets.findByName(name);
      if (sourceSetObj instanceof AndroidSourceSet) {
        allDirs.addAll(getFilesFromSourceSet((AndroidSourceSet) sourceSetObj));
      }
    }

    return allDirs;
  }

  @SuppressWarnings("unchecked")
  private static List<File> getFilesFromSourceSet(AndroidSourceSet sourceSet) {
    List<File> srcDirs = new ArrayList<>();
    // Use reflection because the DSL interfaces in AGP 8.x don't expose getters,
    // but the implementation classes do.
    try {
      Object manifest = sourceSet.getManifest();
      Method getSrcFile = manifest.getClass().getMethod("getSrcFile");
      File manifestFile = (File) getSrcFile.invoke(manifest);
      if (manifestFile != null) {
        srcDirs.add(manifestFile);
      }
    } catch (Exception e) {
      LOGGER.debug("Unable to get manifest file from source set '{}': {}", sourceSet.getName(), e.getMessage());
    }
    addSourceDirsReflective(srcDirs, sourceSet, "getJava");
    addSourceDirsReflective(srcDirs, sourceSet, "getRes");
    addSourceDirsReflective(srcDirs, sourceSet, "getAssets");
    addSourceDirsReflective(srcDirs, sourceSet, "getAidl");
    addSourceDirsReflective(srcDirs, sourceSet, "getRenderscript");
    addSourceDirsReflective(srcDirs, sourceSet, "getJni");
    addSourceDirsReflective(srcDirs, sourceSet, "getResources");
    return srcDirs;
  }

  @SuppressWarnings("unchecked")
  private static void addSourceDirsReflective(List<File> target, AndroidSourceSet sourceSet, String dirSetGetterName) {
    try {
      Method getDirSet = sourceSet.getClass().getMethod(dirSetGetterName);
      Object dirSet = getDirSet.invoke(sourceSet);
      Method getSrcDirs = dirSet.getClass().getMethod("getSrcDirs");
      Set<File> dirs = (Set<File>) getSrcDirs.invoke(dirSet);
      if (dirs != null) {
        target.addAll(dirs);
      }
    } catch (Exception e) {
      // Ignore - directory set might not be available in all AGP versions
    }
  }

  @Nullable
  private static List<File> getBootClasspath(Project project) {
    // Method 1: BaseExtension.getBootClasspath() (AGP < 9)
    try {
      Class<?> baseExtClass = Class.forName("com.android.build.gradle.BaseExtension");
      Object ext = project.getExtensions().getByType(baseExtClass);
      Method method = baseExtClass.getMethod("getBootClasspath");
      @SuppressWarnings("unchecked")
      List<File> result = (List<File>) method.invoke(ext);
      return result;
    } catch (Exception ignored) {
      // Not available, try next method
    }

    // Method 2: AndroidComponentsExtension.sdkComponents.bootClasspath (AGP 7+)
    try {
      Object componentsExt = project.getExtensions().findByName("androidComponents");
      if (componentsExt != null) {
        Method getSdkComponents = componentsExt.getClass().getMethod("getSdkComponents");
        Object sdkComponents = getSdkComponents.invoke(componentsExt);
        Method getBootClasspath = sdkComponents.getClass().getMethod("getBootClasspath");
        @SuppressWarnings("unchecked")
        Provider<List<Object>> provider = (Provider<List<Object>>) getBootClasspath.invoke(sdkComponents);
        List<Object> files = provider.get();
        List<File> result = new ArrayList<>();
        for (Object f : files) {
          try {
            Method getAsFile = f.getClass().getMethod("getAsFile");
            result.add((File) getAsFile.invoke(f));
          } catch (Exception ex) {
            if (f instanceof File) {
              result.add((File) f);
            }
          }
        }
        return result;
      }
    } catch (Exception e) {
      LOGGER.warn("Unable to get Android boot classpath: {}", e.getMessage());
    }

    return null;
  }

  private static boolean isMinSdkSupported() {
    return GradleVersion.current().compareTo(GradleVersion.version("6.5")) >= 0;
  }

  static FileCollection getLibrariesFileCollection(Project project, String variantName) {
    List<File> bootClassPath = getBootClasspath(project);
    FileCollection bootClassPathFiles = bootClassPath != null ? project.files(bootClassPath) : project.files();

    String taskName = "compile" + capitalize(variantName) + "JavaWithJavac";
    JavaCompile javaCompile = findJavaCompileTask(project, taskName);
    if (javaCompile != null) {
      return bootClassPathFiles.plus(javaCompile.getClasspath());
    }

    return bootClassPathFiles;
  }

  public static FileCollection findMainLibraries(Project project) {
    return findVariantAndExtension(project)
      .map(avae -> getLibrariesFileCollection(project, avae.getVariantName()))
      .orElse(project.files());
  }

  public static FileCollection findTestLibraries(Project project) {
    var avae = findVariantAndExtension(project);
    if (avae.isEmpty()) {
      return project.files();
    }
    String variantName = avae.get().getVariantName();
    return Stream.of(variantName + "UnitTest", variantName + "AndroidTest")
      .map(tvn -> getLibrariesFileCollection(project, tvn))
      .filter(Objects::nonNull)
      .reduce(project.files(), FileCollection::plus);
  }

  public static List<String> getTestVariantNames(String variantName) {
    List<String> names = new ArrayList<>();
    names.add(variantName + "UnitTest");
    names.add(variantName + "AndroidTest");
    return names;
  }

  private static Optional<AndroidVariantAndExtension> findVariantAndExtension(Project project) {
    return Optional.ofNullable(AndroidUtils.findVariantAndExtension(project, getConfiguredAndroidVariant(project)));
  }

  static class AndroidVariantAndExtension {
    @SuppressWarnings("rawtypes")
    private final CommonExtension extension;
    private final String variantName;
    private final List<String> flavorNames;
    private final String buildTypeName;
    private final boolean variantProvidedByUser;

    @SuppressWarnings("rawtypes")
    AndroidVariantAndExtension(CommonExtension extension, @Nullable String variantName, List<String> flavorNames, String buildTypeName, @Nullable String userConfiguredVariantName) {
      this.extension = extension;
      this.variantName = variantName;
      this.flavorNames = flavorNames;
      this.buildTypeName = buildTypeName;
      this.variantProvidedByUser = variantName != null && variantName.equals(userConfiguredVariantName);
    }

    @SuppressWarnings("rawtypes")
    CommonExtension getExtension() {
      return extension;
    }

    String getVariantName() {
      return variantName;
    }

    List<String> getFlavorNames() {
      return flavorNames;
    }

    String getBuildTypeName() {
      return buildTypeName;
    }

    boolean isVariantProvidedByUser() {
      return variantProvidedByUser;
    }
  }
}
