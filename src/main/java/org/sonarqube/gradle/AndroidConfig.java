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
import com.android.build.api.variant.ComponentIdentity;
import com.android.build.api.variant.HasAndroidTest;
import com.android.build.api.variant.HasUnitTest;
import com.android.build.api.variant.Variant;
import com.android.build.gradle.internal.lint.AndroidLintTask;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.Test;
import org.sonarqube.gradle.properties.SonarProperty;

/**
 * The Android configuration for a project contains properties and classpath information for all the Android variants defined in it.
 */
public class AndroidConfig {

  /**
   * Variant specific properties for the analysis of an Android project with Sonar.
   */
  private static class VariantConfig {

    private final Variant variant;
    private final Map<String, Object> properties;
    private final FileCollection mainLibraries;
    private final FileCollection testLibraries;

    public VariantConfig(Variant variant, Map<String, Object> properties, FileCollection mainLibraries, FileCollection testLibraries) {
      this.variant = variant;
      this.properties = properties;
      this.mainLibraries = mainLibraries;
      this.testLibraries = testLibraries;
    }

    public Variant getVariant() {
      return variant;
    }

    public Map<String, Object> getProperties() {
      return properties;
    }

    public FileCollection getMainLibraries() {
      return mainLibraries;
    }

    public FileCollection getTestLibraries() {
      return testLibraries;
    }

  }

  private final Project project;
  private final AndroidComponentsExtension<?, ?, ?> androidExtension;
  private final Map<String, VariantConfig> variantConfigs;

  public static AndroidConfig of(Project project) {
    AndroidComponentsExtension<?, ?, ?> androidExtension = project.getExtensions().getByType(AndroidComponentsExtension.class);
    var androidConfig = new AndroidConfig(project, androidExtension);
    androidExtension.onVariants(androidExtension.selector().all(), androidConfig::computeVariantConfig);
    return androidConfig;
  }

  private AndroidConfig(Project project, AndroidComponentsExtension<?, ?, ?> androidExtension) {
    this.project = project;
    this.androidExtension = androidExtension;
    this.variantConfigs = new HashMap<>();
  }

  public Variant getVariant() {
    return variantConfigs.get(getVariantName()).getVariant();
  }

  public Map<String, Object> getProperties() {
    return variantConfigs.get(getVariantName()).getProperties();
  }

  public FileCollection getMainLibraries() {
    return variantConfigs.get(getVariantName()).getMainLibraries();
  }

  public FileCollection getTestLibraries() {
    return variantConfigs.get(getVariantName()).getTestLibraries();
  }

  private String getVariantName() {
    List<Variant> variants = variantConfigs.values().stream()
      .map(VariantConfig::getVariant)
      .collect(Collectors.toList());

    if (variants.isEmpty()) {
      throw new IllegalStateException("No Android variant found for project " + project.getName() + ".");
    }

    String configuredVariantName = SonarQubePlugin.getConfiguredAndroidVariant(project);
    if (configuredVariantName != null) {
      if (variants.stream().map(Variant::getName).noneMatch(variantName -> variantName.equals(configuredVariantName))) {
        throw new IllegalStateException(
          "Unable to find variant '"
            + configuredVariantName
            + "' to use for SonarQube analysis. Candidates are: "
            + String.join(", ", variants.stream().map(ComponentIdentity::getName).collect(Collectors.toSet())));
      }
      return configuredVariantName;
    }

    // Find the variant that is the target for Android (integration) tests in the project. If no variant has integration tests, return the first one.
    Variant variant = variants.stream()
      .filter(v -> v.getNestedComponents().stream().anyMatch(HasAndroidTest.class::isInstance))
      .findFirst()
      .orElse(variants.get(0));

    return variant.getName();
  }

  private void computeVariantConfig(Variant variant) {
    Map<String, Object> properties = computeVariantProperties(variant);
    FileCollection mainLibraries = computeMainLibraries(variant);
    FileCollection testLibraries = computeTestLibraries(variant);
    variantConfigs.put(variant.getName(), new VariantConfig(variant, properties, mainLibraries, testLibraries));
  }

  private Map<String, Object> computeVariantProperties(Variant variant) {
    Map<String, Object> properties = new HashMap<>();
    populateAndroidProperties(properties, variant);

    configureTestReports(properties, variant);
    configureLintReports(properties, variant);

    // TODO: compute analysis properties

    return properties;
  }

  /**
   * Populate the properties of an Android variant with Android specific value.
   */
  private void populateAndroidProperties(Map<String, Object> properties, Variant variant) {
    properties.put(AndroidProperties.ANDROID_DETECTED, true);
    properties.put(AndroidProperties.MIN_SDK_VERSION_MIN, variant.getMinSdk().getApiLevel());
    properties.put(AndroidProperties.MIN_SDK_VERSION_MAX, variant.getMinSdk().getApiLevel());
  }

  /**
   * Compute the test report paths for a given Android variant and populate properties with them.
   */
  private void configureTestReports(Map<String, Object> properties, Variant variant) {
    List<File> directories = project.getTasks().withType(Test.class).stream()
      .filter(task -> task.getName().equals("test" + SonarUtils.capitalize(variant.getName()) + "UnitTest"))
      .map(task -> task.getReports().getJunitXml().getOutputLocation().getAsFile())
      .filter(Provider::isPresent)
      .map(Provider::get)
      .collect(Collectors.toList());

    Provider<Directory> androidTestDirectory = project.getLayout().getBuildDirectory().dir("outputs/androidTest-results/connected");
    if (androidTestDirectory.isPresent()) {
      directories.add(androidTestDirectory.get().getAsFile());
    }

    properties.put(SonarProperty.JUNIT_REPORT_PATHS, directories);
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

  /**
   * Compute the file collection for the libraries used in an Android variant.
   */
  private FileCollection computeMainLibraries(Variant variant) {
    FileCollection mainLibraries = project.files(androidExtension.getSdkComponents().getBootClasspath());
    mainLibraries.plus(project.getConfigurations().getByName(variant.getName() + "CompileClasspath").getIncoming().getFiles());
    return mainLibraries;
  }

  /**
   * Compute the file collection for the libraries used in the test components of an Android variant.
   */
  private FileCollection computeTestLibraries(Variant variant) {
    FileCollection testLibraries = project.files(androidExtension.getSdkComponents().getBootClasspath());
    variant.getNestedComponents().forEach(component -> {
      if (component instanceof HasUnitTest || component instanceof HasAndroidTest) {
        testLibraries.plus(project.getConfigurations().getByName(component.getName() + "CompileClasspath").getIncoming().getFiles());
      }
    });
    return testLibraries;
  }

}
