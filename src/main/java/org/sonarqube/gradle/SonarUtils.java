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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.gradle.api.Project;

public class SonarUtils {
  static boolean isAndroidProject(Project project) {
    return project.getPlugins().hasPlugin("com.android.application") || project.getPlugins().hasPlugin("com.android.library")
      || project.getPlugins().hasPlugin("com.android.test") || project.getPlugins().hasPlugin("com.android.feature");
  }

  static String capitalize(final String word) {
    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
  }

  static boolean isRoot(Project project) {
    return project.getRootProject().equals(project);
  }

  static void setTestClasspathProps(Map<String, Object> properties, Collection<File> testClassDirs, Collection<File> testLibraries) {
    appendProps(properties, "sonar.java.test.binaries", exists(testClassDirs));
    appendProps(properties, "sonar.java.test.libraries", exists(testLibraries));
  }

  static void setMainClasspathProps(Map<String, Object> properties, boolean addForGroovy, Collection<File> mainClassDirs, Collection<File> mainLibraries) {
    appendProps(properties, "sonar.java.binaries", exists(mainClassDirs));
    if (addForGroovy) {
      appendProps(properties, "sonar.groovy.binaries", exists(mainClassDirs));
    }
    // Populate deprecated properties for backward compatibility
    appendProps(properties, "sonar.binaries", exists(mainClassDirs));

    appendProps(properties, "sonar.java.libraries", exists(mainLibraries));
    // Populate deprecated properties for backward compatibility
    appendProps(properties, "sonar.libraries", exists(mainLibraries));
  }

  static List<File> exists(Collection<File> files) {
    return files.stream().filter(File::exists).collect(Collectors.toList());
  }

  static void appendProps(Map<String, Object> properties, String key, Iterable<?> valuesToAppend) {
    properties.putIfAbsent(key, new LinkedHashSet<String>());
    StreamSupport.stream(valuesToAppend.spliterator(), false)
      .forEach(v -> ((Collection<String>) properties.get(key)).add(v.toString()));
  }

  static void appendProp(Map<String, Object> properties, String key, Object valueToAppend) {
    properties.putIfAbsent(key, new LinkedHashSet<String>());
    ((Collection<String>) properties.get(key)).add(valueToAppend.toString());
  }

  @Nullable
  static <T> List<T> nonEmptyOrNull(Collection<T> collection) {
    List<T> list = Collections.unmodifiableList(new ArrayList<>(collection));
    return list.isEmpty() ? null : list;
  }
}
