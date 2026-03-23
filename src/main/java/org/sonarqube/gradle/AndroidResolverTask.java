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
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskAction;

public class AndroidResolverTask extends DefaultTask {

  public static final String TASK_NAME = "androidResolver";
  public static final String TASK_DESCRIPTION = "Resolves and serializes properties and classpath information for the analysis of Android projects.";
  private static final Logger LOGGER = Logging.getLogger(AndroidResolverTask.class.getName());

  public final Set<Variant> variants = new LinkedHashSet<>();

  @Inject
  public AndroidResolverTask() {
    super();
  }

  @TaskAction
  public void run() {
    LOGGER.info("Found variants: {}", variants.stream().map(Variant::getName).collect(java.util.stream.Collectors.toList()));
  }

  public void registerVariants(Project project) {
    if (!project.getPlugins().withType(AppPlugin.class).isEmpty()) {
      ApplicationAndroidComponentsExtension androidExtension = project.getExtensions().getByType(ApplicationAndroidComponentsExtension.class);
      androidExtension.onVariants(androidExtension.selector().all(), variants::add);
    } else if (!project.getPlugins().withType(LibraryPlugin.class).isEmpty()) {
      LibraryAndroidComponentsExtension androidExtension = project.getExtensions().getByType(LibraryAndroidComponentsExtension.class);
      androidExtension.onVariants(androidExtension.selector().all(), variants::add);
    } else if (!project.getPlugins().withType(TestPlugin.class).isEmpty()) {
      TestAndroidComponentsExtension androidExtension = project.getExtensions().getByType(TestAndroidComponentsExtension.class);
      androidExtension.onVariants(androidExtension.selector().all(), variants::add);
    } else if (!project.getPlugins().withType(DynamicFeaturePlugin.class).isEmpty()) {
      DynamicFeatureAndroidComponentsExtension androidExtension = project.getExtensions().getByType(DynamicFeatureAndroidComponentsExtension.class);
      androidExtension.onVariants(androidExtension.selector().all(), variants::add);
    }
  }

}
