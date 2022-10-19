/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarqube.gradle.android;

public interface AndroidProperties {

  /**
   * This property will be set when android project is being analyzed
   */
  String ANDROID_DETECTED = "sonar.android.detected";

  /**
   * This property contains the minimum value of minSdkVersion properties defined in build.gradle.
   * In case we detect the specific variant of the project being analyzed, then we set the value of minSdkVersion defined for this variant
   *
   * Not every android project needs to set this property.
   */
  String MIN_SDK_VERSION_MIN = "sonar.android.minsdkversion.min";

  /**
   * This property contains the maximum value of minSdkVersion properties defined in build.gradle.
   * In case we detect the specific variant of the project being analyzed, then we set the value of minSdkVersion defined for this variant
   *
   * Not every android project needs to set this property.
   */
  String MIN_SDK_VERSION_MAX = "sonar.android.minsdkversion.max";
}
