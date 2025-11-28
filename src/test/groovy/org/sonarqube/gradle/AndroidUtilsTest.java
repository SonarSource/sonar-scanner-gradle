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
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.lint.AndroidLintTask;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.BuildType;
import com.android.builder.model.SourceProvider;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.PluginCollection;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AndroidUtilsTest {

  private Project project;
  private ProductFlavor flavor;
  private AppExtension appExtension;
  private BaseVariant baseVariant;

  @BeforeEach
  void setup() {
    project = mock(Project.class);
    ExtensionContainer extensionsContainer = mock(ExtensionContainer.class);
    appExtension = mock(AppExtension.class);
    PluginContainer pluginContainer = mock(PluginContainer.class);
    PluginCollection<AppPlugin> pluginCollection = mock(PluginCollection.class);
    baseVariant = mock(BaseVariant.class);
    BaseVariant[] baseVariants = {baseVariant};
    DomainObjectSet<ApplicationVariant> domainObjectSet = mock(DomainObjectSet.class);
    BuildType buildType = mock(BuildType.class);
    NamedDomainObjectContainer<ProductFlavor> productFlavors = mock(NamedDomainObjectContainer.class);
    TaskProvider<JavaCompile> javaCompilerProvider = mock(TaskProvider.class);
    flavor = mock(ProductFlavor.class);

    when(project.getExtensions()).thenReturn(extensionsContainer);
    when(project.getPlugins()).thenReturn(pluginContainer);
    when(extensionsContainer.getByType(AppExtension.class)).thenReturn(appExtension);
    when(pluginContainer.withType(AppPlugin.class)).thenReturn(pluginCollection);
    when(pluginCollection.isEmpty()).thenReturn(false);
    when(appExtension.getTestBuildType()).thenReturn("debug");
    when(appExtension.getBootClasspath()).thenReturn(new ArrayList<>());
    when(appExtension.getApplicationVariants()).thenReturn(domainObjectSet); //Overriding final method here, be careful
    when(appExtension.getProductFlavors()).thenReturn(productFlavors);
    when(domainObjectSet.toArray()).thenReturn(baseVariants);
    when(productFlavors.stream()).thenReturn(Stream.of(flavor));
    when(baseVariant.getBuildType()).thenReturn(buildType);
    when(buildType.getName()).thenReturn("debug");
    when(baseVariant.getName()).thenReturn("lollipopDebug");
    when(baseVariant.getJavaCompileProvider()).thenReturn(javaCompilerProvider);

    TaskContainer container = mock(TaskContainer.class);
    when(project.getTasks()).thenReturn(container);
    TaskCollection<AndroidLintTask> taskCollection = mock(TaskCollection.class);
    when(taskCollection.stream()).thenReturn(Stream.empty());
    when(container.withType(AndroidLintTask.class)).thenReturn(taskCollection);
  }

  @Test
  void configureForAndroid_androidPropertiesAreSetForAndroidProject_whenVariantNotDetected() {
    DefaultConfig defaultConfig = mock(DefaultConfig.class);
    when(appExtension.getDefaultConfig()).thenReturn(defaultConfig);
    when(flavor.getMinSdk()).thenReturn(21);
    when(defaultConfig.getMinSdk()).thenReturn(23);

    Map<String, Object> resultProperties = new HashMap<>();
    AndroidUtils.configureForAndroid(project, null, resultProperties);

    assertEquals(true, resultProperties.get("sonar.android.detected"));
    assertEquals(21, resultProperties.get("sonar.android.minsdkversion.min"));
    assertEquals(23, resultProperties.get("sonar.android.minsdkversion.max"));
  }

  @Test
  void configureForAndroid_androidPropertiesAreSetForAndroidProject_whenVariantDetected() {
    com.android.builder.model.ProductFlavor mergedFlavor = mock(com.android.builder.model.ProductFlavor.class);
    ApiVersion apiVersion = mock(ApiVersion.class);
    when(mergedFlavor.getMinSdkVersion()).thenReturn(apiVersion);
    when(apiVersion.getApiLevel()).thenReturn(30);
    when(baseVariant.getMergedFlavor()).thenReturn(mergedFlavor);
    when(baseVariant.getName()).thenReturn("myVariant");

    // Set up a sourceSet with a file to test the presence of sonar.sources
    SourceProvider javaSource = mock(SourceProvider.class);
    File file = mock(File.class);
    when(file.exists()).thenReturn(true);
    when(file.toPath()).thenReturn(Path.of("src/main/java/manifest.xml"));
    when(javaSource.getManifestFile()).thenReturn(file);
    when(baseVariant.getSourceSets()).thenReturn(List.of(javaSource));

    Map<String, Object> resultProperties = new HashMap<>();
    AndroidUtils.configureForAndroid(project, "myVariant", resultProperties);

    assertEquals(true, resultProperties.get("sonar.android.detected"));
    assertEquals(30, resultProperties.get("sonar.android.minsdkversion.min"));
    assertEquals(30, resultProperties.get("sonar.android.minsdkversion.max"));
    Set<File> sources = (Set<File>) resultProperties.get("sonar.sources");
    assertEquals(1, sources.size());
    assertTrue(sources.contains(file));
  }

}
