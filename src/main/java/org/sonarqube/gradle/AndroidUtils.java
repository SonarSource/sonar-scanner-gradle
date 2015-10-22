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
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.api.UnitTestVariant;
import com.android.build.gradle.internal.api.TestedVariant;
import com.android.builder.model.SourceProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Only access this class when running on an Android application
 */
class AndroidUtils {

  static final String RELEASE = "release";

  private AndroidUtils() {
  }

  static void configureForAndroid(Project project, final Map<String, Object> properties) {
    project.getPlugins().withType(AppPlugin.class, appPlugin -> {
      AppExtension androidExtension = project.getExtensions().getByType(AppExtension.class);
      if (androidExtension != null) {
        List<File> bootClassPath = androidExtension.getBootClasspath();
        ApplicationVariant releaseVariant = androidExtension.getApplicationVariants().stream().filter(v -> RELEASE.equals(v.getName())).findFirst().orElse(null);
        populateMainPropsForAndroid(properties, bootClassPath, releaseVariant);

        populateTestPropsForAndroid(properties, bootClassPath, releaseVariant);
      }
    });
    project.getPlugins().withType(LibraryPlugin.class, appPlugin -> {
      LibraryExtension androidExtension = project.getExtensions().getByType(LibraryExtension.class);
      if (androidExtension != null) {
        List<File> bootClassPath = androidExtension.getBootClasspath();
        LibraryVariant releaseVariant = androidExtension.getLibraryVariants().stream().filter(v -> RELEASE.equals(v.getName())).findFirst().orElse(null);
        populateMainPropsForAndroid(properties, bootClassPath, releaseVariant);

        populateTestPropsForAndroid(properties, bootClassPath, releaseVariant);
      }
    });
    project.getPlugins().withType(TestPlugin.class, appPlugin -> {
      TestExtension androidExtension = project.getExtensions().getByType(TestExtension.class);
      if (androidExtension != null) {
        List<File> bootClassPath = androidExtension.getBootClasspath();
        ApplicationVariant releaseVariant = androidExtension.getApplicationVariants().stream().filter(v -> RELEASE.equals(v.getName())).findFirst().orElse(null);
        populateMainPropsForAndroid(properties, bootClassPath, releaseVariant);

        populateTestPropsForAndroid(properties, bootClassPath, releaseVariant);
      }
    });
  }

  private static void populateTestPropsForAndroid(Map<String, Object> properties, List<File> bootClassPath, TestedVariant releaseVariant) {
    UnitTestVariant unitTestVariant = releaseVariant.getUnitTestVariant();
    if (unitTestVariant != null) {
      List<File> testDirs = unitTestVariant.getSourceSets().stream().map(AndroidUtils::getFilesFromSourceSet).collect(
        ArrayList::new,
        ArrayList::addAll,
        ArrayList::addAll);
      properties.put(SonarQubePlugin.SONAR_TESTS_PROP, SonarQubePlugin.nonEmptyOrNull(testDirs.stream().filter(SonarQubePlugin.FILE_EXISTS).collect(Collectors.toList())));

      List<File> testLibraries = new ArrayList<>();
      testLibraries.addAll(bootClassPath);
      testLibraries.addAll(unitTestVariant.getJavaCompiler().getClasspath().getFiles());
      SonarQubePlugin.setTestClasspathProps(properties, unitTestVariant.getJavaCompiler().getDestinationDir(), testLibraries);
    }
  }

  @NotNull
  private static List<File> populateMainPropsForAndroid(Map<String, Object> properties, List<File> bootClassPath, BaseVariant releaseVariant) {
    List<File> srcDirs = releaseVariant.getSourceSets().stream().map(AndroidUtils::getFilesFromSourceSet).collect(
      ArrayList::new,
      ArrayList::addAll,
      ArrayList::addAll);
    properties.put(SonarQubePlugin.SONAR_SOURCES_PROP, SonarQubePlugin.nonEmptyOrNull(srcDirs.stream().filter(SonarQubePlugin.FILE_EXISTS).collect(Collectors.toList())));

    properties.put(SonarQubePlugin.SONAR_JAVA_SOURCE_PROP, releaseVariant.getJavaCompiler().getSourceCompatibility());
    properties.put(SonarQubePlugin.SONAR_JAVA_TARGET_PROP, releaseVariant.getJavaCompiler().getTargetCompatibility());

    List<File> libraries = new ArrayList<>();
    libraries.addAll(bootClassPath);
    libraries.addAll(releaseVariant.getJavaCompiler().getClasspath().getFiles());
    SonarQubePlugin.setMainClasspathProps(properties, false, releaseVariant.getJavaCompiler().getDestinationDir(), libraries);
    return bootClassPath;
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
