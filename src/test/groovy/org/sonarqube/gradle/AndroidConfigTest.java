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

import com.android.build.api.dsl.CommonExtension;
import com.android.build.api.dsl.SdkComponents;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.AndroidTest;
import com.android.build.api.variant.AndroidVersion;
import com.android.build.api.variant.SourceDirectories;
import com.android.build.api.variant.Sources;
import com.android.build.api.variant.UnitTest;
import com.android.build.api.variant.Variant;
import com.android.build.api.variant.VariantSelector;
import com.android.build.gradle.internal.lint.AndroidLintTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.JUnitXmlReport;
import org.gradle.api.tasks.testing.TestTaskReports;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarqube.gradle.properties.SonarProperty;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AndroidConfigTest {

  private Project project;
  private ExtensionContainer extensionContainer;
  private AndroidComponentsExtension<?, ?, ?> androidExt;
  private SonarExtension sonarExtension;
  private TaskContainer taskContainer;

  @BeforeEach
  @SuppressWarnings({"unchecked", "rawtypes"})
  void setup() {
    project = mock(Project.class);
    extensionContainer = mock(ExtensionContainer.class);
    taskContainer = mock(TaskContainer.class);
    ProjectLayout projectLayout = mock(ProjectLayout.class);
    DirectoryProperty directoryProperty = mock(DirectoryProperty.class);
    when(project.getExtensions()).thenReturn(extensionContainer);
    when(project.getTasks()).thenReturn(taskContainer);
    when(project.getName()).thenReturn("testProject");
    when(project.getLayout()).thenReturn(projectLayout);
    when(projectLayout.getBuildDirectory()).thenReturn(directoryProperty);

    // Mock project.provider() so that getCompiledClasses() in configureJDK() does not require a real Gradle project. The callable inside is never executed; the provider simply
    // returns an empty list when its value is resolved.
    Provider<?> classesProvider = mock(Provider.class);
    when(classesProvider.get()).thenReturn(Collections.emptyList());
    when(project.provider(any())).thenReturn((Provider) classesProvider);

    // Mock PluginContainer for the com.android.test check inside configureProperties().
    PluginContainer pluginContainer = mock(PluginContainer.class);
    when(project.getPlugins()).thenReturn(pluginContainer);
    when(pluginContainer.hasPlugin("com.android.test")).thenReturn(false);

    // SonarExtension: controls what SonarQubePlugin.getConfiguredAndroidVariant() returns.
    sonarExtension = mock(SonarExtension.class);
    when(sonarExtension.getAndroidVariant()).thenReturn(null);
    when(extensionContainer.getByName("sonar")).thenReturn(sonarExtension);
    when(extensionContainer.getByName("sonarqube")).thenReturn(sonarExtension);

    // AndroidComponentsExtension and its selector (both used in AndroidConfig.of()).
    androidExt = mock(AndroidComponentsExtension.class);
    when(extensionContainer.getByType(AndroidComponentsExtension.class)).thenReturn(androidExt);
    VariantSelector selector = mock(VariantSelector.class);
    when(androidExt.selector()).thenReturn(selector);
    when(selector.all()).thenReturn(selector);

    // Default empty task collections to satisfy withType() calls inside configureTestReports(), configureLintReports(), and configureJDK().
    TaskCollection<AndroidLintTask> lintTasks = mock(TaskCollection.class);
    when(lintTasks.stream()).thenReturn(Stream.empty());
    when(taskContainer.withType(AndroidLintTask.class)).thenReturn(lintTasks);

    TaskCollection<DeviceProviderInstrumentTestTask> instrumentTestTasks = mock(TaskCollection.class);
    when(instrumentTestTasks.stream()).thenReturn(Stream.empty());
    when(taskContainer.withType(DeviceProviderInstrumentTestTask.class)).thenReturn(instrumentTestTasks);

    TaskCollection<JavaCompile> javaCompileTasks = mock(TaskCollection.class);
    // thenAnswer is used instead of thenReturn so that a fresh stream is created on every call.
    // Stream is a one-shot object; thenReturn would return the same already-consumed instance on the second call.
    when(javaCompileTasks.stream()).thenAnswer(inv -> Stream.empty());
    when(taskContainer.withType(JavaCompile.class)).thenReturn(javaCompileTasks);
  }

  /**
   * Fake source-set types used in the legacy-fallback test for Android test sources. The production code accesses getJava() and getSrcDirs() via reflection, so the types just
   * need to have those methods without having to implement the full AGP interfaces.
   */
  static class FakeSourceDirectorySet {
    public Set<File> getSrcDirs() {
      return Set.of(new File("src/test/java"));
    }
  }

  static class FakeAndroidSourceSet {
    public FakeSourceDirectorySet getJava() {
      return new FakeSourceDirectorySet();
    }
  }

  /**
   * Stubs androidExt.onVariants() to immediately invoke the callback with the given variants.
   * This simulates the AGP callback mechanism that populates AndroidConfig's variants list.
   */
  private void stubOnVariants(Variant... variants) {
    doAnswer(inv -> {
      Action<Variant> action = inv.getArgument(1);
      for (Variant v : variants) {
        action.execute(v);
      }
      return null;
    }).when(androidExt).onVariants(any(), any(Action.class));
  }

  /**
   * Creates a Variant mock with the given name, minSdk API level, and optionally an AndroidTest nested component.
   */
  private Variant mockVariant(String name, int minSdkApiLevel, boolean hasAndroidTest) {
    Variant variant = mock(Variant.class);
    when(variant.getName()).thenReturn(name);

    AndroidVersion minSdk = mock(AndroidVersion.class);
    when(minSdk.getApiLevel()).thenReturn(minSdkApiLevel);
    when(variant.getMinSdk()).thenReturn(minSdk);

    if (hasAndroidTest) {
      AndroidTest androidTest = mock(AndroidTest.class);
      when(androidTest.getName()).thenReturn(name + "AndroidTest");
      when(variant.getNestedComponents()).thenReturn(List.of(androidTest));
    } else {
      when(variant.getNestedComponents()).thenReturn(Collections.emptyList());
    }

    return variant;
  }

  /**
   * Wires project.getObjects().fileCollection() to return a ConfigurableFileCollection mock.
   * Required by getSources(), which creates the collection via the ObjectFactory.
   */
  private ConfigurableFileCollection mockObjectFactory() {
    ObjectFactory objectFactory = mock(ObjectFactory.class);
    ConfigurableFileCollection fileCollection = mock(ConfigurableFileCollection.class);
    when(project.getObjects()).thenReturn(objectFactory);
    when(objectFactory.fileCollection()).thenReturn(fileCollection);
    return fileCollection;
  }

  /**
   * Stubs project.files() to return a ConfigurableFileCollection mock.
   * Required by getAndroidTests(), which initialises an empty collection via project.files().
   */
  private ConfigurableFileCollection mockProjectFiles() {
    ConfigurableFileCollection emptyFC = mock(ConfigurableFileCollection.class);
    when(project.files()).thenReturn(emptyFC);
    return emptyFC;
  }

  /**
   * Wires up the CommonExtension and NamedDomainObjectContainer chain so that getAndroidTests() can reach the legacy "test" source set.
   */
  private void stubLegacySourceSet(Object testSourceSet) {
    CommonExtension<?, ?, ?, ?, ?> commonExt = mock(CommonExtension.class);
    NamedDomainObjectContainer<?> sourceSets = mock(NamedDomainObjectContainer.class);
    when(extensionContainer.getByType(CommonExtension.class)).thenReturn(commonExt);
    doReturn(sourceSets).when(commonExt).getSourceSets();
    doReturn(testSourceSet).when(sourceSets).findByName("test");
  }

  /**
   * Stubs the SdkComponents → getBootClasspath() → project.files() chain used by getMainLibraries().
   */
  private ConfigurableFileCollection stubBootClasspath() {
    SdkComponents sdkComponents = mock(SdkComponents.class);
    when(androidExt.getSdkComponents()).thenReturn(sdkComponents);
    Provider<?> bootProvider = mock(Provider.class);
    when(sdkComponents.getBootClasspath()).thenReturn((Provider) bootProvider);
    ConfigurableFileCollection bootFC = mock(ConfigurableFileCollection.class);
    when(project.files(bootProvider)).thenReturn(bootFC);
    return bootFC;
  }

  /**
   * Stubs the ConfigurationContainer → Configuration → ResolvableDependencies → ArtifactView → getFiles() chain used by getCompileClasspath() inside getMainLibraries() and
   * getTestLibraries(). Also stubs project.getObjects().named() which is called inside the ArtifactView configuration lambda.
   */
  private FileCollection stubCompileClasspath(String configName) {
    ConfigurationContainer configurations = mock(ConfigurationContainer.class);
    when(project.getConfigurations()).thenReturn(configurations);

    Configuration configuration = mock(Configuration.class);
    when(configurations.getByName(configName)).thenReturn(configuration);

    ResolvableDependencies incoming = mock(ResolvableDependencies.class);
    when(configuration.getIncoming()).thenReturn(incoming);

    ArtifactView artifactView = mock(ArtifactView.class);
    when(incoming.artifactView(any())).thenReturn(artifactView);

    FileCollection compileFC = mock(FileCollection.class);
    when(artifactView.getFiles()).thenReturn(compileFC);

    ObjectFactory objectFactory = mock(ObjectFactory.class);
    when(project.getObjects()).thenReturn(objectFactory);
    TargetJvmEnvironment env = mock(TargetJvmEnvironment.class);
    when(objectFactory.named(eq(TargetJvmEnvironment.class), any())).thenReturn(env);

    return compileFC;
  }

  /**
   * Creates a Variant mock with a single UnitTest nested component.
   * UnitTest implements TestComponent, so getTestComponents() will include it.
   */
  private Variant mockVariantWithUnitTest(String name) {
    Variant variant = mock(Variant.class);
    when(variant.getName()).thenReturn(name);
    AndroidVersion minSdk = mock(AndroidVersion.class);
    when(minSdk.getApiLevel()).thenReturn(28);
    when(variant.getMinSdk()).thenReturn(minSdk);

    UnitTest unitTest = mock(UnitTest.class);
    when(unitTest.getName()).thenReturn(name + "UnitTest");
    when(variant.getNestedComponents()).thenReturn(List.of(unitTest));
    return variant;
  }

  /**
   * Creates a Variant mock with both a UnitTest and an AndroidTest nested component.
   */
  private Variant mockVariantWithUnitTestAndAndroidTest(String name) {
    Variant variant = mock(Variant.class);
    when(variant.getName()).thenReturn(name);
    AndroidVersion minSdk = mock(AndroidVersion.class);
    when(minSdk.getApiLevel()).thenReturn(28);
    when(variant.getMinSdk()).thenReturn(minSdk);

    UnitTest unitTest = mock(UnitTest.class);
    when(unitTest.getName()).thenReturn(name + "UnitTest");
    AndroidTest androidTest = mock(AndroidTest.class);
    when(androidTest.getName()).thenReturn(name + "AndroidTest");
    when(variant.getNestedComponents()).thenReturn(List.of(unitTest, androidTest));
    return variant;
  }

  @Test
  void configureProperties_androidPropertiesAreSet_whenVariantNotConfigured() {
    Variant release = mockVariant("release", 21, false);
    Variant debug = mockVariant("debug", 28, false);
    stubOnVariants(release, debug);

    Map<String, Object> props = new HashMap<>();
    AndroidConfig.of(project).configureProperties(props);

    assertEquals(true, props.get("sonar.android.detected"));
    assertEquals(21, props.get("sonar.android.minsdkversion.min"));
    assertEquals(28, props.get("sonar.android.minsdkversion.max"));
  }

  @Test
  void configureProperties_androidPropertiesAreSet_whenVariantConfigured() {
    when(sonarExtension.getAndroidVariant()).thenReturn("debug");
    stubOnVariants(mockVariant("debug", 30, false));

    Map<String, Object> props = new HashMap<>();
    AndroidConfig.of(project).configureProperties(props);

    assertEquals(true, props.get("sonar.android.detected"));
    assertEquals(30, props.get("sonar.android.minsdkversion.min"));
    assertEquals(30, props.get("sonar.android.minsdkversion.max"));
  }

  @Test
  void getVariant_throwsIllegalStateException_whenNoVariantsFound() {
    stubOnVariants();
    AndroidConfig config = AndroidConfig.of(project);

    assertThrows(IllegalStateException.class, config::getVariant);
  }

  @Test
  void getVariant_throwsIllegalStateException_whenConfiguredVariantNotFound() {
    when(sonarExtension.getAndroidVariant()).thenReturn("nonExistent");
    stubOnVariants(mockVariant("release", 21, false));
    AndroidConfig config = AndroidConfig.of(project);

    IllegalStateException exception = assertThrows(IllegalStateException.class, config::getVariant);
    assertTrue(exception.getMessage().contains("nonExistent"));
    assertTrue(exception.getMessage().contains("release"));
  }

  @Test
  void getVariant_prefersVariantWithAndroidTests_whenNoVariantConfigured() {
    Variant release = mockVariant("release", 21, false);
    // This variant has an AndroidTest nested component and should thus be selected by the scanner.
    Variant debug = mockVariant("debug", 28, true);
    stubOnVariants(release, debug);

    assertEquals("debug", AndroidConfig.of(project).getVariant().getName());
  }

  @Test
  void configureLintReports_setsLintReportPath_whenLintTaskMatchesVariant() {
    stubOnVariants(mockVariant("debug", 28, false));

    File lintFile = new File("lint-results.xml");
    AndroidLintTask lintTask = mock(AndroidLintTask.class);
    when(lintTask.getVariantName()).thenReturn("debug");
    RegularFileProperty lintReport = mock(RegularFileProperty.class);
    RegularFile regularFile = mock(RegularFile.class);
    when(lintReport.isPresent()).thenReturn(true);
    when(lintReport.get()).thenReturn(regularFile);
    when(regularFile.getAsFile()).thenReturn(lintFile);
    when(lintTask.getXmlReportOutputFile()).thenReturn(lintReport);

    TaskCollection<AndroidLintTask> lintTasks = mock(TaskCollection.class);
    when(lintTasks.stream()).thenReturn(Stream.of(lintTask));
    when(taskContainer.withType(AndroidLintTask.class)).thenReturn(lintTasks);

    Map<String, Object> props = new HashMap<>();
    AndroidConfig.of(project).configureProperties(props);

    assertEquals(lintFile, props.get(SonarProperty.ANDROID_LINT_REPORT_PATHS));
  }

  @Test
  void configureLintReports_doesNotSetLintReportPath_whenLintTaskBelongsToDifferentVariant() {
    stubOnVariants(mockVariant("debug", 28, false));

    AndroidLintTask lintTask = mock(AndroidLintTask.class);
    // The lint task is set for a different variant than the selected "debug", its report should therefore not be picked up by the scanner.
    when(lintTask.getVariantName()).thenReturn("release");
    RegularFileProperty lintReport = mock(RegularFileProperty.class);
    when(lintReport.isPresent()).thenReturn(true);
    when(lintTask.getXmlReportOutputFile()).thenReturn(lintReport);

    TaskCollection<AndroidLintTask> lintTasks = mock(TaskCollection.class);
    when(lintTasks.stream()).thenReturn(Stream.of(lintTask));
    when(taskContainer.withType(AndroidLintTask.class)).thenReturn(lintTasks);

    Map<String, Object> props = new HashMap<>();
    AndroidConfig.of(project).configureProperties(props);

    assertFalse(props.containsKey(SonarProperty.ANDROID_LINT_REPORT_PATHS));
  }

  @Test
  void getAndroidSources_returnsFileCollection_forSelectedVariant() {
    Variant debug = mockVariant("debug", 28, false);
    stubOnVariants(debug);
    ConfigurableFileCollection fileCollection = mockObjectFactory();
    when(debug.getSources()).thenReturn(mock(Sources.class));

    FileCollection result = AndroidConfig.of(project).getAndroidSources();

    assertSame(fileCollection, result);
  }

  @Test
  void getAndroidSources_addsNonNullSourceDirectories_toFileCollection() {
    Variant debug = mockVariant("debug", 28, false);
    stubOnVariants(debug);
    ConfigurableFileCollection fileCollection = mockObjectFactory();

    Sources sources = mock(Sources.class);
    when(debug.getSources()).thenReturn(sources);

    SourceDirectories.Flat javaDirs = mock(SourceDirectories.Flat.class);
    Provider javaProvider = mock(Provider.class);
    when(sources.getJava()).thenReturn(javaDirs);
    when(javaDirs.getAll()).thenReturn(javaProvider);

    SourceDirectories.Flat kotlinDirs = mock(SourceDirectories.Flat.class);
    Provider kotlinProvider = mock(Provider.class);
    when(sources.getKotlin()).thenReturn(kotlinDirs);
    when(kotlinDirs.getAll()).thenReturn(kotlinProvider);

    SourceDirectories.Layered assetsDirs = mock(SourceDirectories.Layered.class);
    Provider assetsProvider = mock(Provider.class);
    when(sources.getAssets()).thenReturn(assetsDirs);
    when(assetsDirs.getAll()).thenReturn(assetsProvider);

    SourceDirectories.Layered resDirs = mock(SourceDirectories.Layered.class);
    Provider resProvider = mock(Provider.class);
    when(sources.getRes()).thenReturn(resDirs);
    when(resDirs.getAll()).thenReturn(resProvider);

    SourceDirectories.Flat aidlDirs = mock(SourceDirectories.Flat.class);
    Provider aidlProvider = mock(Provider.class);
    when(sources.getAidl()).thenReturn(aidlDirs);
    when(aidlDirs.getAll()).thenReturn(aidlProvider);

    AndroidConfig.of(project).getAndroidSources();

    verify(fileCollection).from(javaProvider);
    verify(fileCollection).from(kotlinProvider);
    verify(fileCollection).from(assetsProvider);
    verify(fileCollection).from(resProvider);
    verify(fileCollection).from(aidlProvider);
  }

  @Test
  void getAndroidSources_skipsNullSourceTypes_withoutThrowingException() {
    Variant debug = mock(Variant.class);
    when(debug.getName()).thenReturn("debug");
    AndroidVersion minSdk = mock(AndroidVersion.class);
    when(minSdk.getApiLevel()).thenReturn(28);
    when(debug.getMinSdk()).thenReturn(minSdk);
    when(debug.getNestedComponents()).thenReturn(Collections.emptyList());
    stubOnVariants(debug);
    mockObjectFactory();

    Sources sources = mock(Sources.class);
    when(debug.getSources()).thenReturn(sources);

    assertDoesNotThrow(() -> AndroidConfig.of(project).getAndroidSources());
  }

  @Test
  void getAndroidSources_ignoresCppSources_whenGetByNameThrows() {
    Variant debug = mockVariant("debug", 28, false);
    stubOnVariants(debug);
    mockObjectFactory();

    Sources sources = mock(Sources.class);
    when(debug.getSources()).thenReturn(sources);
    when(sources.getByName(any())).thenThrow(new RuntimeException("C/C++ sources not available"));

    // The exception is swallowed by the try/catch in getSources() and must not propagate
    assertDoesNotThrow(() -> AndroidConfig.of(project).getAndroidSources());
  }

  @Test
  void getAndroidSources_usesSelectedVariant_notNestedTestComponents() {
    Variant debug = mockVariant("debug", 28, true);
    stubOnVariants(debug);
    mockObjectFactory();

    Sources debugSources = mock(Sources.class);
    when(debug.getSources()).thenReturn(debugSources);

    AndroidTest androidTest = (AndroidTest) debug.getNestedComponents().get(0);

    AndroidConfig.of(project).getAndroidSources();

    verify(debug, atLeastOnce()).getSources();
    verify(androidTest, never()).getSources();
  }

  @Test
  void getAndroidTests_returnsFileCollection_forTestComponents() {
    Variant debug = mockVariant("debug", 28, true);
    stubOnVariants(debug);
    ConfigurableFileCollection fileCollection = mockObjectFactory();
    ConfigurableFileCollection emptyFC = mockProjectFiles();
    AndroidTest androidTest = (AndroidTest) debug.getNestedComponents().get(0);
    when(androidTest.getSources()).thenReturn(mock(Sources.class));

    FileCollection resultFC = mock(FileCollection.class);
    when(emptyFC.plus(fileCollection)).thenReturn(resultFC);

    FileCollection result = AndroidConfig.of(project).getAndroidTests();

    assertSame(resultFC, result);
  }

  @Test
  void getAndroidTests_addsNonNullSourceDirectories_toFileCollection() {
    Variant debug = mockVariant("debug", 28, true);
    stubOnVariants(debug);
    ConfigurableFileCollection fileCollection = mockObjectFactory();
    ConfigurableFileCollection emptyFC = mockProjectFiles();
    when(emptyFC.plus(any())).thenReturn(mock(FileCollection.class));

    AndroidTest androidTest = (AndroidTest) debug.getNestedComponents().get(0);
    Sources sources = mock(Sources.class);
    when(androidTest.getSources()).thenReturn(sources);

    SourceDirectories.Flat javaDirs = mock(SourceDirectories.Flat.class);
    Provider javaProvider = mock(Provider.class);
    when(sources.getJava()).thenReturn(javaDirs);
    when(javaDirs.getAll()).thenReturn(javaProvider);

    AndroidConfig.of(project).getAndroidTests();

    verify(fileCollection).from(javaProvider);
  }

  @Test
  void getAndroidTests_skipsNullSourceTypes_withoutThrowingException() {
    Variant debug = mockVariant("debug", 28, true);
    stubOnVariants(debug);
    mockObjectFactory();
    ConfigurableFileCollection emptyFC = mockProjectFiles();
    when(emptyFC.plus(any())).thenReturn(mock(FileCollection.class));

    AndroidTest androidTest = (AndroidTest) debug.getNestedComponents().get(0);
    when(androidTest.getSources()).thenReturn(mock(Sources.class));

    assertDoesNotThrow(() -> AndroidConfig.of(project).getAndroidTests());
  }

  @Test
  void getAndroidTests_ignoresCppSources_whenGetByNameThrows() {
    Variant debug = mockVariant("debug", 28, true);
    stubOnVariants(debug);
    mockObjectFactory();
    ConfigurableFileCollection emptyFC = mockProjectFiles();
    when(emptyFC.plus(any())).thenReturn(mock(FileCollection.class));

    AndroidTest androidTest = (AndroidTest) debug.getNestedComponents().get(0);
    Sources sources = mock(Sources.class);
    when(androidTest.getSources()).thenReturn(sources);
    when(sources.getByName(any())).thenThrow(new RuntimeException("C/C++ sources not available"));

    // The exception is caught by getSources() and indicates that no C or C++ sources were found, which is why ot shouldn't raise.
    assertDoesNotThrow(() -> AndroidConfig.of(project).getAndroidTests());
  }

  @Test
  void getAndroidTests_usesTestComponents_notSelectedVariant() {
    Variant debug = mockVariant("debug", 28, true);
    stubOnVariants(debug);
    mockObjectFactory();
    ConfigurableFileCollection emptyFC = mockProjectFiles();
    when(emptyFC.plus(any())).thenReturn(mock(FileCollection.class));

    Sources debugSources = mock(Sources.class);
    when(debug.getSources()).thenReturn(debugSources);

    AndroidTest androidTest = (AndroidTest) debug.getNestedComponents().get(0);
    when(androidTest.getSources()).thenReturn(mock(Sources.class));

    AndroidConfig.of(project).getAndroidTests();

    verify(androidTest, atLeastOnce()).getSources();
    verify(debug, never()).getSources();
  }

  @Test
  void getAndroidTests_returnsEmptyFileCollection_whenNoTestComponentsAndNoSourceSet() {
    stubOnVariants(mockVariant("debug", 28, false));
    stubLegacySourceSet(null);
    ConfigurableFileCollection emptyFC = mockProjectFiles();

    FileCollection result = AndroidConfig.of(project).getAndroidTests();

    assertSame(emptyFC, result);
  }

  @Test
  void getAndroidTests_usesLegacySourceSetSrcDirs_whenNoTestComponents() {
    stubOnVariants(mockVariant("debug", 28, false));
    stubLegacySourceSet(new FakeAndroidSourceSet());

    // project.files() is called at the top of getAndroidTests() but its result is discarded for this branch. project.files(srcDirs) is what the method actually returns.
    mockProjectFiles();
    ConfigurableFileCollection resultFC = mock(ConfigurableFileCollection.class);
    when(project.files(any())).thenReturn(resultFC);

    FileCollection result = AndroidConfig.of(project).getAndroidTests();

    assertSame(resultFC, result);
  }

  @Test
  void getAndroidTests_returnsEmptyFileCollection_whenReflectionFails() {
    stubOnVariants(mockVariant("debug", 28, false));
    // A plain Object has no getJava() → NoSuchMethodException is caught → an empty collection is returned.
    stubLegacySourceSet(new Object());
    ConfigurableFileCollection emptyFC = mockProjectFiles();

    assertDoesNotThrow(() -> {
      FileCollection result = AndroidConfig.of(project).getAndroidTests();
      assertSame(emptyFC, result);
    });
  }

  @Test
  void getAndroidTests_combinesSourcesFromMultipleTestComponents() {
    Variant debug = mock(Variant.class);
    when(debug.getName()).thenReturn("debug");
    AndroidVersion minSdk = mock(AndroidVersion.class);
    when(minSdk.getApiLevel()).thenReturn(28);
    when(debug.getMinSdk()).thenReturn(minSdk);

    UnitTest unitTest = mock(UnitTest.class);
    AndroidTest androidTest = mock(AndroidTest.class);
    when(unitTest.getSources()).thenReturn(mock(Sources.class));
    when(androidTest.getSources()).thenReturn(mock(Sources.class));
    when(debug.getNestedComponents()).thenReturn(List.of(unitTest, androidTest));

    stubOnVariants(debug);
    mockObjectFactory();

    ConfigurableFileCollection fc0 = mockProjectFiles();
    FileCollection fc1 = mock(FileCollection.class);
    FileCollection fc2 = mock(FileCollection.class);
    when(fc0.plus(any())).thenReturn(fc1);
    when(fc1.plus(any())).thenReturn(fc2);

    FileCollection result = AndroidConfig.of(project).getAndroidTests();

    assertSame(fc2, result);
  }

  @Test
  void getMainLibraries_returnsUnionOfBootClasspathAndCompileClasspath() {
    stubOnVariants(mockVariant("debug", 28, false));
    ConfigurableFileCollection bootFC = stubBootClasspath();
    FileCollection compileFC = stubCompileClasspath("debugCompileClasspath");

    FileCollection resultFC = mock(FileCollection.class);
    when(bootFC.plus(compileFC)).thenReturn(resultFC);

    FileCollection result = AndroidConfig.of(project).getMainLibraries();

    assertSame(resultFC, result);
  }

  @Test
  void getMainLibraries_usesVariantCompileClasspathConfiguration() {
    stubOnVariants(mockVariant("release", 21, false));
    stubBootClasspath();
    ConfigurationContainer configurations = mock(ConfigurationContainer.class);
    when(project.getConfigurations()).thenReturn(configurations);

    Configuration configuration = mock(Configuration.class);
    when(configurations.getByName(any())).thenReturn(configuration);
    ResolvableDependencies incoming = mock(ResolvableDependencies.class);
    when(configuration.getIncoming()).thenReturn(incoming);
    ArtifactView artifactView = mock(ArtifactView.class);
    when(incoming.artifactView(any())).thenReturn(artifactView);
    when(artifactView.getFiles()).thenReturn(mock(FileCollection.class));
    ObjectFactory objectFactory = mock(ObjectFactory.class);
    when(project.getObjects()).thenReturn(objectFactory);
    when(objectFactory.named(eq(TargetJvmEnvironment.class), any())).thenReturn(mock(TargetJvmEnvironment.class));

    ConfigurableFileCollection bootFC = mock(ConfigurableFileCollection.class);
    when(bootFC.plus(any())).thenReturn(mock(FileCollection.class));
    when(project.files(any())).thenReturn(bootFC);

    AndroidConfig.of(project).getMainLibraries();

    verify(configurations).getByName("releaseCompileClasspath");
  }

  @Test
  void getMainLibraries_configuresArtifactViewWithAndroidClassesJarAttribute() {
    stubOnVariants(mockVariant("debug", 28, false));
    stubBootClasspath();

    // Wire up configurations and incoming as usual
    ConfigurationContainer configurations = mock(ConfigurationContainer.class);
    when(project.getConfigurations()).thenReturn(configurations);
    Configuration configuration = mock(Configuration.class);
    when(configurations.getByName(any())).thenReturn(configuration);
    ResolvableDependencies incoming = mock(ResolvableDependencies.class);
    when(configuration.getIncoming()).thenReturn(incoming);

    // Capture and execute the ArtifactView configuration action to verify attribute setup
    ArtifactView artifactView = mock(ArtifactView.class);
    when(artifactView.getFiles()).thenReturn(mock(FileCollection.class));
    AttributeContainer attrs = mock(AttributeContainer.class);
    when(attrs.attribute(any(), any())).thenReturn(attrs);
    ArtifactView.ViewConfiguration viewConfig = mock(ArtifactView.ViewConfiguration.class);
    doAnswer(inv -> {
      ((Action<AttributeContainer>) inv.getArgument(0)).execute(attrs);
      return null;
    }).when(viewConfig).attributes(any());
    doAnswer(inv -> {
      ((Action<ArtifactView.ViewConfiguration>) inv.getArgument(0)).execute(viewConfig);
      return artifactView;
    }).when(incoming).artifactView(any());

    ObjectFactory objectFactory = mock(ObjectFactory.class);
    when(project.getObjects()).thenReturn(objectFactory);
    when(objectFactory.named(eq(TargetJvmEnvironment.class), any())).thenReturn(mock(TargetJvmEnvironment.class));

    ConfigurableFileCollection bootFC = mock(ConfigurableFileCollection.class);
    when(bootFC.plus(any())).thenReturn(mock(FileCollection.class));
    when(project.files(any())).thenReturn(bootFC);

    AndroidConfig.of(project).getMainLibraries();

    verify(attrs).attribute(Attribute.of("artifactType", String.class), "android-classes-jar");
  }

  @Test
  void getTestLibraries_returnsEmptyFileCollection_whenNoTestComponents() {
    // mockVariant with hasAndroidTest=false has getNestedComponents() returning an empty list,
    // so getTestComponents() finds nothing and the early-return branch fires.
    stubOnVariants(mockVariant("debug", 28, false));
    ConfigurableFileCollection emptyFC = mockProjectFiles();

    FileCollection result = AndroidConfig.of(project).getTestLibraries();

    assertSame(emptyFC, result);
  }

  @Test
  void getTestLibraries_returnsBootAndComponentCompileClasspath_whenOneTestComponent() {
    Variant debug = mock(Variant.class);
    when(debug.getName()).thenReturn("debug");
    AndroidVersion minSdk = mock(AndroidVersion.class);
    when(minSdk.getApiLevel()).thenReturn(28);
    when(debug.getMinSdk()).thenReturn(minSdk);
    UnitTest unitTest = mock(UnitTest.class);
    when(unitTest.getName()).thenReturn("debugUnitTest");
    when(debug.getNestedComponents()).thenReturn(List.of(unitTest));
    stubOnVariants(debug);

    ConfigurableFileCollection bootFC = stubBootClasspath();
    FileCollection unitTestFC = stubCompileClasspath("debugUnitTestCompileClasspath");
    FileCollection resultFC = mock(FileCollection.class);
    when(bootFC.plus(unitTestFC)).thenReturn(resultFC);

    FileCollection result = AndroidConfig.of(project).getTestLibraries();

    assertSame(resultFC, result);
  }

  @Test
  void getTestLibraries_usesComponentNameForClasspathConfiguration() {
    Variant debug = mock(Variant.class);
    when(debug.getName()).thenReturn("debug");
    AndroidVersion minSdk = mock(AndroidVersion.class);
    when(minSdk.getApiLevel()).thenReturn(28);
    when(debug.getMinSdk()).thenReturn(minSdk);
    UnitTest unitTest = mock(UnitTest.class);
    when(unitTest.getName()).thenReturn("debugUnitTest");
    when(debug.getNestedComponents()).thenReturn(List.of(unitTest));
    stubOnVariants(debug);

    ConfigurableFileCollection bootFC = stubBootClasspath();
    when(bootFC.plus(any())).thenReturn(mock(FileCollection.class));

    ConfigurationContainer configurations = mock(ConfigurationContainer.class);
    when(project.getConfigurations()).thenReturn(configurations);
    Configuration configuration = mock(Configuration.class);
    when(configurations.getByName(any())).thenReturn(configuration);
    ResolvableDependencies incoming = mock(ResolvableDependencies.class);
    when(configuration.getIncoming()).thenReturn(incoming);
    ArtifactView artifactView = mock(ArtifactView.class);
    when(incoming.artifactView(any())).thenReturn(artifactView);
    when(artifactView.getFiles()).thenReturn(mock(FileCollection.class));
    ObjectFactory objectFactory = mock(ObjectFactory.class);
    when(project.getObjects()).thenReturn(objectFactory);
    when(objectFactory.named(eq(TargetJvmEnvironment.class), any())).thenReturn(mock(TargetJvmEnvironment.class));

    AndroidConfig.of(project).getTestLibraries();

    verify(configurations).getByName("debugUnitTestCompileClasspath");
  }

  @Test
  void getTestLibraries_accumulatesClasspathsFromMultipleTestComponents() {
    Variant debug = mock(Variant.class);
    when(debug.getName()).thenReturn("debug");
    AndroidVersion minSdk = mock(AndroidVersion.class);
    when(minSdk.getApiLevel()).thenReturn(28);
    when(debug.getMinSdk()).thenReturn(minSdk);
    UnitTest unitTest = mock(UnitTest.class);
    when(unitTest.getName()).thenReturn("debugUnitTest");
    AndroidTest androidTest = mock(AndroidTest.class);
    when(androidTest.getName()).thenReturn("debugAndroidTest");
    when(debug.getNestedComponents()).thenReturn(List.of(unitTest, androidTest));
    stubOnVariants(debug);

    ConfigurableFileCollection bootFC = stubBootClasspath();

    // Set up a single ConfigurationContainer that handles both test components.
    ConfigurationContainer configurations = mock(ConfigurationContainer.class);
    when(project.getConfigurations()).thenReturn(configurations);
    ObjectFactory objectFactory = mock(ObjectFactory.class);
    when(project.getObjects()).thenReturn(objectFactory);
    when(objectFactory.named(eq(TargetJvmEnvironment.class), any())).thenReturn(mock(TargetJvmEnvironment.class));

    FileCollection unitTestFC = mock(FileCollection.class);
    Configuration unitTestConfig = mock(Configuration.class);
    ResolvableDependencies unitTestIncoming = mock(ResolvableDependencies.class);
    ArtifactView unitTestView = mock(ArtifactView.class);
    when(configurations.getByName("debugUnitTestCompileClasspath")).thenReturn(unitTestConfig);
    when(unitTestConfig.getIncoming()).thenReturn(unitTestIncoming);
    when(unitTestIncoming.artifactView(any())).thenReturn(unitTestView);
    when(unitTestView.getFiles()).thenReturn(unitTestFC);

    FileCollection androidTestFC = mock(FileCollection.class);
    Configuration androidTestConfig = mock(Configuration.class);
    ResolvableDependencies androidTestIncoming = mock(ResolvableDependencies.class);
    ArtifactView androidTestView = mock(ArtifactView.class);
    when(configurations.getByName("debugAndroidTestCompileClasspath")).thenReturn(androidTestConfig);
    when(androidTestConfig.getIncoming()).thenReturn(androidTestIncoming);
    when(androidTestIncoming.artifactView(any())).thenReturn(androidTestView);
    when(androidTestView.getFiles()).thenReturn(androidTestFC);

    FileCollection afterUnitTest = mock(FileCollection.class);
    FileCollection afterAndroidTest = mock(FileCollection.class);
    when(bootFC.plus(unitTestFC)).thenReturn(afterUnitTest);
    when(afterUnitTest.plus(androidTestFC)).thenReturn(afterAndroidTest);

    FileCollection result = AndroidConfig.of(project).getTestLibraries();

    assertSame(afterAndroidTest, result);
  }

  @Test
  void getTasks_returnsMainCompileAndUnitTestTasks_whenNoTestComponents() {
    stubOnVariants(mockVariant("debug", 28, false));

    Task compileTask = mock(Task.class);
    Task unitTestTask = mock(Task.class);
    when(taskContainer.getByName("compileDebugJavaWithJavac")).thenReturn(compileTask);
    when(taskContainer.getByName("testDebugUnitTest")).thenReturn(unitTestTask);

    List<Task> result = AndroidConfig.of(project).getTasks();

    assertEquals(List.of(compileTask, unitTestTask), result);
  }

  @Test
  void getTasks_omitsMainCompileTask_whenTestComponentCompileTaskExists() {
    stubOnVariants(mockVariantWithUnitTest("debug"));

    Task testCompileTask = mock(Task.class);
    Task unitTestTask = mock(Task.class);
    when(taskContainer.getByName("compileDebugUnitTestJavaWithJavac")).thenReturn(testCompileTask);
    when(taskContainer.getByName("testDebugUnitTest")).thenReturn(unitTestTask);

    List<Task> result = AndroidConfig.of(project).getTasks();

    assertEquals(List.of(testCompileTask, unitTestTask), result);
    // The test compile task depends on the main compile task, so the latter is redundant and must not be added.
    verify(taskContainer, never()).getByName("compileDebugJavaWithJavac");
  }

  @Test
  void getTasks_addsMainCompileTask_whenTestComponentCompileTaskMissing() {
    stubOnVariants(mockVariantWithUnitTest("debug"));

    when(taskContainer.getByName("compileDebugUnitTestJavaWithJavac")).thenThrow(new UnknownTaskException("not found"));
    Task compileTask = mock(Task.class);
    Task unitTestTask = mock(Task.class);
    when(taskContainer.getByName("compileDebugJavaWithJavac")).thenReturn(compileTask);
    when(taskContainer.getByName("testDebugUnitTest")).thenReturn(unitTestTask);

    List<Task> result = AndroidConfig.of(project).getTasks();

    assertEquals(List.of(compileTask, unitTestTask), result);
  }

  @Test
  void getTasks_includesAllTestComponentCompileTasks_whenMultipleTestComponentsExist() {
    stubOnVariants(mockVariantWithUnitTestAndAndroidTest("debug"));

    Task unitTestCompileTask = mock(Task.class);
    Task androidTestCompileTask = mock(Task.class);
    Task unitTestTask = mock(Task.class);
    when(taskContainer.getByName("compileDebugUnitTestJavaWithJavac")).thenReturn(unitTestCompileTask);
    when(taskContainer.getByName("compileDebugAndroidTestJavaWithJavac")).thenReturn(androidTestCompileTask);
    when(taskContainer.getByName("testDebugUnitTest")).thenReturn(unitTestTask);

    List<Task> result = AndroidConfig.of(project).getTasks();

    assertEquals(List.of(unitTestCompileTask, androidTestCompileTask, unitTestTask), result);
    // Both test compile tasks already depend on the main compile task, so it must not be added.
    verify(taskContainer, never()).getByName("compileDebugJavaWithJavac");
  }

  @Test
  void getTasks_returnsEmptyList_whenNoTasksExist() {
    stubOnVariants(mockVariant("debug", 28, false));

    when(taskContainer.getByName(any())).thenThrow(new UnknownTaskException("not found"));

    List<Task> result = AndroidConfig.of(project).getTasks();

    assertTrue(result.isEmpty());
  }

  @Test
  void getTasks_returnsOnlyUnitTestTask_whenMainCompileTaskMissing() {
    stubOnVariants(mockVariant("debug", 28, false));

    when(taskContainer.getByName("compileDebugJavaWithJavac")).thenThrow(new UnknownTaskException("not found"));
    Task unitTestTask = mock(Task.class);
    when(taskContainer.getByName("testDebugUnitTest")).thenReturn(unitTestTask);

    List<Task> result = AndroidConfig.of(project).getTasks();

    assertEquals(List.of(unitTestTask), result);
  }

  @Test
  void configureProperties_setsBinaries_forMainVariant() {
    stubOnVariants(mockVariant("debug", 28, false));

    Map<String, Object> props = new HashMap<>();
    AndroidConfig.of(project).configureProperties(props);

    assertTrue(props.containsKey("sonar.java.binaries"));
    assertTrue(props.containsKey("sonar.binaries"));
    assertFalse(props.containsKey("sonar.java.test.binaries"));
  }

  @Test
  void configureProperties_setsTestBinaries_whenTestComponentsExist() {
    stubOnVariants(mockVariantWithUnitTest("debug"));

    TaskCollection<org.gradle.api.tasks.testing.Test> testTasks = mock(TaskCollection.class);
    when(testTasks.stream()).thenAnswer(inv -> Stream.empty());
    when(taskContainer.withType(org.gradle.api.tasks.testing.Test.class)).thenReturn(testTasks);

    Map<String, Object> props = new HashMap<>();
    AndroidConfig.of(project).configureProperties(props);

    assertTrue(props.containsKey("sonar.java.binaries"));
    assertTrue(props.containsKey("sonar.binaries"));
    assertTrue(props.containsKey("sonar.java.test.binaries"));
  }

  @Test
  void configureProperties_setsTestBinariesOnly_whenComAndroidTestPluginPresent() {
    // The com.android.test plugin signals that this is a pure test module: configureJDK is called with isTest = true for the variant, and the method returns early without
    // processing test components.
    PluginContainer plugins = mock(PluginContainer.class);
    when(project.getPlugins()).thenReturn(plugins);
    when(plugins.hasPlugin("com.android.test")).thenReturn(true);

    stubOnVariants(mockVariant("debug", 28, false));

    Map<String, Object> props = new HashMap<>();
    AndroidConfig.of(project).configureProperties(props);

    assertFalse(props.containsKey("sonar.java.binaries"));
    assertFalse(props.containsKey("sonar.binaries"));
    assertTrue(props.containsKey("sonar.java.test.binaries"));
  }

  @Test
  void configureProperties_doesNotSetJunitReportPaths_whenNoTestComponents() {
    stubOnVariants(mockVariant("debug", 28, false));

    Map<String, Object> props = new HashMap<>();
    AndroidConfig.of(project).configureProperties(props);

    assertFalse(props.containsKey(SonarProperty.JUNIT_REPORT_PATHS));
  }

  @Test
  void configureProperties_setsJunitReportPaths_whenUnitTestTaskFound() {
    stubOnVariants(mockVariantWithUnitTest("debug"));

    File reportDir = new File("build/test-results/testDebugUnitTest");

    org.gradle.api.tasks.testing.Test testTask = mock(org.gradle.api.tasks.testing.Test.class);
    when(testTask.getName()).thenReturn("testDebugUnitTest");
    TestTaskReports reports = mock(TestTaskReports.class);
    JUnitXmlReport junitXml = mock(JUnitXmlReport.class);
    DirectoryProperty outputLocation = mock(DirectoryProperty.class);
    Directory dir = mock(Directory.class);
    when(testTask.getReports()).thenReturn(reports);
    when(reports.getJunitXml()).thenReturn(junitXml);
    when(junitXml.getOutputLocation()).thenReturn(outputLocation);
    when(outputLocation.isPresent()).thenReturn(true);
    when(outputLocation.get()).thenReturn(dir);
    when(dir.getAsFile()).thenReturn(reportDir);

    TaskCollection<org.gradle.api.tasks.testing.Test> testTasks = mock(TaskCollection.class);
    when(testTasks.stream()).thenAnswer(inv -> Stream.of(testTask));
    when(taskContainer.withType(org.gradle.api.tasks.testing.Test.class)).thenReturn(testTasks);

    Map<String, Object> props = new HashMap<>();
    AndroidConfig.of(project).configureProperties(props);

    assertEquals(List.of(reportDir), props.get(SonarProperty.JUNIT_REPORT_PATHS));
  }

  @Test
  void configureProperties_setsJunitReportPaths_whenAndroidTestTaskFound() {
    stubOnVariants(mockVariant("debug", 28, true));

    File reportDir = new File("build/outputs/androidTest-results/connected/debug");
    DirectoryProperty reportsDir = mock(DirectoryProperty.class);
    Directory dir = mock(Directory.class);
    when(reportsDir.isPresent()).thenReturn(true);
    when(reportsDir.get()).thenReturn(dir);
    when(dir.getAsFile()).thenReturn(reportDir);

    DeviceProviderInstrumentTestTask instrumentTestTask = mock(DeviceProviderInstrumentTestTask.class);
    when(instrumentTestTask.getVariantName()).thenReturn("debugAndroidTest");
    when(instrumentTestTask.getReportsDir()).thenReturn(reportsDir);

    TaskCollection<DeviceProviderInstrumentTestTask> instrumentTestTasks = mock(TaskCollection.class);
    when(instrumentTestTasks.stream()).thenAnswer(inv -> Stream.of(instrumentTestTask));
    when(taskContainer.withType(DeviceProviderInstrumentTestTask.class)).thenReturn(instrumentTestTasks);

    Map<String, Object> props = new HashMap<>();
    AndroidConfig.of(project).configureProperties(props);

    assertEquals(List.of(reportDir), props.get(SonarProperty.JUNIT_REPORT_PATHS));
  }

}
