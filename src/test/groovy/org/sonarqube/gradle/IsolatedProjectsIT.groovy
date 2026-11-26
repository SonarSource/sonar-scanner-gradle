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
package org.sonarqube.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Exercises the real scanner under Gradle's project isolation enforcement.
 * <p>
 * The recommended configuration applies 'org.sonarqube' once, in the settings script. Subproject build scripts only
 * contain their own 'sonar { }' block, and never declare the plugin.
 */
class IsolatedProjectsIT extends Specification {

  private static final String INCLUDES = "rootProject.name = 'isolated-projects'\ninclude 'module-a', 'nested:module-b'\n"
  private static final String SETTINGS_WITH_PLUGIN = "plugins { id 'org.sonarqube' }\n" + INCLUDES
  private static final String ROOT_SONAR_BLOCK = "sonar { properties { property 'sonar.projectKey', 'isolated-root' } }\n"

  @TempDir
  Path projectDir

  def setup() {
    TestKitGradleProperties.configure(projectDir)
    write('settings.gradle', SETTINGS_WITH_PLUGIN)
    write('build.gradle', ROOT_SONAR_BLOCK)
    write('module-a/build.gradle', """
      plugins { id 'java' }
      sonar { properties { property 'sonar.projectName', 'Named module A' } }
    """)
    write('nested/build.gradle', '')
    write('nested/module-b/build.gradle', "plugins { id 'java' }\n")
    write('module-a/src/main/java/A.java', 'class A {}')
    write('module-a/src/test/java/ATest.java', 'class ATest {}')
    write('nested/module-b/src/main/java/B.java', 'class B {}')
    write('nested/module-b/src/test/java/BTest.java', 'class BTest {}')
  }

  def 'settings plugin analyzes the whole build under isolation exactly like without isolation'() {
    given:
    def baselineFile = projectDir.resolve('baseline.properties')
    def isolatedFile = projectDir.resolve('isolated.properties')

    when:
    def baseline = runner(':module-a:classes', ':nested:module-b:classes', 'sonar',
      '--configuration-cache', dumpArgument(baselineFile)).build()
    def firstRun = runner(':module-a:classes', ':nested:module-b:classes', 'sonar',
      '--isolated-projects', dumpArgument(isolatedFile)).build()
    def secondRun = runner(':module-a:classes', ':nested:module-b:classes', 'sonar',
      '--isolated-projects', dumpArgument(isolatedFile)).build()

    then:
    sonarTasks(baseline) == [':sonar']
    sonarTasks(firstRun) == [':sonar']
    firstRun.task(':sonar').outcome == SUCCESS
    secondRun.task(':sonar').outcome == SUCCESS
    secondRun.output.contains('Reusing configuration cache')
    !firstRun.output.contains('isolated projects violation')
    comparableProperties(isolatedFile) == comparableProperties(baselineFile)

    and: 'the nested module graph and per-project custom properties remain intact'
    def properties = readProperties(isolatedFile)
    properties.getProperty('sonar.modules').split(',').toList().toSet() == [':module-a', ':nested'].toSet()
    properties.getProperty(':nested.sonar.modules') == ':nested:module-b'
    properties.getProperty(':module-a.sonar.projectName') == 'Named module A'
    properties.getProperty('sonar.projectKey') == 'isolated-root'
    properties.getProperty(':module-a.sonar.sources').replace('\\', '/').contains('src/main/java')
    properties.getProperty(':module-a.sonar.tests').replace('\\', '/').contains('src/test/java')
    properties.getProperty(':module-a.sonar.java.binaries') != null
    properties.getProperty(':nested.:nested:module-b.sonar.sources').replace('\\', '/').contains('src/main/java')
    properties.getProperty(':nested.:nested:module-b.sonar.java.binaries') != null
  }

  def 'only the root project owns an analysis task under isolation'() {
    when:
    def result = runner(':nested:sonar', '--isolated-projects').buildAndFail()

    then: 'a subproject has producer tasks only, so its analysis task name is not found'
    result.output.contains("Cannot locate tasks that match ':nested:sonar'")
    sonarTasks(result).isEmpty()
  }

