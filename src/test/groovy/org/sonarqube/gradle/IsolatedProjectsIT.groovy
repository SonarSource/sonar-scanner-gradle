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

/** Exercises the real scanner under Gradle's project isolation enforcement. */
class IsolatedProjectsIT extends Specification {

  @TempDir
  Path projectDir

  def setup() {
    TestKitGradleProperties.configure(projectDir)
    write('settings.gradle', "rootProject.name = 'isolated-projects'\ninclude 'module-a', 'nested:module-b'\n")
    write('build.gradle', """
      plugins { id 'org.sonarqube' }
      sonar { properties { property 'sonar.projectKey', 'isolated-root' } }
    """)
    write('module-a/build.gradle', """
      plugins { id 'java'; id 'org.sonarqube' }
      sonar { properties { property 'sonar.projectName', 'Named module A' } }
    """)
    write('nested/build.gradle', "plugins { id 'org.sonarqube' }\n")
    write('nested/module-b/build.gradle', "plugins { id 'java'; id 'org.sonarqube' }\n")
    write('module-a/src/main/java/A.java', 'class A {}')
    write('module-a/src/test/java/ATest.java', 'class ATest {}')
    write('nested/module-b/src/main/java/B.java', 'class B {}')
    write('nested/module-b/src/test/java/BTest.java', 'class BTest {}')
  }

  def 'isolated root sonar preserves nested scanner properties and reuses the configuration cache'() {
    given:
    def baselineFile = projectDir.resolve('baseline.properties')
    def isolatedFile = projectDir.resolve('isolated.properties')

    when:
    def baseline = runner(':module-a:classes', ':nested:module-b:classes', ':sonar',
      '--configuration-cache', dumpArgument(baselineFile)).build()
    def firstRun = runner(':module-a:classes', ':nested:module-b:classes', ':sonar',
      '--isolated-projects', dumpArgument(isolatedFile)).build()
    def secondRun = runner(':module-a:classes', ':nested:module-b:classes', ':sonar',
      '--isolated-projects', dumpArgument(isolatedFile)).build()

    then:
    baseline.task(':sonar').outcome == SUCCESS
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
    properties.getProperty(':nested.:nested:module-b.sonar.tests').replace('\\', '/').contains('src/test/java')
    properties.getProperty(':nested.:nested:module-b.sonar.java.binaries') != null
  }

