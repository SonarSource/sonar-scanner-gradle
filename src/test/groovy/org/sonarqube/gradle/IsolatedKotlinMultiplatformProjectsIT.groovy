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

/** Checks Kotlin Multiplatform metadata and a JVM project dependency under project isolation. */
class IsolatedKotlinMultiplatformProjectsIT extends Specification {

  @TempDir
  Path projectDir

  def setup() {
    TestKitGradleProperties.configure(projectDir)
    write('settings.gradle', """
      pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
      rootProject.name = 'isolated-kmp'
      include 'consumer', 'producer'
    """)
    write('build.gradle', """
      plugins {
        id 'org.sonarqube'
        id 'org.jetbrains.kotlin.multiplatform' version '2.4.20' apply false
      }
      sonar { properties { property 'sonar.projectKey', 'isolated-kmp' } }
    """)
    write('producer/build.gradle', """
      plugins {
        id 'org.jetbrains.kotlin.multiplatform'
        id 'org.sonarqube'
      }
      repositories { mavenCentral() }
      kotlin { jvm() }
    """)
    write('consumer/build.gradle', """
      plugins { id 'java-library'; id 'org.sonarqube' }
      repositories { mavenCentral() }
      dependencies { implementation project(':producer') }
    """)
    write('producer/src/commonMain/kotlin/example/Common.kt', 'package example\nclass Common')
    write('producer/src/jvmMain/kotlin/example/Producer.kt', 'package example\nclass Producer')
    write('producer/src/jvmTest/kotlin/example/ProducerTest.kt', 'package example\nclass ProducerTest')
    write('consumer/src/main/java/example/Consumer.java', 'package example; public class Consumer {}')
  }

  def 'KMP JVM source metadata matches legacy mode and configuration cache is reused'() {
    given:
    def baselineFile = projectDir.resolve('baseline.properties')
    def isolatedFile = projectDir.resolve('isolated.properties')

    when:
    def baseline = runner(':sonar', ':consumer:classes', ':producer:jvmJar', '--configuration-cache', dumpArgument(baselineFile)).build()
    def firstRun = runner(':sonar', ':consumer:classes', ':producer:jvmJar', '--isolated-projects',
      '-Dorg.gradle.isolated-projects.diagnostics=true', dumpArgument(isolatedFile)).build()
    def secondRun = runner(':sonar', ':consumer:classes', ':producer:jvmJar', '--isolated-projects',
      '-Dorg.gradle.isolated-projects.diagnostics=true', dumpArgument(isolatedFile)).build()

    then:
    baseline.task(':sonar').outcome == SUCCESS
    firstRun.task(':sonar').outcome == SUCCESS
    secondRun.task(':sonar').outcome == SUCCESS
    secondRun.output.contains('Reusing configuration cache')
    !firstRun.output.contains('isolated projects violation')
    comparableProperties(isolatedFile) == comparableProperties(baselineFile)

    and:
    def properties = readProperties(isolatedFile)
    properties.getProperty(':producer.sonar.sources').replace('\\', '/').contains('src/commonMain/kotlin')
    properties.getProperty(':producer.sonar.sources').replace('\\', '/').contains('src/jvmMain/kotlin')
    properties.getProperty(':producer.sonar.tests').replace('\\', '/').contains('src/jvmTest/kotlin')
    taskIndex(firstRun, ':producer:jvmJar') < taskIndex(firstRun, ':sonar')
    taskIndex(firstRun, ':producer:jvmJar') < taskIndex(firstRun, ':consumer:sonarResolver')
    taskIndex(firstRun, ':consumer:classes') < taskIndex(firstRun, ':sonar')
    taskIndex(firstRun, ':consumer:sonarResolver') < taskIndex(firstRun, ':sonar')
  }

  def 'KMP JVM artifact runs before isolated analysis when selected separately'() {
    when:
    def result = runner(':sonar', ':producer:jvmJar', '--isolated-projects',
      dumpArgument(projectDir.resolve('producer-order.properties'))).build()

    then:
    result.task(':sonar').outcome == SUCCESS
    taskIndex(result, ':producer:jvmJar') < taskIndex(result, ':sonar')
  }

  def 'KMP JVM artifact is not compiled for analysis alone'() {
    when:
    def result = runner(':sonar', '--isolated-projects',
      dumpArgument(projectDir.resolve('analysis-only.properties'))).build()

    then:
    result.task(':sonar').outcome == SUCCESS
    result.task(':producer:jvmJar') == null
  }

  private GradleRunner runner(String... arguments) {
    GradleRunner.create()
      .withGradleVersion('9.7.0')
      .withProjectDir(projectDir.toFile())
      .withEnvironment(System.getenv() + ['GRADLE_USER_HOME': projectDir.resolve('gradle-user-home').toString()])
      .withArguments(arguments + ['--stacktrace'])
      .withPluginClasspath()
  }

  private static int taskIndex(BuildResult result, String path) {
    int index = result.tasks*.path.indexOf(path)
    assert index >= 0: "Task ${path} was absent from ${result.tasks*.path}"
    index
  }

  private static String dumpArgument(Path file) {
    '-Dsonar.scanner.internal.dumpToFile=' + file
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
    properties.collectEntries { key, value ->
      def normalized = key.toString().endsWith('sonar.modules') ? value.toString().split(',').sort().join(',') : value.toString()
      [(key.toString()): normalized]
    }
  }
}
