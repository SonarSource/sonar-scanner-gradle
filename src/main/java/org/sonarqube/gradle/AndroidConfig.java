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
import com.android.build.api.dsl.CommonExtension;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.AndroidTest;
import com.android.build.api.variant.Component;
import com.android.build.api.variant.SourceDirectories;
import com.android.build.api.variant.Sources;
import com.android.build.api.variant.TestComponent;
import com.android.build.api.variant.UnitTest;
import com.android.build.api.variant.Variant;
import com.android.build.gradle.internal.api.DefaultAndroidSourceDirectorySet;
import com.android.build.gradle.internal.lint.AndroidLintTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.sonarqube.gradle.properties.SonarProperty;

import static com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION;

public class AndroidConfig {

  private static final Logger LOGGER = Logging.getLogger(AndroidConfig.class);

  private final Project project;
  private final AndroidComponentsExtension<?, ?, ?> androidComponentsExtension;
  private final List<Variant> variants;
  private Variant selectedVariant;

  @SuppressWarnings({"java:S1612", "java:S1602"})
  public static AndroidConfig of(Project project) {
    AndroidComponentsExtension<?, ?, ?> androidComponentsExtension = project.getExtensions().getByType(AndroidComponentsExtension.class);
    var androidConfig = new AndroidConfig(project, androidComponentsExtension);
    androidComponentsExtension.onVariants(androidComponentsExtension.selector().all(), variant -> {
      androidConfig.variants.add(variant);
    });
    return androidConfig;
  }

  public static boolean usesAndroidGradlePlugin9() {
    return Version.of(ANDROID_GRADLE_PLUGIN_VERSION).compareTo(Version.of("9.0.0")) >= 0;
  }

  private static int getMinSdk(Variant variant) {
    if (Version.of(ANDROID_GRADLE_PLUGIN_VERSION).compareTo(Version.of(8, 0)) < 0) {
      return variant.getMinSdkVersion().getApiLevel();
    }
    return variant.getMinSdk().getApiLevel();
  }

  private static Provider<List<File>> getCompiledClasses(Project project, Component component) {
    return project.provider(() -> {
      File defaultJavaPath = project.getLayout()
        .getBuildDirectory()
        .dir("intermediates/javac/" + component.getName() + "/classes")
        .get()
        .getAsFile();
      return Collections.singletonList(defaultJavaPath);
    });
  }

  private static String getCompileTaskName(Component component) {
    return "compile" + SonarUtils.capitalize(component.getName()) + "JavaWithJavac";
  }

  private AndroidConfig(Project project, AndroidComponentsExtension<?, ?, ?> androidComponentsExtension) {
    this.project = project;
    this.androidComponentsExtension = androidComponentsExtension;
    this.variants = new ArrayList<>();
    this.selectedVariant = null;
  }

  /**
   * Get the variant selected for the analysis with Sonar.
   */
  public Variant getVariant() {
    if (selectedVariant != null) {
      return selectedVariant;
    }

    if (variants.isEmpty()) {
      throw new IllegalStateException("No Android variant found for project " + project.getName() + ".");
    }

    String configuredVariantName = SonarQubePlugin.getConfiguredAndroidVariant(project);
    if (configuredVariantName != null) {
      Optional<Variant> configuredVariant = variants.stream()
        .filter(variant -> variant.getName().equals(configuredVariantName))
        .findFirst();
      if (configuredVariant.isEmpty()) {
        throw new IllegalStateException(
          "Unable to find variant '"
            + configuredVariantName
            + "' to use for SonarQube analysis. Candidates are: "
            + String.join(", ", variants.stream().map(Variant::getName).collect(Collectors.toSet())));
      }
      selectedVariant = configuredVariant.get();
    } else {
      // Find the variant that is the target for Android tests in the project. If no variant has Android tests, return the first we find.
      selectedVariant = variants.stream()
        .filter(v -> v.getNestedComponents().stream().anyMatch(AndroidTest.class::isInstance))
        .findFirst()
        .orElse(variants.get(0));
    }

    return selectedVariant;
  }

  /**
   * Get the main libraries file collection for the variant selected for the analysis with Sonar.
   */
  public FileCollection getMainLibraries() {
    FileCollection mainLibraries = project.files(androidComponentsExtension.getSdkComponents().getBootClasspath());
    mainLibraries = mainLibraries.plus(getCompileClasspath(getVariant()));
    return mainLibraries;
  }

