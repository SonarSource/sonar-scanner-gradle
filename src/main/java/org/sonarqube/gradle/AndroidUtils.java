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

import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension;
import com.android.build.api.variant.LibraryAndroidComponentsExtension;
import com.android.build.api.variant.TestAndroidComponentsExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.DynamicFeaturePlugin;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.TestPlugin;
import java.util.Objects;
import javax.annotation.Nullable;
import org.gradle.api.Project;

import static org.sonarqube.gradle.SonarQubePlugin.getSonarExtensions;

public class AndroidUtils {

  private AndroidUtils() {
  }

  public static void configureForProject(AndroidResolverTask androidResolverTask, Project project) {
    androidResolverTask.setConfiguredAndroidVariant(getConfiguredVariantName(project));

    // TODO: remove after migrating the computation of main and test libraries to the Android resolver task's execution
    androidResolverTask.setMainLibraries(project.provider(() -> LegacyAndroidUtils.findMainLibraries(project)));
    androidResolverTask.setTestLibraries(project.provider(() -> LegacyAndroidUtils.findTestLibraries(project)));

    if (!project.getPlugins().withType(AppPlugin.class).isEmpty()) {
      ApplicationAndroidComponentsExtension androidExtension = project.getExtensions().getByType(ApplicationAndroidComponentsExtension.class);
      androidExtension.onVariants(androidExtension.selector().all(), androidResolverTask.getVariants()::add);
      androidResolverTask.setBootClassPath(androidExtension.getSdkComponents().getBootClasspath());
    } else if (!project.getPlugins().withType(LibraryPlugin.class).isEmpty()) {
      LibraryAndroidComponentsExtension androidExtension = project.getExtensions().getByType(LibraryAndroidComponentsExtension.class);
      androidExtension.onVariants(androidExtension.selector().all(), androidResolverTask.getVariants()::add);
      androidResolverTask.setBootClassPath(androidExtension.getSdkComponents().getBootClasspath());
    } else if (!project.getPlugins().withType(TestPlugin.class).isEmpty()) {
      TestAndroidComponentsExtension androidExtension = project.getExtensions().getByType(TestAndroidComponentsExtension.class);
      androidExtension.onVariants(androidExtension.selector().all(), androidResolverTask.getVariants()::add);
      androidResolverTask.setBootClassPath(androidExtension.getSdkComponents().getBootClasspath());
    } else if (!project.getPlugins().withType(DynamicFeaturePlugin.class).isEmpty()) {
      DynamicFeatureAndroidComponentsExtension androidExtension = project.getExtensions().getByType(DynamicFeatureAndroidComponentsExtension.class);
      androidExtension.onVariants(androidExtension.selector().all(), androidResolverTask.getVariants()::add);
      androidResolverTask.setBootClassPath(androidExtension.getSdkComponents().getBootClasspath());
    }
  }

  @Nullable
  public static String getConfiguredVariantName(Project project) {
    return getSonarExtensions(project)
      .stream()
      .map(SonarExtension::getAndroidVariant)
      .filter(Objects::nonNull)
      .findFirst().orElse(null);
  }

}
