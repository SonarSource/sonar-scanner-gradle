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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.util.GradleVersion;

public class SonarUtils {

  private static final Pattern REPORT_PATH_PROPERTY_PATTERN = Pattern.compile(
    "^sonar\\.(coverageReportPaths|([^.]++\\.)++(xml)?reports?paths?)$",
    Pattern.CASE_INSENSITIVE
  );

  static final String SONAR_JAVA_SOURCE_PROP = "sonar.java.source";
  static final String SONAR_JAVA_TARGET_PROP = "sonar.java.target";
  static final String SONAR_JAVA_ENABLE_PREVIEW_PROP = "sonar.java.enablePreview";

  /**
   * Find test files given the path by looking for the keyword "test", for example:
   * - script/test/run.sh
   *          ^^^^
   * But exclude not test related English words. AI was used to find the most common words.
   */
  private static final Pattern TEST_FILE_PATH_PATTERN = Pattern.compile(
    // Exclude valid English words ending with "test": attest, contest, detest, latest, protest
    "(?<!at|con|de|la|pro)" +
      // Find path containing "test"
      "test" +
      // Exclude valid English words starting with "test":
      // - testate, testator, testatrix, testament, testimonial, testimony, testiness, testy
      "(?!ate|ator|atrix|ament|imonial|imony|iness|y)",
    Pattern.CASE_INSENSITIVE);

  private SonarUtils() {
    // Utility class
  }

  static boolean isAndroidProject(Project project) {
    return project.getPlugins().hasPlugin("com.android.application")
      || project.getPlugins().hasPlugin("com.android.library")
      || project.getPlugins().hasPlugin("com.android.test")
      || project.getPlugins().hasPlugin("com.android.feature")
      || project.getPlugins().hasPlugin("com.android.dynamic-feature");
  }

  /**
   * Return sourceSets using the best supported method according to the Gradle version.
   * Because Gradle 7 deprecated JavaPluginConvention, with removal planned for Gradle 9, projects using Gradle 7 and
   * greater should use JavaPluginExtension instead.
   * However, since projects using Gradle 7 or lower cannot use the replacement JavaPluginExtension API, we maintain a
   * path that keeps compatibility and removes warning logs from build with Gradle 7 or greater.
   *
   * @param project The (sub-)project under analysis
   * @return A container with the "main" and "test" source sets
   */
  static SourceSetContainer getSourceSets(Project project) {
    GradleVersion gradleVersion = GradleVersion.version(project.getGradle().getGradleVersion());
    if (isCompatibleWithJavaPluginExtension(gradleVersion)) {
      return getSourceSetsGradle7orGreater(project);
    }
    return getSourceSetsGradleLegacy(project);
  }

  @Nullable
  private static SourceSetContainer getSourceSetsGradle7orGreater(Project project) {
    JavaPluginExtension javaPluginExtension = new DslObject(project).getExtensions().findByType(JavaPluginExtension.class);
    if (javaPluginExtension == null) {
      return null;
    }
    return javaPluginExtension.getSourceSets();
  }

  @Nullable
  @SuppressWarnings("java:S1874")
  private static SourceSetContainer getSourceSetsGradleLegacy(Project project) {
    JavaPluginConvention javaPluginConvention = new DslObject(project).getConvention().findPlugin(JavaPluginConvention.class);
    if (javaPluginConvention == null) {
      return null;
    }
    return javaPluginConvention.getSourceSets();
  }

  static boolean isCompatibleWithJavaPluginExtension(GradleVersion version) {
    return version.getBaseVersion().compareTo(GradleVersion.version("7.0")) >= 0;
  }

  static String capitalize(final String word) {
    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
  }

