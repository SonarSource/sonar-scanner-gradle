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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.sonarqube.gradle.properties.SonarProperty;

final class SonarModuleProperties {
  private SonarModuleProperties() { }

  static void merge(Map<String, String> result, Set<String> userKeys, Set<File> files, String rootBuildScript) {
    Map<String, Properties> modules = new TreeMap<>();
    for (File file : files) {
      Properties properties = new Properties();
      try (InputStream stream = Files.newInputStream(file.toPath())) {
        properties.load(stream);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot read Sonar project properties: " + file, e);
      }
      String path = properties.getProperty("_gradle.path");
      if (path == null || modules.put(path, properties) != null) {
        throw new IllegalStateException("Missing or duplicate Sonar project path in " + file);
      }
    }
    List<String> buildScripts = new ArrayList<>();
    if (rootBuildScript.endsWith(".kts")) {
      buildScripts.add(rootBuildScript);
    }
    addChildren(":", "", modules, result, userKeys, buildScripts);
    result.computeIfPresent(SonarProperty.PROJECT_BASE_DIR, (key, value) -> SonarUtils.findProjectBaseDir(new HashMap<>(result)));
    if (!userKeys.contains(SonarProperty.PROJECT_SOURCE_DIRS) && !buildScripts.isEmpty()) {
      String existing = result.getOrDefault(SonarProperty.PROJECT_SOURCE_DIRS, "");
      String scripts = SonarUtils.joinAsCsv(buildScripts);
      result.put(SonarProperty.PROJECT_SOURCE_DIRS, existing.isEmpty() ? scripts : existing + "," + scripts);
    }
  }

  private static void addChildren(String parent, String prefix, Map<String, Properties> modules,
    Map<String, String> result, Set<String> userKeys, List<String> buildScripts) {
    List<String> children = modules.keySet().stream()
      .filter(path -> parent(path).equals(parent))
      .filter(path -> !Boolean.parseBoolean(modules.get(path).getProperty("_gradle.skip")))
      .collect(Collectors.toList());
    if (!children.isEmpty()) {
      result.put(prefix + SonarProperty.MODULES, String.join(",", children));
    }
    for (String path : children) {
      Properties module = modules.get(path);
      String childPrefix = prefix + path + ".";
      for (String key : module.stringPropertyNames()) {
        if (!key.startsWith("_gradle.")) {
          result.put(childPrefix + key, module.getProperty(key));
        }
      }
      result.putIfAbsent(childPrefix + SonarProperty.MODULE_KEY, result.get(SonarProperty.PROJECT_KEY) + path);
      Arrays.stream(module.getProperty("_gradle.userKeys", "").split("\n")).filter(key -> !key.isEmpty())
        .forEach(key -> userKeys.add(childPrefix + key));
      String buildScript = module.getProperty("_gradle.buildScript", "");
      if (buildScript.endsWith(".kts")) {
        buildScripts.add(buildScript);
      }
      addChildren(path, childPrefix, modules, result, userKeys, buildScripts);
    }
  }

  private static String parent(String path) {
    int separator = path.lastIndexOf(':');
    return separator == 0 ? ":" : path.substring(0, separator);
  }
}
