/**
 * SonarQube Gradle Plugin
 * Copyright (C) 2015-2016 SonarSource
 * sonarqube@googlegroups.com
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
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.TestExtension;
import com.android.build.gradle.TestPlugin;
import com.android.build.gradle.api.ApkVariant;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.api.UnitTestVariant;
import com.android.build.gradle.internal.api.TestedVariant;
import com.android.builder.model.SourceProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Nullable;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.PluginCollection;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.jetbrains.annotations.NotNull;

/**
 * Only access this class when running on an Android application
 */
class AndroidUtils {

  /**
   * We should use debug by default. Release variant may be obfuscated using proguard. Also unit tests and coverage reports are usually collected in debug mode.
   */
  static final String DEFAULT_BUILD_TYPE = "debug";
  private static final Logger LOGGER = Logging.getLogger(AndroidUtils.class);

  private AndroidUtils() {
  }

  static void configureForAndroid(Project project, String variantName, final Map<String, Object> properties) {
    BaseVariant variant = findVariant(project, variantName);
    if (variant != null) {
      configureForAndroid(project, variant, properties);
    }
  }

  private static void configureForAndroid(Project project, BaseVariant variant, Map<String, Object> properties) {
    List<File> bootClassPath = getBootClasspath(project);
    if (project.getPlugins().hasPlugin("com.android.test")) {
      // Instrumentation tests only
      populateSonarQubeProps(properties, bootClassPath, variant, true);
    } else {
      populateSonarQubeProps(properties, bootClassPath, variant, false);
      if (variant instanceof TestedVariant) {
        // Local tests
        UnitTestVariant unitTestVariant = ((TestedVariant) variant).getUnitTestVariant();
        if (unitTestVariant != null) {
          populateSonarQubeProps(properties, bootClassPath, unitTestVariant, true);
        }
        // Instrumentation tests
        TestVariant testVariant = ((TestedVariant) variant).getTestVariant();
        if (testVariant != null) {
          populateSonarQubeProps(properties, bootClassPath, testVariant, true);
        }
      }
    }
  }

  @Nullable
  static List<File> getBootClasspath(Project project) {
    PluginCollection<AppPlugin> appPlugins = project.getPlugins().withType(AppPlugin.class);
    if (!appPlugins.isEmpty()) {
      AppExtension androidExtension = project.getExtensions().getByType(AppExtension.class);
      if (androidExtension != null) {
        return androidExtension.getBootClasspath();
      }
    }
    PluginCollection<LibraryPlugin> libPlugins = project.getPlugins().withType(LibraryPlugin.class);
    if (!libPlugins.isEmpty()) {
      LibraryExtension androidExtension = project.getExtensions().getByType(LibraryExtension.class);
      if (androidExtension != null) {
        return androidExtension.getBootClasspath();
      }
    }
    PluginCollection<TestPlugin> testPlugins = project.getPlugins().withType(TestPlugin.class);
    if (!testPlugins.isEmpty()) {
      TestExtension androidExtension = project.getExtensions().getByType(TestExtension.class);
      if (androidExtension != null) {
        return androidExtension.getBootClasspath();
      }
    }
    return null;
  }

  @Nullable
  static BaseVariant findVariant(Project project, @Nullable String variant) {
    PluginCollection<AppPlugin> appPlugins = project.getPlugins().withType(AppPlugin.class);
    if (!appPlugins.isEmpty()) {
      AppExtension androidExtension = project.getExtensions().getByType(AppExtension.class);
      if (androidExtension != null) {
        return findVariant(androidExtension.getApplicationVariants().stream().collect(Collectors.toList()), variant);
      }
    }
    PluginCollection<LibraryPlugin> libPlugins = project.getPlugins().withType(LibraryPlugin.class);
    if (!libPlugins.isEmpty()) {
      LibraryExtension androidExtension = project.getExtensions().getByType(LibraryExtension.class);
      if (androidExtension != null) {
        return findVariant(androidExtension.getLibraryVariants().stream().collect(Collectors.toList()), variant);
      }
    }
    PluginCollection<TestPlugin> testPlugins = project.getPlugins().withType(TestPlugin.class);
    if (!testPlugins.isEmpty()) {
      TestExtension androidExtension = project.getExtensions().getByType(TestExtension.class);
      if (androidExtension != null) {
        return findVariant(androidExtension.getApplicationVariants().stream().collect(Collectors.toList()), variant);
      }
    }
    return null;
  }

