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

import com.android.build.api.variant.SourceDirectories;
import com.android.build.api.variant.Sources;
import com.android.build.api.variant.Variant;

/**
 * Captures source directory providers from {@code variant.getSources()}.
 * <p>
 * This class is intentionally separate from {@link SonarQubePlugin} because it references
 * {@code Sources} and {@code SourceDirectories.Flat} which don't exist in AGP &lt; 7.2.
 * By isolating these references in a separate class, the JVM only attempts to load it
 * when actually called — allowing {@link SonarQubePlugin} to load on any AGP version.
 */
class AndroidVariantSourcesCollector {

  private AndroidVariantSourcesCollector() {
  }

  static void collect(Variant variant, String variantName, AndroidConfigCollectorTask.SharedCollector collector) {
    Sources sources = variant.getSources();
    addIfPresent(collector, variantName, sources.getJava());
    addIfPresent(collector, variantName, sources.getKotlin());
    addIfPresent(collector, variantName, sources.getResources());
    addIfPresent(collector, variantName, sources.getAidl());
  }

  private static void addIfPresent(AndroidConfigCollectorTask.SharedCollector collector, String variantName,
    SourceDirectories.Flat sourceDir) {
    if (sourceDir != null) {
      collector.addVariantSourceProvider(variantName, sourceDir.getAll());
    }
  }
}