  static String findProjectBaseDir(Map<String, Object> properties) {
    Path rootBaseDir = Paths.get(properties.get("sonar.projectBaseDir").toString()).toAbsolutePath().normalize();

    List<Path> allProjectsBaseDir = properties.entrySet().stream()
      .filter(e -> e.getKey().endsWith(".projectBaseDir"))
      .map(e -> Paths.get(e.getValue().toString()))
      .collect(Collectors.toList());

    for (Path baseDir : allProjectsBaseDir) {
      Path normalizedBaseDir = baseDir.toAbsolutePath().normalize();

      if (!normalizedBaseDir.getRoot().equals(rootBaseDir.getRoot())) {
        continue;
      }

      if (!normalizedBaseDir.startsWith(rootBaseDir)) {
        int c1 = normalizedBaseDir.getNameCount();
        int c2 = rootBaseDir.getNameCount();
        Path newBaseDir = rootBaseDir.getRoot();
        for (int i = 0; i < c1 && i < c2 && normalizedBaseDir.getName(i).equals(rootBaseDir.getName(i)); i++) {
          newBaseDir = newBaseDir.resolve(rootBaseDir.getName(i));
        }
        rootBaseDir = newBaseDir;
      }
    }
    return rootBaseDir.toString();
  }

  static void setTestClasspathProps(Map<String, Object> properties, Collection<File> testClassDirs, Collection<File> testLibraries) {
    appendProps(properties, "sonar.java.test.binaries", exists(testClassDirs));
    appendProps(properties, "sonar.java.test.libraries", exists(testLibraries));
  }

