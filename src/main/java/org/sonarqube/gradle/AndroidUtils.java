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

import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.api.dsl.DynamicFeatureExtension;
import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension;
import com.android.build.api.variant.LibraryAndroidComponentsExtension;
import com.android.build.api.variant.TestAndroidComponentsExtension;
import com.android.build.api.variant.Variant;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.DynamicFeaturePlugin;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.TestPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.PluginCollection;

public class AndroidUtils {

  private static final Logger LOGGER = Logging.getLogger(AndroidUtils.class);

  private AndroidUtils() {
  }

  public static void withAndroidVariant(Project project, @Nullable String variantName, Consumer<AndroidVariant> consumer) {
    withVariants(project, variants -> {
      Variant variant = findVariant(variants, variantName, getTestBuildType(project));
      consumer.accept(new AndroidVariant(variant));
    });
  }

  private static void withVariants(Project project, Consumer<List<Variant>> consumer) {
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

  @Nullable
  private static Variant findVariant(List<Variant> candidates, @Nullable String variantName, @Nullable String testBuildType) {
    if (candidates.isEmpty()) {
      return null;
    }

    if (variantName == null) {
      // Take the first test variant when there is no provided variant name, or any variant if there is no test variant.
      Optional<Variant> firstDebug = candidates.stream().filter(v -> testBuildType != null && testBuildType.equals(v.getName())).findFirst();
      Variant result = firstDebug.orElse(candidates.get(0));
      LOGGER.info("No variant name specified to be used by SonarQube. Default to '{}'", result.getName());
      return result;
    }

    Optional<Variant> result = candidates.stream().filter(v -> variantName.equals(v.getName())).findFirst();
    if (result.isPresent()) {
      return result.get();
    } else {
      throw new IllegalArgumentException(
        "Unable to find variant '"
          + variantName
          + "' to use for SonarQube analysis. Candidates are: "
          + candidates.stream().map(Variant::getName).collect(Collectors.joining(", "))
      );
    }
  }

  @Nullable
  private static String getTestBuildType(Project project) {
    PluginCollection<AppPlugin> appPlugins = project.getPlugins().withType(AppPlugin.class);
    if (!appPlugins.isEmpty()) {
      ApplicationExtension androidExtension = project.getExtensions().getByType(ApplicationExtension.class);
      return androidExtension.getTestBuildType();
    }
    PluginCollection<LibraryPlugin> libPlugins = project.getPlugins().withType(LibraryPlugin.class);
    if (!libPlugins.isEmpty()) {
      LibraryExtension androidExtension = project.getExtensions().getByType(LibraryExtension.class);
      return androidExtension.getTestBuildType();
    }
    PluginCollection<DynamicFeaturePlugin> dynamicFeaturePlugins = project.getPlugins().withType(DynamicFeaturePlugin.class);
    if (!dynamicFeaturePlugins.isEmpty()) {
      DynamicFeatureExtension androidExtension = project.getExtensions().getByType(DynamicFeatureExtension.class);
      return androidExtension.getTestBuildType();
    }
    return null;
  }

  static class AndroidVariant {

    public final Variant variant;

    public AndroidVariant(Variant variant) {
      this.variant = variant;
    }

  }

}