  def 'isolated root sonar succeeds with diagnostics and runs after explicit producers'() {
    when:
    def result = runner(':module-a:compileJava', ':nested:module-b:compileJava', ':sonar',
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
    runner(':sonar', '--configuration-cache', '-Dsonar.projectKey=command-line-root',
      dumpArgument(baselineFile)).build()
    runner(':sonar', '--isolated-projects', '-Dsonar.projectKey=command-line-root',
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
    def result = runner(':sonarqube', '--isolated-projects', dumpArgument(dumpFile)).build()

    then:
    result.task(':sonarqube').outcome == SUCCESS
    readProperties(dumpFile).getProperty('sonar.modules').split(',').toList().toSet() == [':module-a', ':nested'].toSet()
    readProperties(dumpFile).getProperty('sonar.working.directory').replace('\\', '/').endsWith('/build/sonar')
    taskIndex(result, ':module-a:sonarResolver') < taskIndex(result, ':sonarqube')
    taskIndex(result, ':nested:module-b:sonarResolver') < taskIndex(result, ':sonarqube')
  }

  def 'direct nested sonar preserves its descendant properties under isolation'() {
    given:
    // Legacy root application registers only its own analysis task. The isolated
    // aggregation also needs root metadata, so restore root application after baseline.
    write('build.gradle', '')
    def baselineFile = projectDir.resolve('nested-baseline.properties')
    def isolatedFile = projectDir.resolve('nested-isolated.properties')

    when:
    def baseline = runner(':nested:module-b:classes', ':nested:sonar',
      '--configuration-cache', dumpArgument(baselineFile)).build()
    write('build.gradle', "plugins { id 'org.sonarqube' }\n")
    def isolated = runner(':nested:module-b:classes', ':nested:sonar',
      '--isolated-projects', dumpArgument(isolatedFile)).build()

    then:
    baseline.task(':nested:sonar').outcome == SUCCESS
    isolated.task(':nested:sonar').outcome == SUCCESS
    comparableProperties(isolatedFile) == comparableProperties(baselineFile)
    readProperties(isolatedFile).getProperty('sonar.modules') == ':nested:module-b'
    readProperties(isolatedFile).getProperty(':nested:module-b.sonar.java.binaries') != null
  }

  def 'root-only isolated build fails on missing child metadata rather than silently omitting children'() {
    given:
    write('module-a/build.gradle', "plugins { id 'java' }\n")
    write('nested/build.gradle', '')
    write('nested/module-b/build.gradle', "plugins { id 'java' }\n")

    when:
    def result = runner(':sonar', '--isolated-projects',
      dumpArgument(projectDir.resolve('root-only.properties'))).buildAndFail()

    then:
    result.output.contains('sonarResolverElements')
    result.output.contains(':module-a')
    result.output.contains('no variant with that configuration name exists')
  }

  def 'root-only plugin application still analyzes children without isolation'() {
    given:
    write('module-a/build.gradle', "plugins { id 'java' }\n")
    write('nested/build.gradle', '')
    write('nested/module-b/build.gradle', "plugins { id 'java' }\n")
    def dumpFile = projectDir.resolve('root-only-legacy.properties')

    when:
    def result = runner(':sonar', '--configuration-cache', dumpArgument(dumpFile)).build()

    then:
    result.task(':sonar').outcome == SUCCESS
    readProperties(dumpFile).getProperty('sonar.modules').split(',').toList().toSet() == [':module-a', ':nested'].toSet()
    readProperties(dumpFile).getProperty(':nested.sonar.modules') == ':nested:module-b'
  }

  def 'skipped nested parent omits its descendants under isolation'() {
    given:
    write('nested/build.gradle', "plugins { id 'org.sonarqube' }\nsonar { skipProject = true }\n")
    def baselineFile = projectDir.resolve('skipped-baseline.properties')
    def isolatedFile = projectDir.resolve('skipped-isolated.properties')

    when:
    def baseline = runner(':sonar', '--configuration-cache', dumpArgument(baselineFile)).build()
    def isolated = runner(':sonar', '--isolated-projects', dumpArgument(isolatedFile)).build()

    then:
    baseline.task(':sonar').outcome == SUCCESS
    isolated.task(':sonar').outcome == SUCCESS
    comparableProperties(isolatedFile) == comparableProperties(baselineFile)
    readProperties(isolatedFile).getProperty('sonar.modules') == ':module-a'
    !readProperties(isolatedFile).stringPropertyNames().any { it.startsWith(':nested.') }
  }

  def 'credentials reach analysis without being written to project metadata'() {
    given:
    write('build.gradle', """
      plugins { id 'org.sonarqube' }
      sonar { properties {
        property 'sonar.projectKey', 'isolated-root'
        property 'sonar.login', 'dsl-secret-value'
      } }
    """)
    def dumpFile = projectDir.resolve('credentials.properties')

    when:
    def result = runner(':sonar', '--isolated-projects',
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
    write('build.gradle', "plugins { id 'org.sonarqube' }\ngroup = 'configured.group'\n")
    def baselineFile = projectDir.resolve('group-baseline.properties')
    def isolatedFile = projectDir.resolve('group-isolated.properties')

    when:
    runner(':sonar', '--configuration-cache', dumpArgument(baselineFile)).build()
    runner(':sonar', '--isolated-projects', dumpArgument(isolatedFile)).build()

    then:
    readProperties(baselineFile).getProperty('sonar.projectKey') == 'configured.group:isolated-projects'
    readProperties(isolatedFile).getProperty('sonar.projectKey') == 'configured.group:isolated-projects'
  }

  private static int taskIndex(BuildResult result, String path) {
    int index = result.tasks*.path.indexOf(path)
    assert index >= 0: "Task ${path} was absent from ${result.tasks*.path}"
    index
  }

  private String dumpArgument(Path file) {
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
