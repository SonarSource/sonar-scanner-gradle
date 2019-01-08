/**
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2019 SonarSource
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.PluginCollection;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.jetbrains.annotations.NotNull;

import static org.sonarqube.gradle.SonarPropertyComputer.SONAR_JAVA_SOURCE_PROP;
import static org.sonarqube.gradle.SonarPropertyComputer.SONAR_JAVA_TARGET_PROP;
import static org.sonarqube.gradle.SonarPropertyComputer.SONAR_SOURCES_PROP;
import static org.sonarqube.gradle.SonarPropertyComputer.SONAR_TESTS_PROP;
import static org.sonarqube.gradle.SonarUtils.appendProps;
import static org.sonarqube.gradle.SonarUtils.nonEmptyOrNull;
import static org.sonarqube.gradle.SonarUtils.setMainClasspathProps;
import static org.sonarqube.gradle.SonarUtils.setTestClasspathProps;

/**
 * Only access this class when running on an Android application
 */
class AndroidUtils {

  private static final Logger LOGGER = Logging.getLogger(AndroidUtils.class);

  private AndroidUtils() {
  }

  static void configureForAndroid(Project project, String userConfiguredBuildVariantName, final Map<String, Object> properties) {
    BaseVariant variant = findVariant(project, userConfiguredBuildVariantName);
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
    return null;
  }

  @Nullable
  static BaseVariant findVariant(Project project, @Nullable String userConfiguredBuildVariantName) {
    String testBuildType = getTestBuildType(project);
    PluginCollection<AppPlugin> appPlugins = project.getPlugins().withType(AppPlugin.class);
    if (!appPlugins.isEmpty()) {
      AppExtension androidExtension = project.getExtensions().getByType(AppExtension.class);
      return findVariant(new ArrayList<>(androidExtension.getApplicationVariants()), testBuildType, userConfiguredBuildVariantName);
    }
    PluginCollection<LibraryPlugin> libPlugins = project.getPlugins().withType(LibraryPlugin.class);
    if (!libPlugins.isEmpty()) {
      LibraryExtension androidExtension = project.getExtensions().getByType(LibraryExtension.class);
      return findVariant(new ArrayList<>(androidExtension.getLibraryVariants()), testBuildType, userConfiguredBuildVariantName);
    }
    PluginCollection<TestPlugin> testPlugins = project.getPlugins().withType(TestPlugin.class);
    if (!testPlugins.isEmpty()) {
      TestExtension androidExtension = project.getExtensions().getByType(TestExtension.class);
      return findVariant(new ArrayList<>(androidExtension.getApplicationVariants()), testBuildType, userConfiguredBuildVariantName);
    }
    return null;
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

  private static void populateSonarQubeProps(Map<String, Object> properties, List<File> bootClassPath, BaseVariant variant, boolean isTest) {
    List<File> srcDirs = variant.getSourceSets().stream().map(AndroidUtils::getFilesFromSourceSet).collect(
      ArrayList::new,
      ArrayList::addAll,
      ArrayList::addAll);
    List<File> sourcesOrTests = nonEmptyOrNull(srcDirs.stream().filter(File::exists).collect(Collectors.toList()));
    if (sourcesOrTests != null) {
      appendProps(properties, isTest ? SONAR_TESTS_PROP : SONAR_SOURCES_PROP, sourcesOrTests);
    }

    AbstractCompile javaCompiler = getJavaCompiler(variant);
    if (javaCompiler == null) {
      LOGGER.warn("Unable to find Java compiler on variant '{}'. Is Jack toolchain used? SonarQube analysis will be less accurate without bytecode.", variant.getName());
    }
    if (javaCompiler != null) {
      properties.put(SONAR_JAVA_SOURCE_PROP, javaCompiler.getSourceCompatibility());
      properties.put(SONAR_JAVA_TARGET_PROP, javaCompiler.getTargetCompatibility());
    }

    Set<File> libraries = new LinkedHashSet<>(bootClassPath);
    // I don't know what is best: ApkVariant::getCompileClasspath() or BaseVariant::getJavaCompile()::getClasspath()
    // In doubt I put both in a set to remove duplicates
    if (variant instanceof ApkVariant) {
      libraries.addAll(getLibraries((ApkVariant) variant));
    }
    if (javaCompiler != null) {
      libraries.addAll(javaCompiler.getClasspath().filter(File::exists).getFiles());
    }
    if (isTest) {
      setTestClasspathProps(properties, javaCompiler != null ? Collections.singleton(javaCompiler.getDestinationDir()) : null, libraries);
    } else {
      setMainClasspathProps(properties, false, javaCompiler != null ? Collections.singleton(javaCompiler.getDestinationDir()) : null, libraries);
    }
  }

  @NotNull
  private static Collection<File> getLibraries(ApkVariant variant) {
    try {
      Method methodOnAndroidBefore30 = variant.getClass().getMethod("getCompileLibraries");
      return (Set<File>) methodOnAndroidBefore30.invoke(variant, (Object[]) null);
    } catch (NoSuchMethodException e) {
      return variant.getCompileClasspath(null).getFiles();
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalArgumentException("Unable to call getCompileLibraries", e);
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
