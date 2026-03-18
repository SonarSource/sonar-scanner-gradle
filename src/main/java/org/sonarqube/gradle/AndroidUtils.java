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
import com.android.build.api.variant.Variant;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.DynamicFeaturePlugin;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.TestPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.gradle.api.Project;

public class AndroidUtils {

  private AndroidUtils() {
  }

  public static void withVariants(Project project, Consumer<List<Variant>> consumer) {
    var variants = new ArrayList<Variant>();

    if (!project.getPlugins().withType(AppPlugin.class).isEmpty()) {
      ApplicationAndroidComponentsExtension androidExtension = project.getExtensions().getByType(ApplicationAndroidComponentsExtension.class);
      androidExtension.onVariants(androidExtension.selector().all(), variant -> {
        variants.add(variant);
      });
    } else if (!project.getPlugins().withType(LibraryPlugin.class).isEmpty()) {
      LibraryAndroidComponentsExtension androidExtension = project.getExtensions().getByType(LibraryAndroidComponentsExtension.class);
      androidExtension.onVariants(androidExtension.selector().all(), variant -> {
        variants.add(variant);
      });
    } else if (!project.getPlugins().withType(TestPlugin.class).isEmpty()) {
      TestAndroidComponentsExtension androidExtension = project.getExtensions().getByType(TestAndroidComponentsExtension.class);
      androidExtension.onVariants(androidExtension.selector().all(), variant -> {
        variants.add(variant);
      });
    } else if (!project.getPlugins().withType(DynamicFeaturePlugin.class).isEmpty()) {
      DynamicFeatureAndroidComponentsExtension androidExtension = project.getExtensions().getByType(DynamicFeatureAndroidComponentsExtension.class);
      androidExtension.onVariants(androidExtension.selector().all(), variant -> {
        variants.add(variant);
      });
    }

    project.afterEvaluate(p -> consumer.accept(variants));
  }

}
