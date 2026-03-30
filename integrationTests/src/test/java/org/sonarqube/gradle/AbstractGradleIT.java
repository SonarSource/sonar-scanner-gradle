/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarqube.gradle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.Semver.SemverType;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.SoftAssertions;
import org.jspecify.annotations.Nullable;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.gradle.run_configuration.DefaultRunConfiguration;
import org.sonarqube.gradle.run_configuration.RunConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractGradleIT {

  private static final Gson GSON = new Gson();
  private static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
  private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
  public static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");
  private static final String ANDROID_SDK_TOKEN = "${ANDROID_SDK}";

  @Rule
  public TemporaryFolder temp = TemporaryFolder.builder().build();

  private static final Semver gradleVersion;
  private static final Semver androidGradleVersion;

  static {
    try {
      gradleVersion = new Semver(IOUtils.toString(AbstractGradleIT.class.getResource("/gradleversion.txt"), StandardCharsets.UTF_8), SemverType.LOOSE);

      String androidGradleVersionString = IOUtils.toString(AbstractGradleIT.class.getResource("/androidgradleversion.txt"), StandardCharsets.UTF_8);
      if ("NOT_AVAILABLE".equals(androidGradleVersionString)) {
        androidGradleVersion = null;
      } else {
        androidGradleVersion = new Semver(androidGradleVersionString, SemverType.LOOSE);
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static Semver getGradleVersion() {
    return gradleVersion;
  }

  protected static void ignoreThisTestIfGradleVersionIsLessThan(String version) {
    Semver gradleVersion = getGradleVersion();
    Assume.assumeTrue("Test is ignored for Gradle version " + gradleVersion + " which should be greater than or equal to " + version, gradleVersion.isGreaterThanOrEqualTo(version));
  }

  protected static void ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo(String version) {
    Semver gradleVersion = getGradleVersion();
    Assume.assumeTrue("Test is ignored for Gradle version " + gradleVersion + " which should be lower than " + version, gradleVersion.isLowerThan(version));
  }

  protected static void ignoreThisTestIfGradleVersionIsNotBetween(String minVersionIncluded, String maxVersionExcluded) {
    ignoreThisTestIfGradleVersionIsLessThan(minVersionIncluded);
    ignoreThisTestIfGradleVersionIsGreaterThanOrEqualTo(maxVersionExcluded);
  }

  protected static Map<String, String> extractComparableProperties(Properties props) {
    String absoluteProjectBaseDir = props.getProperty("sonar.projectBaseDir");
    if (absoluteProjectBaseDir == null) {
      throw new IllegalStateException("sonar.projectBaseDir is null");
    }
    Set<String> hiddenProperties = new HashSet<>(Arrays.asList(
      // different on each machine, nothing to compare
      "sonar.java.jdkHome", "sonar.scanner.arch", "sonar.scanner.os", "sonar.scanner.opts", "sonar.token",
      // path to a different temporary file for each run, e.g. junit17577551948278219342.tmp
      "sonar.scanner.internal.dumpToFile",
      // depends on the plugin and gradle version that run the test, e.g. 6.3-SNAPSHOT/Gradle 9.0.0
      "sonar.scanner.appVersion"));
    // All environment variables started with SONAR_SCANNER_* will pollute the test by adding sonar.scanner.* properties
    // We are forced to filter them out
    Set<String> sonarScannerPropertiesToTest = new HashSet<>(Arrays.asList(
      "sonar.scanner.apiBaseUrl",
      "sonar.scanner.app",
      "sonar.scanner.appVersion",
      "sonar.scanner.arch",
      "sonar.scanner.internal.dumpToFile",
      "sonar.scanner.os",
      "sonar.scanner.wasEngineCacheHit"));
    Map<String, String> replacementMap = new LinkedHashMap<>();
    replacementMap.put("${parentBaseDir}", Paths.get(absoluteProjectBaseDir).getParent().toString());
    replacementMap.put("${currentWorkingDir}", System.getProperty("user.dir"));
    replacementMap.put("${HOME}", System.getProperty("user.home"));
    String gitHubWorkingDir = System.getenv("GITHUB_WORKING_DIR");
    if (gitHubWorkingDir != null) {
      replacementMap.put("${HOME}", gitHubWorkingDir.replace('/', File.separatorChar));
    }
    Pattern dependenciesInGradleCache = Pattern.compile("(?<=,|^)[^,]+/.gradle/caches/modules-\\d+/files-[0-9.]+/" +
      "(?<groupId>[^/,]++)/(?<artifactId>[^/,]++)/(?<version>[^/,]++)/[0-9a-f]{40}/\\k<artifactId>-\\k<version>\\.jar(?=,|$)");
    Pattern gradleTransformArtifacts = Pattern.compile("(?<=,|^)(?<prefix>[^,]*/\\.gradle/caches/transforms-\\d+/)[0-9a-f]+(?<suffix>/transformed/[^,]+)(?=,|$)");
    Pattern projectTransformArtifacts = Pattern.compile("(?<=,|^)(?<prefix>[^,]*/build/\\.transforms/)[0-9a-f]+(?<suffix>/transformed/[^,]+)(?=,|$)");
    Map<String, String> result = new LinkedHashMap<>();
    props.stringPropertyNames().stream()
      .sorted()
      .filter(key -> !key.equals("sonar.token"))
      .filter(key -> !key.startsWith("sonar.scanner.") || sonarScannerPropertiesToTest.contains(key))
      .forEach(key -> {
        String value = props.getProperty(key);
        if (hiddenProperties.contains(key) || key.endsWith(".sonar.java.jdkHome")) {
          value = "<hidden>";
        }
        for (Map.Entry<String, String> entry : replacementMap.entrySet()) {
          value = replaceAllPrefixInCommaSeparatedString(value, entry.getValue(), entry.getKey());
        }
        value = value.replace('\\', '/');
        value = normalizeAndroidSdkRoots(value);
        value = normalizeAndroidGeneratedPaths(value);
        for (String replacementKey : replacementMap.keySet()) {
          value = replaceAllPrefixInCommaSeparatedString(value, replacementKey + "/.m2", "${M2}");
        }
        value = dependenciesInGradleCache.matcher(value).replaceAll(res ->
        // Java 11 does not support res.group("groupId") this is why there is res.group(1)
        "\\${M2}/repository/" + res.group(1).replace(".", "/") + "/${artifactId}/${version}/${artifactId}-${version}.jar");
        value = gradleTransformArtifacts.matcher(value).replaceAll("${prefix}<hash>${suffix}");
        value = projectTransformArtifacts.matcher(value).replaceAll("${prefix}<hash>${suffix}");
        result.put(key, value);
      });
    return result;
  }

  protected static Map<String, String> expandSnapshotPlaceholders(Map<String, String> properties, Map<String, String> actualProperties) {
    Map<String, String> expanded = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      String value = entry.getValue();
      if (value != null) {
        String actualValue = actualProperties.get(entry.getKey());
        if (actualValue != null) {
          value = value.replace("${JAVA_VERSION}", actualValue);
          value = value.replace("${JAVA_SOURCE}", actualValue);
          value = value.replace("${JAVA_TARGET}", actualValue);
        }
        value = normalizeAndroidSdkRoots(value);
        value = normalizeAndroidGeneratedPaths(value);
      }
      expanded.put(entry.getKey(), value);
    }
    return expanded;
  }

  protected static Map<String, String> canonicalizeSnapshotPlaceholders(Map<String, String> properties) {
    Map<String, String> canonicalized = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      String value = entry.getValue();
      if (value != null) {
        value = normalizeAndroidSdkRoots(value);
        value = normalizeAndroidGeneratedPaths(value);
        if (isJavaVersionSnapshotProperty(entry.getKey())) {
          value = entry.getKey().endsWith(".source") ? "${JAVA_SOURCE}" : "${JAVA_TARGET}";
        }
      }
      canonicalized.put(entry.getKey(), value);
    }
    return canonicalized;
  }

  private static boolean isJavaVersionSnapshotProperty(String key) {
    return "sonar.java.source".equals(key)
      || "sonar.java.target".equals(key)
      || key.endsWith(".sonar.java.source")
      || key.endsWith(".sonar.java.target");
  }

  private static String normalizeAndroidSdkRoots(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    return replaceKnownAndroidSdkRoots(value);
  }

  private static String replaceKnownAndroidSdkRoots(String value) {
    for (String sdkRoot : getKnownAndroidSdkRoots()) {
      value = replaceAllPrefixInCommaSeparatedString(value, sdkRoot, ANDROID_SDK_TOKEN);
    }
    return value;
  }

  private static String normalizeAndroidGeneratedPaths(String value) {
    return value
      .replace("/build-tools/34.0.0/core-lambda-stubs.jar", "/build-tools/30.0.3/core-lambda-stubs.jar")
      .replace("/compileDebugJavaWithJavac/classes", "/classes")
      .replace("/compileDebugUnitTestJavaWithJavac/classes", "/classes")
      .replace("/compileDebugAndroidTestJavaWithJavac/classes", "/classes")
      .replace("/compileReleaseJavaWithJavac/classes", "/classes")
      .replace("/compileReleaseUnitTestJavaWithJavac/classes", "/classes")
      .replace("/compileDemoMinApi23DebugJavaWithJavac/classes", "/classes")
      .replace("/compileDemoMinApi23DebugUnitTestJavaWithJavac/classes", "/classes")
      .replace("/compileDemoMinApi23DebugAndroidTestJavaWithJavac/classes", "/classes")
      .replace("/compileFlavor1DebugJavaWithJavac/classes", "/classes")
      .replace("/compileFlavor1DebugUnitTestJavaWithJavac/classes", "/classes")
      .replace("/compileFlavor1DebugAndroidTestJavaWithJavac/classes", "/classes")
      .replace("/processDebugResources/R.jar", "/R.jar")
      .replace("/processDebugUnitTestResources/R.jar", "/R.jar")
      .replace("/processDebugAndroidTestResources/R.jar", "/R.jar")
      .replace("/processReleaseResources/R.jar", "/R.jar")
      .replace("/processDemoMinApi23DebugResources/R.jar", "/R.jar")
      .replace("/processDemoMinApi23DebugAndroidTestResources/R.jar", "/R.jar")
      .replace("/processFlavor1DebugResources/R.jar", "/R.jar")
      .replace("/processFlavor1DebugAndroidTestResources/R.jar", "/R.jar")
      .replace("/generateDebugRFile/R.jar", "/R.jar")
      .replace("/bundleDebugClassesToCompileJar/classes.jar", "/classes.jar")
      .replace("/bundleLibCompileToJarDebug/classes.jar", "/classes.jar")
      .replace("/bundleDemoMinApi23DebugClassesToCompileJar/classes.jar", "/classes.jar")
      .replace("/bundleFlavor1DebugClassesToCompileJar/classes.jar", "/classes.jar");
  }

  private static List<String> getKnownAndroidSdkRoots() {
    List<String> candidates = new ArrayList<>();
    addAndroidSdkCandidate(candidates, System.getenv("ANDROID_HOME"));
    addAndroidSdkCandidate(candidates, System.getenv("ANDROID_SDK_ROOT"));
    addAndroidSdkCandidate(candidates, "/usr/local/lib/android/sdk");
    addAndroidSdkCandidate(candidates, "C:/Android/Sdk");
    addAndroidSdkCandidate(candidates, "C:\\Android\\Sdk");
    return candidates;
  }

  private static void addAndroidSdkCandidate(List<String> candidates, @Nullable String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return;
    }
    String normalized = candidate.replace('\\', '/');
    candidates.add(normalized);
    Path path = Paths.get(normalized);
    if (Files.exists(path)) {
      candidates.add(path.toAbsolutePath().normalize().toString().replace('\\', '/'));
    }
  }

  protected static Map<String, String> loadExpectedMap(String resourcePath) throws IOException {
    try (Reader reader = new InputStreamReader(
      java.util.Objects.requireNonNull(AbstractGradleIT.class.getResourceAsStream(resourcePath)),
      StandardCharsets.UTF_8)) {
      return GSON.fromJson(reader, STRING_MAP_TYPE);
    }
  }

  protected static void writeExpectedMap(Path targetFile, Map<String, String> properties) throws IOException {
    File parent = targetFile.toFile().getParentFile();
    if (parent != null && !parent.exists()) {
      parent.mkdirs();
    }
    try (FileWriter writer = new FileWriter(targetFile.toFile(), StandardCharsets.UTF_8)) {
      PRETTY_GSON.toJson(canonicalizeSnapshotPlaceholders(properties), writer);
    }
  }

  private static String replaceAllPrefixInCommaSeparatedString(String value, String oldPrefix, String newPrefix) {
    String prefixAfterComma = ',' + oldPrefix;
    int index = value.lastIndexOf(prefixAfterComma);
    while (index != -1) {
      value = value.substring(0, index + 1) + newPrefix + value.substring(index + prefixAfterComma.length());
      index = value.lastIndexOf(prefixAfterComma);
    }
    if (value.startsWith(oldPrefix)) {
      value = newPrefix + value.substring(oldPrefix.length());
    }
    return value;
  }

  protected static Semver getAndroidGradleVersion() {
    return androidGradleVersion;
  }

  protected static String getRequiredProperty(Properties props, String key) {
    String value = props.getProperty(key);
    if (value == null) {
      throw new IllegalStateException("Missing property: " + key);
    }
    return value;
  }

  protected static Path getRequiredPathProperty(Properties props, String key) {
    return normalizePath(Paths.get(getRequiredProperty(props, key)));
  }

  protected static List<Path> getPathListProperty(Properties props, String key) {
    return Arrays.stream(getRequiredProperty(props, key).split(","))
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .map(Paths::get)
      .map(AbstractGradleIT::normalizePath)
      .collect(Collectors.toList());
  }

  protected static void assertPathProperty(Properties props, String key, Path expected) {
    assertThat(getRequiredPathProperty(props, key)).isEqualTo(normalizePath(expected));
  }

  protected static void assertPathListPropertyContainsOnly(Properties props, String key, Path... expectedPaths) {
    assertThat(getPathListProperty(props, key))
      .containsOnly(Arrays.stream(expectedPaths).map(AbstractGradleIT::normalizePath).toArray(Path[]::new));
  }

  protected static void assertPathListPropertyContainsExactlyInAnyOrder(Properties props, String key, Path... expectedPaths) {
    assertThat(getPathListProperty(props, key))
      .containsExactlyInAnyOrder(Arrays.stream(expectedPaths).map(AbstractGradleIT::normalizePath).toArray(Path[]::new));
  }

  protected static void assertPropertyContains(Properties props, String key, String... fragments) {
    assertThat(getRequiredProperty(props, key)).contains(fragments);
  }

  protected static void assertPropertyDoesNotContain(Properties props, String key, String... fragments) {
    assertThat(getRequiredProperty(props, key)).doesNotContain(fragments);
  }

  private static Path normalizePath(Path path) {
    return path.toAbsolutePath().normalize();
  }

  protected Properties runGradlewSonarSimulationMode(String project) throws Exception {
    return runGradlewSonarSimulationModeWithEnv(project, Collections.emptyMap(), new DefaultRunConfiguration());
  }

  protected Properties runGradlewSonarSimulationModeWithEnv(String project, Map<String, String> env,  RunConfiguration runConfiguration, String... args) throws Exception {
    return runGradlewSonarSimulationModeWithEnv(project, null, env, runConfiguration, args);
  }

  protected Properties runGradlewSonarSimulationModeWithEnv(String project, String exeRelativePath, Map<String, String> env,  RunConfiguration runConfiguration, String... args) throws Exception {
    File out = temp.newFile();
    String[] newArgs = Stream.concat(
        Stream.of("-Dsonar.scanner.internal.dumpToFile=" + out.getAbsolutePath()),
        Arrays.stream(args))
      .toArray(String[]::new);
    RunResult result = runGradlewSonarWithEnv(project, exeRelativePath, env, runConfiguration, newArgs);

    return result.getDumpedProperties().get();
  }

  protected RunResult runGradlewSonarWithEnv(String project, Map<String, String> env,  RunConfiguration runConfiguration, String... args) throws Exception {
    return runGradlewSonarWithEnv(project, null, env, runConfiguration, args);
  }

  protected RunResult runGradlewSonarWithEnv(String project, String exeRelativePath, Map<String, String> env,  RunConfiguration runConfiguration, String... args) throws Exception {
    RunResult result = runGradlewSonarWithEnvQuietly(project, exeRelativePath, env, runConfiguration, args);
    System.out.println(result.getLog());
    if (result.exitValue != 0) {
      throw new RuntimeException(result.log);
    }
    return result;
  }

  protected RunResult runGradlewSonarWithEnvQuietly(String project, Map<String, String> env,  RunConfiguration runConfiguration, String... args) throws Exception {
    return runGradlewSonarWithEnvQuietly(project, null, env, runConfiguration, args);
  }

  protected RunResult runGradlewSonarWithEnvQuietly(String project, String exeRelativePath, Map<String, String> env, RunConfiguration runConfiguration, String... args) throws Exception {
    List<String> newArgs = new ArrayList<>(args.length + 1);
    newArgs.addAll(Arrays.asList(args));
    newArgs.add("sonar");
    return runGradlewWithEnvQuietly(project, exeRelativePath, env, runConfiguration, newArgs.toArray(new String[args.length + 1]));
  }

  protected RunResult runGradlewWithEnvQuietly(String project, String exeRelativePath, Map<String, String> env, RunConfiguration runConfiguration, String... args) throws Exception {
    File projectBaseDir = new File(this.getClass().getResource(project).toURI());
    String projectDir = project.startsWith("/") ? "." + project : project;
    File tempProjectDir = new File(temp.getRoot(), projectDir);
    if (!tempProjectDir.exists()) {
      tempProjectDir = temp.newFolder(projectDir);
    }
    File outputFile = temp.newFile();
    FileUtils.copyDirectory(projectBaseDir, tempProjectDir);
    File exeDir = tempProjectDir;
    if (exeRelativePath != null) {
      exeDir = new File(exeDir, exeRelativePath);
    }
    List<String> command = new ArrayList<>();
    if (IS_WINDOWS) {
      command.addAll(Arrays.asList("cmd.exe", "/C", new File(exeDir, "gradlew.bat").getAbsolutePath()));
    } else {
      command.add("/bin/bash");
      command.add("gradlew");
    }
    command.addAll(Arrays.asList("--stacktrace", "--no-daemon", "--warning-mode", "all"));
    runConfiguration.updateProcessArgument(command);
    command.addAll(Arrays.asList(args));
    ProcessBuilder pb = new ProcessBuilder(command)
      .directory(exeDir)
      .redirectOutput(outputFile)
      .redirectErrorStream(true);
    if (getJavaVersion() > 8) {
      // Fix jacoco java 17 compatibility
      pb.environment().put("GRADLE_OPTS", "-Xmx1024m --add-opens=java.prefs/java.util.prefs=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED");
    } else {
      pb.environment().put("GRADLE_OPTS", "-Xmx1024m");
    }
    pb.environment().putAll(env);
    Process p = pb.start();
    p.waitFor();

    String output = FileUtils.readFileToString(outputFile, StandardCharsets.UTF_8);

    assertThatPluginDoesNotCreateGradleDeprecationWarnings(output);

    RunResult result = new RunResult(output, p.exitValue(), getDumpedProperties(command));
    runConfiguration.checkOutput(result);
    return result;
  }

  protected List<String> getGradlewCommand() {
    List<String> command = new ArrayList<>();
    if (System.getProperty("os.name").startsWith("Windows")) {
      command.addAll(Arrays.asList("cmd.exe", "/C", "gradlew.bat"));
    } else {
      command.add("/bin/bash");
      command.add("gradlew");
    }
    return command;
  }

  protected static int getJavaVersion() {
    String version = System.getProperty("java.version");
    if (version.startsWith("1.")) {
      version = version.substring(2, 3);
    } else {
      int dot = version.indexOf(".");
      if (dot != -1) {
        version = version.substring(0, dot);
      }
    }
    return Integer.parseInt(version);
  }

  @Nullable
  protected static Properties getDumpedProperties(List<String> command) throws IOException {
    for (String part : command) {
      if (part.trim().startsWith("-Dsonar.scanner.internal.dumpToFile=")) {
        File dumpFile = new File(part.split("=")[1]);
        return loadProperties(dumpFile);
      }
    }
    return null;
  }

  private static Properties loadProperties(File out) throws IOException {
    Properties props = new Properties();
    try (FileReader fr = new FileReader(out)) {
      props.load(fr);
    }
    return props;
  }

  public static class RunResult {
    private final String log;
    private final int exitValue;
    private final Properties dumpedProperties;

    RunResult(String log, int exitValue, @Nullable Properties dumpedProperties) {
      this.log = log;
      this.exitValue = exitValue;
      this.dumpedProperties = dumpedProperties;
    }

    public String getLog() {
      return log;
    }

    public int getExitValue() {
      return exitValue;
    }

    public Optional<Properties> getDumpedProperties() {
      return Optional.ofNullable(dumpedProperties);
    }
  }

  private static final List<String> GRADLE_DEPRECATION_PATTERNS = Arrays.asList(
          "has been deprecated"
  );

  /**
   * Ensure that the output of the gradle analysis logs do not contain any deprecation warnings caused the sonar-gradle-plugin.
   * @param text Text of the output to check
   */
  static void assertThatPluginDoesNotCreateGradleDeprecationWarnings(String text) {
    SoftAssertions softly = new SoftAssertions();
    String[] lines = text.split(System.lineSeparator());
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (GRADLE_DEPRECATION_PATTERNS.stream().anyMatch(line::contains)) {
        int j = i + 1;
        List<String> stackTraceLines = new ArrayList<>();
        stackTraceLines.add(line);
        while (j < lines.length && lines[j].matches("^\\s++at\\s++.+")) {
          stackTraceLines.add(lines[j]);
          j++;
        }
        softly.assertThat(stackTraceLines)
          .noneMatch(AbstractGradleIT::isUnexpectedWarning);
        i = j - 1;
      }
    }
    softly.assertAll();
  }

  static boolean isUnexpectedWarning(String line){
    if(line.contains("SonarResolverTask.java:152") || line.contains("SonarResolverTask.java:155")){
      // These warnings are expected until we properly support Gradle 9
      return false;
    }
    return line.contains("org.sonarqube.gradle");
  }

}