  /**
   * Get the test libraries file collection for the variant selected for the analysis with Sonar.
   */
  public FileCollection getTestLibraries() {
    List<Component> testComponents = getTestComponents();
    if (testComponents.isEmpty()) {
      return project.files();
    }
    FileCollection testLibraries = project.files(androidComponentsExtension.getSdkComponents().getBootClasspath());
    for (Component component : testComponents) {
      testLibraries = testLibraries.plus(getCompileClasspath(component));
    }
    return testLibraries;
  }

  /**
   * Get the source directories for the selected Android variant.
   */
  public FileCollection getAndroidSources() {
    FileCollection sources = getSources(getVariant());
    sources = addManifestAndRes(sources, "main");
    return addManifestAndRes(sources, getVariant().getName());
  }

  /**
   * Get the source directories for the selected Android variant's tests.
   */
  public FileCollection getAndroidTests() {
    FileCollection tests = project.files();
    String variantName = SonarUtils.capitalize(getVariant().getName());
    for (Component component : getTestComponents()) {
      tests = tests.plus(getSources(component));
      if (component instanceof AndroidTest) {
        tests = addManifestAndRes(tests, "androidTest" + variantName);
      } else {
        tests = addManifestAndRes(tests, "test" + variantName);
      }
    }
    return tests;
  }

  /**
   * Get the Android tasks on which Sonar tasks need to depend for the variant selected for the analysis with Sonar.
   */
  public Set<Task> getTasks() {
    String variantName = getVariant().getName().toLowerCase();
    return project.getTasks().stream()
      .filter(task -> task.getName().toLowerCase().contains(variantName))
      .collect(Collectors.toSet());
  }

  /**
   * Compute the compilation classpath for an Android component.
   */
  private FileCollection getCompileClasspath(Component component) {
    String configName = component.getName() + "CompileClasspath";
    Configuration configuration = project.getConfigurations().getByName(configName);

    return configuration.getIncoming().artifactView(viewConfiguration -> viewConfiguration.attributes(attributeContainer -> {
      attributeContainer.attribute(
        Attribute.of("artifactType", String.class),
        "android-classes-jar"
      );
      attributeContainer.attribute(
        TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
        project.getObjects().named(TargetJvmEnvironment.class, TargetJvmEnvironment.ANDROID)
      );
    })).getFiles();
  }

  /**
   * Populate the properties of an Android variant with Android specific values.
   */
  public void configureProperties(Map<String, Object> properties) {
    configureAndroidProperties(properties);
    configureTestReports(properties);
    configureLintReports(properties);

    if (project.getPlugins().hasPlugin("com.android.test")) {
      configureJDK(properties, getVariant(), true);
      return;
    }
    configureJDK(properties, getVariant(), false);
    for (Component component : getTestComponents()) {
      configureJDK(properties, component, true);
    }
  }

  /**
   * Compute Android specific properties and populate properties with them.
   */
  private void configureAndroidProperties(Map<String, Object> properties) {
    properties.put(AndroidProperties.ANDROID_DETECTED, true);
    if (SonarQubePlugin.getConfiguredAndroidVariant(project) != null) {
      int minSdkVersion = getMinSdk(getVariant());
      properties.put(AndroidProperties.MIN_SDK_VERSION_MIN, minSdkVersion);
      properties.put(AndroidProperties.MIN_SDK_VERSION_MAX, minSdkVersion);
    } else {
      Set<Integer> minSdks = variants.stream()
        .map(AndroidConfig::getMinSdk)
        .collect(Collectors.toSet());
      if (!minSdks.isEmpty()) {
        properties.put(AndroidProperties.MIN_SDK_VERSION_MIN, Collections.min(minSdks));
        properties.put(AndroidProperties.MIN_SDK_VERSION_MAX, Collections.max(minSdks));
      }
    }
  }