  def 'Kotlin DSL settings plugin analyzes the whole build under isolation'() {
    given:
    Files.delete(projectDir.resolve('settings.gradle'))
    Files.delete(projectDir.resolve('build.gradle'))
    Files.delete(projectDir.resolve('module-a/build.gradle'))
    write('settings.gradle.kts', """
      plugins { id("org.sonarqube") }
      rootProject.name = "isolated-projects"
      include("module-a", "nested:module-b")
    """)
    write('build.gradle.kts', 'sonar { properties { property("sonar.projectKey", "isolated-kts-root") } }\n')
    write('module-a/build.gradle.kts', """
      plugins { java }
      sonar { properties { property("sonar.projectName", "Kotlin module A") } }
    """)
    def dumpFile = projectDir.resolve('kts.properties')

    when:
    def result = runner('sonar', '--isolated-projects', dumpArgument(dumpFile)).build()

    then:
    sonarTasks(result) == [':sonar']
    !result.output.contains('isolated projects violation')
    def properties = readProperties(dumpFile)
    properties.getProperty('sonar.projectKey') == 'isolated-kts-root'
    properties.getProperty(':module-a.sonar.projectName') == 'Kotlin module A'
    properties.getProperty(':module-a.sonar.moduleKey') == 'isolated-kts-root:module-a'
  }

  def 'root build script may keep declaring the plugin without a version next to the settings plugin'() {
    given:
    write('build.gradle', "plugins { id 'org.sonarqube' }\n" + ROOT_SONAR_BLOCK)
    def dumpFile = projectDir.resolve('both.properties')

    when:
    def result = runner('sonar', '--isolated-projects', dumpArgument(dumpFile)).build()

    then:
    sonarTasks(result) == [':sonar']
    readProperties(dumpFile).getProperty('sonar.modules').split(',').toList().toSet() == [':module-a', ':nested'].toSet()
  }

  def 'root-only application fails under isolation with instructions to apply the plugin in settings'() {
    given: 'a build configured as documented before Isolated Projects support, without sonar blocks in subprojects'
    write('settings.gradle', INCLUDES)
    write('build.gradle', "plugins { id 'org.sonarqube' }\n" + ROOT_SONAR_BLOCK)
    write('module-a/build.gradle', "plugins { id 'java' }\n")
    def dumpFile = projectDir.resolve('root-only.properties')

    when:
    def result = runner('sonar', '--isolated-projects', dumpArgument(dumpFile)).buildAndFail()

    then: 'no partial analysis is started'
    !Files.exists(dumpFile)
    result.task(':sonar') != null

    and: 'the console explains how to migrate, instead of a generic dependency resolution error'
    result.output.contains("the 'org.sonarqube' plugin is not applied to 3 project(s) of the build: :module-a, :nested, :nested:module-b.")
    containsMigrationInstructions(result.output)
    !result.output.contains('no variant with that configuration name exists')
  }

  def 'root-only application with a subproject sonar block fails under isolation with instructions'() {
    given: 'a build configured as documented before Isolated Projects support, module-a keeps its sonar block'
    write('settings.gradle', INCLUDES)
    write('build.gradle', "plugins { id 'org.sonarqube' }\n" + ROOT_SONAR_BLOCK)

    when:
    def result = runner('sonar', '--isolated-projects').buildAndFail()

    then: 'Gradle reports the failing build script, and the plugin explains the cause'
    result.output.contains('Could not find method sonar()')
    result.output.contains("A subproject build script uses the 'sonar { }' block, but the 'org.sonarqube' plugin is not applied to that subproject.")
    containsMigrationInstructions(result.output)
  }

  def 'root-only application with a Kotlin DSL subproject sonar block fails under isolation with instructions'() {
    given:
    write('settings.gradle', INCLUDES)
    write('build.gradle', "plugins { id 'org.sonarqube' }\n" + ROOT_SONAR_BLOCK)
    Files.delete(projectDir.resolve('module-a/build.gradle'))
    write('module-a/build.gradle.kts', """
      plugins { java }
      sonar { properties { property("sonar.projectName", "Kotlin module A") } }
    """)

    when:
    def result = runner('sonar', '--isolated-projects').buildAndFail()

    then:
    result.output.contains('Unresolved reference')
    result.output.contains("A subproject build script uses the 'sonar { }' block, but the 'org.sonarqube' plugin is not applied to that subproject.")
    containsMigrationInstructions(result.output)
  }

  def 'unrelated configuration failures under isolation do not print the migration instructions'() {
    given:
    write('module-a/build.gradle', "plugins { id 'java' }\nthrow new GradleException('unrelated failure')\n")

    when:
    def result = runner('sonar', '--isolated-projects').buildAndFail()

    then:
    result.output.contains('unrelated failure')
    !result.output.contains("is not applied to that subproject")
  }