  @Nullable
  static BaseVariant findVariant(List<BaseVariant> candidates, @Nullable String variant) {
    if (candidates.isEmpty()) {
      return null;
    }
    if (variant == null) {
      // Take first "debug" buildType when there is no provided variant name
      Optional<BaseVariant> firstDebug = candidates.stream().filter(v -> DEFAULT_BUILD_TYPE.equals(v.getBuildType().getName())).findFirst();
      BaseVariant result;
      if (firstDebug.isPresent()) {
        result = firstDebug.get();
      } else {
        // No debug variant? Then use first variant whatever is the type
        result = candidates.get(0);
      }
      LOGGER.info("No variant name specified to be used by SonarQube. Default to '{}'", result.getName());
      return result;
    } else {
      Optional<BaseVariant> result = candidates.stream().filter(v -> variant.equals(v.getName())).findFirst();
      if (result.isPresent()) {
        return result.get();
      } else {
        throw new IllegalArgumentException("Unable to find variant '" + variant + "' to use for SonarQube configuration");
      }
    }
  }

  @NotNull
  private static void populateSonarQubeProps(Map<String, Object> properties, List<File> bootClassPath, BaseVariant variant, boolean isTest) {
    List<File> srcDirs = variant.getSourceSets().stream().map(AndroidUtils::getFilesFromSourceSet).collect(
      ArrayList::new,
      ArrayList::addAll,
      ArrayList::addAll);
    List<File> sourcesOrTests = SonarQubePlugin.nonEmptyOrNull(srcDirs.stream().filter(SonarQubePlugin.FILE_EXISTS).collect(Collectors.toList()));
    if (sourcesOrTests != null) {
      SonarQubePlugin.appendProps(properties, isTest ? SonarQubePlugin.SONAR_TESTS_PROP : SonarQubePlugin.SONAR_SOURCES_PROP, sourcesOrTests);
    }

    AbstractCompile javaCompiler = getJavaCompiler(variant);
    if (javaCompiler == null) {
      LOGGER.warn("Unable to find Java compiler on variant '{}'. Is Jack toolchain used? SonarQube analysis will be less accurate without bytecode.", variant.getName());
    }
    if (javaCompiler != null) {
      properties.put(SonarQubePlugin.SONAR_JAVA_SOURCE_PROP, javaCompiler.getSourceCompatibility());
      properties.put(SonarQubePlugin.SONAR_JAVA_TARGET_PROP, javaCompiler.getTargetCompatibility());
    }

    Set<File> libraries = new LinkedHashSet<>();
    libraries.addAll(bootClassPath);
    // I don't know what is best: ApkVariant::getCompileLibraries() or BaseVariant::getJavaCompile()::getClasspath()
    // In doubt I put both in a set to remove duplicates
    if (variant instanceof ApkVariant) {
      libraries.addAll(((ApkVariant) variant).getCompileLibraries());
    }
    if (javaCompiler != null) {
      libraries.addAll(javaCompiler.getClasspath().filter(File::exists).getFiles());
    }
    if (isTest) {
      SonarQubePlugin.setTestClasspathProps(properties, javaCompiler != null ? javaCompiler.getDestinationDir() : null, libraries);
    } else {
      SonarQubePlugin.setMainClasspathProps(properties, false, javaCompiler != null ? javaCompiler.getDestinationDir() : null, libraries);
    }
  }

  @Nullable
  private static AbstractCompile getJavaCompiler(BaseVariant variant) {
    return variant.getJavaCompile();
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
}