  static void setMainClasspathProps(Map<String, Object> properties, Collection<File> mainClassDirs, Collection<File> mainLibraries, boolean addForGroovy) {
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

  static void populateJdkProperties(Map<String, Object> properties, JavaCompilerConfiguration config) {
    config.getJdkHome().ifPresent(s -> properties.put("sonar.java.jdkHome", s));
    Optional<String> release = config.getRelease();
    if (release.isPresent()) {
      properties.put(SONAR_JAVA_SOURCE_PROP, release.get());
      properties.put(SONAR_JAVA_TARGET_PROP, release.get());
    } else {
      config.getSource().ifPresent(s -> properties.put(SONAR_JAVA_SOURCE_PROP, s));
      config.getTarget().ifPresent(t -> properties.put(SONAR_JAVA_TARGET_PROP, t));
    }
    properties.put(SONAR_JAVA_ENABLE_PREVIEW_PROP, config.getEnablePreview());
  }

  /**
   * Filters a collection files returning only the existing ones.
   */
  static List<File> exists(Iterable<File> files) {
    List<File> list = new ArrayList<>();
    for (File file : files) {
      if (!list.contains(file) && file.exists()) {
        list.add(file);
      }
    }
    return list;
  }

  static void appendProps(Map<String, Object> properties, String key, Iterable<?> valuesToAppend) {
    Set<Object> newList = new LinkedHashSet<>();
    Object previousValue = properties.get(key);
    if (previousValue instanceof Collection) {
      newList.addAll((Collection<Object>) previousValue);
    } else if (previousValue != null) {
      newList.add(previousValue);
    }
    for (Object value : valuesToAppend) {
      newList.add(value);
    }
    properties.put(key, newList);
  }

  static void appendSourcesProp(Map<String, Object> properties, Iterable<File> filesToAppend, boolean testSources) {
    List<File> filteredList = filterOutSubFiles(filesToAppend);
    appendProps(properties, testSources ? ScanProperties.PROJECT_TEST_DIRS : ScanProperties.PROJECT_SOURCE_DIRS, filteredList);
  }

  static List<File> filterOutSubFiles(Iterable<File> files) {
    return StreamSupport.stream(files.spliterator(), false)
      .filter(file -> {
        for (File other : files) {
          if (!file.equals(other) && file.getAbsolutePath().startsWith(other.getAbsolutePath())) {
            return false;
          }
        }
        return true;
      })
      .collect(Collectors.toList());
  }

  static void appendProp(Map<String, Object> properties, String key, Object valueToAppend) {
    properties.putIfAbsent(key, new LinkedHashSet<String>());
    ((Collection<Object>) properties.get(key)).add(valueToAppend);
  }

  @Nullable
  public static <T> List<T> nonEmptyOrNull(Collection<T> collection) {
    List<T> list = Collections.unmodifiableList(new ArrayList<>(collection));
    return list.isEmpty() ? null : list;
  }

  /**
   * Joins a list of strings that may contain commas by wrapping those strings in double quotes, like in CSV format.
   * <p>
   * For example:
   * values = { "/home/users/me/artifact-123,456.jar", "/opt/lib" }
   * return is the string: "\"/home/users/me/artifact-123,456.jar\",/opt/lib"
   *
   * @param values
   * @return a string having all the values separated by commas
   * and each single value that contains a comma wrapped in double quotes
   */
  public static String joinAsCsv(List<String> values) {
    return values.stream()
      .map(SonarUtils::escapeCommas)
      .collect(Collectors.joining(","));
  }

  private static String escapeCommas(String value) {
    // escape only when needed
    return value.contains(",") ? ("\"" + value + "\"") : value;
  }

  public static List<String> splitAsCsv(String joined) {
    List<String> collected = new ArrayList<>();
    if (joined.indexOf('"') == -1) {
      return Arrays.asList(joined.split(","));
    }
    int start = 0;
    int end = joined.length() - 1;
    while (start < end && end < joined.length()) {
      if (joined.charAt(start) == '"') {
        end = joined.indexOf('"', start + 1);
        String value = joined.substring(start + 1, end);
        collected.add(value);
        int nextComma = joined.indexOf(",", end);
        if (nextComma == -1) {
          break;
        }
        start = nextComma + 1;
      } else {
        int nextComma = joined.indexOf(",", start);
        if (nextComma == -1) {
          end = joined.length();
        } else {
          end = nextComma;
        }
        String value = joined.substring(start, end);
        collected.add(value);
        start = end + 1;
      }
      end = start + 1;
    }

    return collected;
  }

  /**
   * Returns the paths listed under the external or coverage report path parameters found in the properties.
   *
   * @param properties Properties to explore
   * @return The set of paths that point to external reports
   */
  public static Set<Path> extractReportPaths(Map<String, Object> properties) {
    return properties.entrySet()
      .stream()
      .filter(entry -> isReportPathProperty(entry.getKey()))
      .map(Map.Entry::getValue)
      .filter(String.class::isInstance)
      .map(String.class::cast)
      .map(SonarUtils::splitAsCsv)
      .flatMap(Collection::stream)
      .map(String::trim)
      .map(Path::of)
      .collect(Collectors.toSet());
  }

  /**
   * Computes the absolute paths for the report paths extracted from the properties.
   * @return The set of absolute paths to external and coverage reports
   * @throws IllegalStateException if the property "sonar.projectBaseDir" is not defined in the properties argument
   */
  public static Set<Path> computeReportPaths(Map<String, Object> properties) {
    if (!properties.containsKey("sonar.projectBaseDir")) {
      throw new IllegalStateException("Cannot compute absolute paths for reports because \"sonar.projectBaseDir\" is not defined.");
    }
    Path projectBaseDir = Path.of(findProjectBaseDir(properties));
    return extractReportPaths(properties)
      .stream()
      .map(originalPath -> originalPath.isAbsolute() ? originalPath : projectBaseDir.resolve(originalPath))
      .collect(Collectors.toSet());
  }

  private static boolean isReportPathProperty(String propertyName) {
    return REPORT_PATH_PROPERTY_PATTERN.matcher(propertyName.trim()).matches();
  }

  public static InputFileType findProjectFileType(Path projectDir, Path filePath) {
    String relativePath = projectDir.relativize(filePath).toString();
    return TEST_FILE_PATH_PATTERN.matcher(relativePath).find() ? InputFileType.TEST : InputFileType.MAIN;
  }

  public enum InputFileType {
    MAIN,
    TEST
  }

  /**
   * Produces a prefix for property keys that is appropriate and consumable by the scanner-engine
   */
  public static String constructPrefixedProjectName(String projectPath) {
    String[] parts = projectPath.split(":");
    parts = Arrays.stream(parts).filter(s -> !s.isBlank()).toArray(String[]::new);
    StringBuilder result = new StringBuilder(":");
    for (int i = 0; i < parts.length - 1; i++) {
      for (int j = 0; j <= i; j++) {
        result.append(parts[j]);
        if (j < i) {
          result.append(":");
        }
      }
      result.append(".:");
    }
    result.append(projectPath.substring(1));
    return result.toString();
  }

}