  def 'root-only application still analyzes children without isolation'() {
    given:
    write('settings.gradle', INCLUDES)
    write('build.gradle', "plugins { id 'org.sonarqube' }\n" + ROOT_SONAR_BLOCK)
    def dumpFile = projectDir.resolve('root-only-legacy.properties')

    when:
    def result = runner('sonar', '--configuration-cache', dumpArgument(dumpFile)).build()

    then:
    result.task(':sonar').outcome == SUCCESS
    readProperties(dumpFile).getProperty('sonar.modules').split(',').toList().toSet() == [':module-a', ':nested'].toSet()
    readProperties(dumpFile).getProperty(':nested.sonar.modules') == ':nested:module-b'
  }

  def 'configuring a subproject from the root build script fails under isolation with instructions to move it'() {
    given:
    write('build.gradle', ROOT_SONAR_BLOCK + "project(':nested') { sonar { skipProject = true } }\n")

    when:
    def result = runner('sonar', '--isolated-projects').buildAndFail()

    then: 'Gradle reports the location, and the plugin explains where the block must go'
    result.output.contains("Build file 'build.gradle': line 2: Project ':' cannot access 'sonar' extension on another project ':nested'")
    result.output.contains("Move the 'sonar { }' configuration of project ':nested' from the root build script")
    result.output.contains("into the build script of project ':nested':")
  }

  def 'configuring all subprojects from the root build script fails under isolation with instructions to move it'() {
    given:
    write('build.gradle', ROOT_SONAR_BLOCK + "subprojects { sonar { properties { property 'sonar.exclusions', '**/gen/**' } } }\n")

    when:
    def result = runner('sonar', '--isolated-projects').buildAndFail()

    then:
    result.output.contains("Project ':' cannot access 'sonar' extension on subprojects")
    result.output.contains("written in 'subprojects { }' or 'allprojects { }' of the root build script")
    result.output.contains("With Gradle Isolated Projects, a build script can only configure the 'sonar { }' block of its own project.")
    result.output.contains('Configuration shared by many projects can go into a convention plugin (buildSrc or build-logic).')
  }

  def 'the same subproject configuration in the subproject build script works under isolation'() {
    given:
    write('nested/build.gradle', "sonar { skipProject = true }\n")
    def dumpFile = projectDir.resolve('moved.properties')

    when:
    runner('sonar', '--isolated-projects', dumpArgument(dumpFile)).build()

    then:
    readProperties(dumpFile).getProperty('sonar.modules') == ':module-a'
  }

  def 'skipped nested parent omits its descendants under isolation'() {
    given:
    write('nested/build.gradle', "sonar { skipProject = true }\n")
    def baselineFile = projectDir.resolve('skipped-baseline.properties')
    def isolatedFile = projectDir.resolve('skipped-isolated.properties')

    when:
    def baseline = runner('sonar', '--configuration-cache', dumpArgument(baselineFile)).build()
    def isolated = runner('sonar', '--isolated-projects', dumpArgument(isolatedFile)).build()

    then:
    baseline.task(':sonar').outcome == SUCCESS
    isolated.task(':sonar').outcome == SUCCESS
    comparableProperties(isolatedFile) == comparableProperties(baselineFile)
    readProperties(isolatedFile).getProperty('sonar.modules') == ':module-a'
    !readProperties(isolatedFile).stringPropertyNames().any { it.startsWith(':nested.') }
  }

  def 'isolated root sonar succeeds with diagnostics and runs after explicit producers'() {
    when:
    def result = runner(':module-a:compileJava', ':nested:module-b:compileJava', 'sonar',
      '--isolated-projects', '-Dorg.gradle.isolated-projects.diagnostics=true',
      dumpArgument(projectDir.resolve('diagnostics.properties'))).build()

    then:
    result.task(':sonar').outcome == SUCCESS
    !result.output.contains('isolated projects violation')
    taskIndex(result, ':module-a:compileJava') < taskIndex(result, ':sonar')
    taskIndex(result, ':nested:module-b:compileJava') < taskIndex(result, ':sonar')
    taskIndex(result, ':module-a:sonarResolver') < taskIndex(result, ':sonar')
    taskIndex(result, ':nested:module-b:sonarResolver') < taskIndex(result, ':sonar')
  }

  def 'global project key override also changes isolated module keys'() {
    given:
    def baselineFile = projectDir.resolve('override-baseline.properties')
    def isolatedFile = projectDir.resolve('override-isolated.properties')

    when:
    runner('sonar', '--configuration-cache', '-Dsonar.projectKey=command-line-root',
      dumpArgument(baselineFile)).build()
    runner('sonar', '--isolated-projects', '-Dsonar.projectKey=command-line-root',
      dumpArgument(isolatedFile)).build()

    then:
    comparableProperties(isolatedFile) == comparableProperties(baselineFile)
    readProperties(isolatedFile).getProperty(':module-a.sonar.moduleKey') == 'command-line-root:module-a'
    readProperties(isolatedFile).getProperty(':nested.:nested:module-b.sonar.moduleKey') == 'command-line-root:nested:module-b'
  }

