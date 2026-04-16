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

import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.AndroidTest;
import com.android.build.api.variant.Component;
import com.android.build.api.variant.TestComponent;
import com.android.build.api.variant.UnitTest;
import com.android.build.api.variant.Variant;
import com.android.build.gradle.internal.lint.AndroidLintTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.Test;
import org.sonarqube.gradle.properties.SonarProperty;

/**
 * The Android configuration for a project contains properties and classpath information for all the Android variants defined in it.
 */
public class AndroidConfig {

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

  private static boolean addTaskByName(Set<Task> taskSet, String name, Project project) {
    try {
      taskSet.add(project.getTasks().getByName(name));
      return true;
    } catch (UnknownTaskException e) {
      return false;
    }
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
    FileCollection testLibraries = project.files();
    boolean foundTestComponent = false;
    for (Component component : getVariant().getNestedComponents()) {
      if (component instanceof TestComponent) {
        foundTestComponent = true;
        testLibraries = testLibraries.plus(getCompileClasspath(component));
      }
    }
    if (foundTestComponent) {
      testLibraries = testLibraries.plus(project.files(androidComponentsExtension.getSdkComponents().getBootClasspath()));
    }
    return testLibraries;
  }

  /**
   * Get the Android tasks on which Sonar tasks need to depend for the variant selected for the analysis with Sonar.
   */
  public Set<Task> getTasks() {
    String variantName = SonarUtils.capitalize(getVariant().getName());
    String compileTaskPrefix = "compile" + variantName;
    Set<Task> tasks = new HashSet<>();

    boolean unitTestTaskAdded = addTaskByName(tasks, compileTaskPrefix + "UnitTestJavaWithJavac", project);
    boolean androidTestTaskAdded = addTaskByName(tasks, compileTaskPrefix + "AndroidTestJavaWithJavac", project);
    // The compilation of unit tests or Android tests already depends on the main compilation task, so it is only necessary to add it if no test compilation tasks were found.
    if (!unitTestTaskAdded && !androidTestTaskAdded) {
      addTaskByName(tasks, compileTaskPrefix + "JavaWithJavac", project);
    }
    addTaskByName(tasks, "test" + variantName + "UnitTest", project);

    return tasks;
  }

  private FileCollection getCompileClasspath(Component component) {
    try {
      java.lang.reflect.Method method = component.getClass().getMethod("getCompileClasspath");
      return (FileCollection) method.invoke(component);
    } catch (NoSuchMethodException e) {
      String configName = component.getName() + "CompileClasspath";
      Configuration configuration = project.getConfigurations().getByName(configName);

      return configuration.getIncoming().artifactView(viewConfiguration -> viewConfiguration.attributes(attributeContainer -> {
        // Explicitly request the Android-optimized classes JAR to prevent ArtifactSelectionException
        attributeContainer.attribute(
          Attribute.of("artifactType", String.class),
          "android-classes-jar"
        );
        // Explicitly request the Android JVM environment to satisfy missing target environment attributes
        attributeContainer.attribute(
          TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
          project.getObjects().named(TargetJvmEnvironment.class, TargetJvmEnvironment.ANDROID)
        );
      })).getFiles();

    } catch (Exception e) {
      throw new IllegalStateException("Failed to compute compile classpath for variant: " + component.getName(), e);
    }
  }

  /**
   * Populate the properties of an Android variant with Android specific value.
   */
  private void configureProperties(Map<String, Object> properties, Variant variant) {
    properties.put(AndroidProperties.ANDROID_DETECTED, true);
    properties.put(AndroidProperties.MIN_SDK_VERSION_MIN, variant.getMinSdk().getApiLevel());
    properties.put(AndroidProperties.MIN_SDK_VERSION_MAX, variant.getMinSdk().getApiLevel());
  }

  /**
   * Compute the JUnit test report paths for a given Android variant and populate properties with them.
   */
  private void configureTestReports(Map<String, Object> properties, Variant variant) {
    List<DirectoryProperty> directories = new ArrayList<>();

    variant.getNestedComponents().stream()
      .filter(UnitTest.class::isInstance)
      .forEach(component ->
        project.getTasks().withType(Test.class).stream()
          .filter(task -> task.getName().equals("test" + SonarUtils.capitalize(component.getName())))
          .map(task -> task.getReports().getJunitXml().getOutputLocation())
          .forEach(directories::add)
      );

    variant.getNestedComponents().stream()
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
  private void configureLintReports(Map<String, Object> properties, Variant variant) {
    project.getTasks().withType(AndroidLintTask.class).stream()
      .filter(task -> task.getXmlReportOutputFile().isPresent())
      .filter(task -> task.getVariantName().equals(variant.getName()))
      .map(task -> task.getXmlReportOutputFile().get().getAsFile())
      .findFirst()
      .ifPresent(output -> properties.put(SonarProperty.ANDROID_LINT_REPORT_PATHS, output));
  }

}
