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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Immutable DTO holding Android variant metadata collected by {@link AndroidConfigCollectorTask}.
 * Serialized to JSON so it can be read back during the analysis phase.
 */
public class AndroidVariantData {

  public final List<String> variantNames;
  public final Map<String, String> variantBuildTypes;
  public final Map<String, Integer> variantMinSdks;
  public final List<Integer> allMinSdks;
  @Nullable
  public final String testBuildType;
  /** variant name &rarr; list of source directory/file absolute paths */
  public final Map<String, List<String>> variantSourceDirs;
  /** Absolute paths of the Android SDK boot classpath jars (android.jar, etc.) */
  public final List<String> bootClasspath;

  public AndroidVariantData(
    List<String> variantNames,
    Map<String, String> variantBuildTypes,
    Map<String, Integer> variantMinSdks,
    List<Integer> allMinSdks,
    @Nullable String testBuildType,
    Map<String, List<String>> variantSourceDirs,
    List<String> bootClasspath) {
    this.variantNames = variantNames;
    this.variantBuildTypes = variantBuildTypes;
    this.variantMinSdks = variantMinSdks;
    this.allMinSdks = allMinSdks;
    this.testBuildType = testBuildType;
    this.variantSourceDirs = variantSourceDirs;
    this.bootClasspath = bootClasspath != null ? bootClasspath : Collections.emptyList();
  }
}