  def 'deprecated sonarqube task uses the isolated metadata and resolver graph'() {
    given:
    def dumpFile = projectDir.resolve('deprecated-isolated.properties')

    when:
    def result = runner('sonarqube', '--isolated-projects', dumpArgument(dumpFile)).build()

    then:
    result.task(':sonarqube').outcome == SUCCESS
    readProperties(dumpFile).getProperty('sonar.modules').split(',').toList().toSet() == [':module-a', ':nested'].toSet()
    readProperties(dumpFile).getProperty('sonar.working.directory').replace('\\', '/').endsWith('/build/sonar')
    taskIndex(result, ':module-a:sonarResolver') < taskIndex(result, ':sonarqube')
    taskIndex(result, ':nested:module-b:sonarResolver') < taskIndex(result, ':sonarqube')
  }

  def 'credentials reach analysis without being written to project metadata'() {
    given:
    write('build.gradle', """
      sonar { properties {
        property 'sonar.projectKey', 'isolated-root'
        property 'sonar.login', 'dsl-secret-value'
      } }
    """)
    def dumpFile = projectDir.resolve('credentials.properties')

    when:
    def result = runner('sonar', '--isolated-projects',
      '-Dsonar.token=system-secret-value', dumpArgument(dumpFile)).build()

    then:
    result.task(':sonar').outcome == SUCCESS
    readProperties(dumpFile).getProperty('sonar.login') == 'dsl-secret-value'
    readProperties(dumpFile).getProperty('sonar.token') == 'system-secret-value'
    def metadataFiles = Files.walk(projectDir).withCloseable { stream ->
      stream.filter { it.fileName.toString() == 'properties.json' && it.parent.fileName.toString() == 'sonar-metadata' }.toList()
    }
    metadataFiles.size() == 4
    metadataFiles.every { path ->
      def contents = Files.readString(path)
      !contents.contains('dsl-secret-value') && !contents.contains('system-secret-value')
    }
  }

  def 'default project key sees group configured after plugin application'() {
    given:
    write('build.gradle', "group = 'configured.group'\n")
    def baselineFile = projectDir.resolve('group-baseline.properties')
    def isolatedFile = projectDir.resolve('group-isolated.properties')

    when:
    runner('sonar', '--configuration-cache', dumpArgument(baselineFile)).build()
    runner('sonar', '--isolated-projects', dumpArgument(isolatedFile)).build()

    then:
    readProperties(baselineFile).getProperty('sonar.projectKey') == 'configured.group:isolated-projects'
    readProperties(isolatedFile).getProperty('sonar.projectKey') == 'configured.group:isolated-projects'
  }

  private static boolean containsMigrationInstructions(String output) {
    output.contains('// settings.gradle.kts') &&
      output.contains('id("org.sonarqube") version "') &&
      output.contains('// settings.gradle\n') &&
      output.contains("id 'org.sonarqube' version '") &&
      output.contains("must move to the 'sonar { }' block of that subproject's build script")
  }

  private static List<String> sonarTasks(BuildResult result) {
    result.tasks*.path.findAll { it.endsWith(':sonar') || it.endsWith(':sonarqube') }
  }

  private static int taskIndex(BuildResult result, String path) {
    int index = result.tasks*.path.indexOf(path)
    assert index >= 0: "Task ${path} was absent from ${result.tasks*.path}"
    index
  }

  private static String dumpArgument(Path file) {
    '-Dsonar.scanner.internal.dumpToFile=' + file
  }

  private GradleRunner runner(String... arguments) {
    GradleRunner.create()
      .withGradleVersion('9.7.0')
      .withProjectDir(projectDir.toFile())
      .withPluginClasspath()
      .withEnvironment(System.getenv() + ['GRADLE_USER_HOME': projectDir.resolve('gradle-user-home').toString()])
      .withArguments(arguments)
  }

  private void write(String path, String content) {
    def file = projectDir.resolve(path)
    Files.createDirectories(file.parent)
    Files.writeString(file, content)
  }

  private static Properties readProperties(Path file) {
    def properties = new Properties()
    file.withInputStream { properties.load(it) }
    properties
  }

  private static Map<String, String> comparableProperties(Path file) {
    def properties = readProperties(file)
    properties.remove('sonar.scanner.internal.dumpToFile')
    properties.collectEntries { key, value -> [(key.toString()): value.toString()] }
  }
}