  /**
   * Compute the JUnit test report paths for a given Android variant and populate properties with them.
   */
  private void configureTestReports(Map<String, Object> properties) {
    List<DirectoryProperty> directories = new ArrayList<>();

    getVariant().getNestedComponents().stream()
      .filter(UnitTest.class::isInstance)
      .forEach(component ->
        project.getTasks().withType(Test.class).stream()
          .filter(task -> task.getName().equals("test" + SonarUtils.capitalize(component.getName())))
          .map(task -> task.getReports().getJunitXml().getOutputLocation())
          .forEach(directories::add)
      );

    getVariant().getNestedComponents().stream()
      .filter(AndroidTest.class::isInstance)
      .forEach(component ->
        project.getTasks().withType(DeviceProviderInstrumentTestTask.class).stream()
          .filter(task -> component.getName().equals(task.getVariantName()))
          .map(DeviceProviderInstrumentTestTask::getReportsDir)
          .filter(DirectoryProperty::isPresent)
          .forEach(directories::add)
      );

    if (directories.isEmpty()) {
      return;
    }

    List<File> value = directories.stream()
      .filter(Provider::isPresent)
      .map(d -> d.get().getAsFile())
      .collect(Collectors.toList());
    properties.put(SonarProperty.JUNIT_REPORT_PATHS, value);
  }

  /**
   * Compute the Android lint report path for a given Android variant and populate properties with it.
   */
  private void configureLintReports(Map<String, Object> properties) {
    project.getTasks().withType(AndroidLintTask.class).stream()
      .filter(task -> task.getXmlReportOutputFile().isPresent())
      .filter(task -> task.getVariantName().equals(getVariant().getName()))
      .map(task -> task.getXmlReportOutputFile().get().getAsFile())
      .findFirst()
      .ifPresent(output -> properties.put(SonarProperty.ANDROID_LINT_REPORT_PATHS, output));
  }

  /**
   * Compute JDK properties and binaries for the selected Android variant.
   */
  private void configureJDK(Map<String, Object> properties, Component component, boolean isTest) {
    Optional<JavaCompile> javaCompile = getJavaCompileTask();
    if (javaCompile.isEmpty()) {
      LOGGER.warn("Unable to find Java compiler on variant '{}'.", getVariant().getName());
    } else {
      SonarUtils.populateJdkProperties(properties, JavaCompilerUtils.extractConfiguration(javaCompile.get()));
    }

    List<File> destinationDirsProvider = getCompiledClasses(project, component).get();
    if (isTest) {
      properties.put("sonar.java.test.binaries", destinationDirsProvider);
    } else {
      properties.put("sonar.java.binaries", destinationDirsProvider);
      properties.put("sonar.binaries", destinationDirsProvider);
    }
  }

  /**
   * Get the test components for the selected Android variant.
   */
  private List<Component> getTestComponents() {
    List<Component> testComponents = new ArrayList<>();
    for (Component component : getVariant().getNestedComponents()) {
      if (component instanceof TestComponent) {
        testComponents.add(component);
      }
    }
    return testComponents;
  }

  /**
   * Get the sources for a given Android component.
   */
  private FileCollection getSources(Component component) {
    Sources sources = component.getSources();
    FileCollection sourceFiles = project.files(
      sources.getJava().getAll(),
      sources.getKotlin().getAll(),
      sources.getAssets().getAll(),
      sources.getByName("c").getAll(),
      sources.getByName("cpp").getAll()
    );
    SourceDirectories.Flat aidlSources = sources.getAidl();
    if (aidlSources != null) {
      sourceFiles = sourceFiles.plus(project.files(aidlSources.getAll()));
    }
    SourceDirectories.Flat renderscriptSources = sources.getRenderscript();
    if (renderscriptSources != null) {
      sourceFiles = sourceFiles.plus(project.files(renderscriptSources.getAll()));
    }
    return sourceFiles;
  }

  private FileCollection addManifestAndRes(FileCollection files, String sourceSetName) {
    AndroidSourceSet sourceSets = (AndroidSourceSet) project.getExtensions().getByType(CommonExtension.class).getSourceSets().getByName(sourceSetName);
    Set<File> resDirectories = ((DefaultAndroidSourceDirectorySet) sourceSets.getRes()).getSrcDirs();
    return files.plus(project.files(sourceSets.getManifest().toString(), resDirectories));
  }

  /**
   * Retrieve the Java compilation task for the selected Android variant.
   */
  private Optional<JavaCompile> getJavaCompileTask() {
    return project.getTasks().withType(JavaCompile.class).stream()
      .filter(task -> task.getName().equals(getCompileTaskName(getVariant())))
      .findFirst();
  }

}
