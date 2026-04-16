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

import com.android.build.api.artifact.SingleArtifact;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.AndroidTest;
import com.android.build.api.variant.AndroidVersion;
import com.android.build.api.variant.Component;
import com.android.build.api.variant.SourceDirectories;
import com.android.build.api.variant.Sources;
import com.android.build.api.variant.TestComponent;
import com.android.build.api.variant.UnitTest;
import com.android.build.api.variant.Variant;
import com.android.build.gradle.internal.lint.AndroidLintTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.Test;
import org.sonarqube.gradle.properties.SonarProperty;

import static org.sonarqube.gradle.SonarUtils.appendSourcesProp;

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
  }

  /**
   * Populate the properties of an Android variant with Android specific value.
   */
  private void configureProperties(Map<String, Object> properties) {
    configureAndroidProperties(properties);
    configureTestReports(properties);
    configureLintReports(properties);

    if (project.getPlugins().hasPlugin("com.android.test")) {
      // Instrumentation tests only
      populateSonarQubeProps(properties, getVariant(), true);
      return;
    }
    populateSonarQubeProps(properties, getVariant(), false);
    for (Component component : getVariant().getNestedComponents()) {
      if (component instanceof TestComponent) {
        populateSonarQubeProps(properties, component, true);
      }
    }
  }

  private void configureAndroidProperties(Map<String, Object> properties) {
    properties.put(AndroidProperties.ANDROID_DETECTED, true);
    if (SonarQubePlugin.getConfiguredAndroidVariant(project) != null) {
      AndroidVersion minSdkVersion = getVariant().getMinSdk();
      properties.put(AndroidProperties.MIN_SDK_VERSION_MIN, minSdkVersion.getApiLevel());
      properties.put(AndroidProperties.MIN_SDK_VERSION_MAX, minSdkVersion.getApiLevel());
    } else {
      Set<Integer> minSdks = variants.stream()
        .map(variant -> variant.getMinSdk().getApiLevel())
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

  public void populateSonarQubeProps(Map<String, Object> properties, Component component, boolean isTest) {
    List<File> srcDirsProvider = getFilesFromSourceSet(project, component.getSources()).get();
    appendSourcesProp(properties, srcDirsProvider, isTest);

    // TODO: populate JDK properties

    Provider<List<File>> destinationDirsProvider = getCompiledClasses(project, component);
    if (isTest) {
      properties.put("sonar.java.test.binaries", destinationDirsProvider);
    } else {
      properties.put("sonar.java.binaries", destinationDirsProvider);
      properties.put("sonar.binaries", destinationDirsProvider);
    }
  }

  private Provider<List<File>> getFilesFromSourceSet(Project project, Sources sources) {
    Provider<List<File>> javaDirs = extractFlat(project, sources.getJava());
    Provider<List<File>> kotlinDirs = extractFlat(project, sources.getKotlin());
    Provider<List<File>> aidlDirs = extractFlat(project, sources.getAidl());
    Provider<List<File>> rsDirs = extractFlat(project, sources.getRenderscript());

    Provider<List<File>> resDirs = extractLayered(project, sources.getRes());
    Provider<List<File>> assetsDirs = extractLayered(project, sources.getAssets());

    Provider<List<File>> manifestFiles = getVariant().getArtifacts()
      .get(SingleArtifact.MERGED_MANIFEST.INSTANCE)
      .map(regularFile -> Collections.singletonList(regularFile.getAsFile()));

    // Dynamically resolve C/C++ via the incubating getByName API
    Provider<List<File>> cDirs = extractFlat(project, sources.getByName("c"));
    Provider<List<File>> cppDirs = extractFlat(project, sources.getByName("cpp"));

    // Zip providers together to maintain lazy evaluation
    return javaDirs
      .zip(kotlinDirs, AndroidConfig::combine)
      .zip(resDirs, AndroidConfig::combine)
      .zip(assetsDirs, AndroidConfig::combine)
      .zip(manifestFiles, AndroidConfig::combine)
      .zip(aidlDirs, AndroidConfig::combine)
      .zip(rsDirs, AndroidConfig::combine)
      .zip(cDirs, AndroidConfig::combine)
      .zip(cppDirs, AndroidConfig::combine);
  }

  private static Provider<List<File>> extractFlat(Project project, @Nullable SourceDirectories.Flat flat) {
    if (flat == null) {
      return project.provider(Collections::emptyList);
    }
    return flat.getAll().map(directories ->
      directories.stream().map(Directory::getAsFile).collect(Collectors.toList())
    );
  }

  private static Provider<List<File>> extractLayered(Project project, @Nullable SourceDirectories.Layered layered) {
    if (layered == null) {
      return project.provider(Collections::emptyList);
    }
    return layered.getAll().map(directories ->
      directories.stream()
        .flatMap(dirs -> dirs.stream().map(Directory::getAsFile))
        .collect(Collectors.toList())
    );
  }

  private static List<File> combine(List<File> list1, List<File> list2) {
    List<File> combined = new ArrayList<>(list1);
    combined.addAll(list2);
    return combined;
  }

  private static Provider<List<File>> getCompiledClasses(Project project, Component component) {
    return project.provider(() -> {
      File defaultJavaPath = project.getLayout().getBuildDirectory()
        .dir("intermediates/javac/" + component.getName() + "/classes")
        .get().getAsFile();
      return Collections.singletonList(defaultJavaPath);
    });
  }

}
