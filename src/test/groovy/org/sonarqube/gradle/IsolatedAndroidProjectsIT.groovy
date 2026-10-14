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
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/** Exercises Android metadata and resolver aggregation with Gradle's project isolation enforcement. */
@Requires({ System.getenv('ANDROID_HOME') != null && System.getenv('JAVA_HOME') != null })
class IsolatedAndroidProjectsIT extends Specification {

  @TempDir
  Path projectDir

  def setup() {
    TestKitGradleProperties.configure(projectDir)
    write('settings.gradle', "rootProject.name = 'isolated-android'\ninclude 'app', 'library'\n")
    write('build.gradle', """
      ${androidAndSonarClasspath()}
      apply plugin: 'org.sonarqube'
      sonar { properties { property 'sonar.projectKey', 'isolated-android' } }
    """)
    write('app/build.gradle', """
      ${androidAndSonarClasspath()}
      apply plugin: 'com.android.application'
      apply plugin: 'org.sonarqube'
      repositories { google(); mavenCentral() }
      android {
        namespace 'org.example.app'
        compileSdk 36
        defaultConfig { applicationId 'org.example.app'; minSdk 23 }
      }
      dependencies { implementation project(':library') }
    """)
    write('library/build.gradle', """
      ${androidAndSonarClasspath()}
      apply plugin: 'com.android.library'
      apply plugin: 'org.sonarqube'
      repositories { google(); mavenCentral() }
      android {
        namespace 'org.example.library'
        compileSdk 36
        defaultConfig { minSdk 23 }
      }
    """)
    write('app/src/main/AndroidManifest.xml', '<manifest xmlns:android="http://schemas.android.com/apk/res/android" />')
    write('app/src/main/java/org/example/app/Main.java', 'package org.example.app; public class Main {}')
    write('app/src/test/java/org/example/app/MainTest.java', 'package org.example.app; public class MainTest {}')
    write('library/src/main/AndroidManifest.xml', '<manifest xmlns:android="http://schemas.android.com/apk/res/android" />')
    write('library/src/main/java/org/example/library/Library.java', 'package org.example.library; public class Library {}')
    write('library/src/test/java/org/example/library/LibraryTest.java', 'package org.example.library; public class LibraryTest {}')
  }

  def 'AGP 9 Android modules preserve scanner properties and reuse configuration cache under isolation'() {
    given:
    def baselineFile = projectDir.resolve('baseline.properties')
    def isolatedFile = projectDir.resolve('isolated.properties')

    when:
    def baseline = runner(':sonar', '--configuration-cache', dumpArgument(baselineFile)).build()
    def firstRun = runner(':sonar', '--isolated-projects', dumpArgument(isolatedFile)).build()
    def secondRun = runner(':sonar', '--isolated-projects', dumpArgument(isolatedFile)).build()

    then:
    baseline.task(':sonar').outcome == SUCCESS
    firstRun.task(':sonar').outcome == SUCCESS
    secondRun.task(':sonar').outcome == SUCCESS
    secondRun.output.contains('Reusing configuration cache')
    !firstRun.output.contains('isolated projects violation')
    comparableProperties(isolatedFile) == comparableProperties(baselineFile)

    and:
    def properties = readProperties(isolatedFile)
    properties.getProperty('sonar.modules').split(',').toList().toSet() == [':app', ':library'].toSet()
    [':app', ':library'].every { module ->
      properties.getProperty("${module}.sonar.android.detected") == 'true' &&
        properties.getProperty("${module}.sonar.sources").contains('src/main/java') &&
        properties.getProperty("${module}.sonar.tests").contains('src/test/java') &&
        properties.getProperty("${module}.sonar.java.libraries").contains('android.jar')
    }
    taskIndex(firstRun, ':app:sonarResolver') < taskIndex(firstRun, ':sonar')
    taskIndex(firstRun, ':library:sonarResolver') < taskIndex(firstRun, ':sonar')
  }

  def 'AGP 9 Android project resolves with isolation diagnostics enabled'() {
    when:
    def result = runner(':app:compileDebugJavaWithJavac', ':library:compileDebugJavaWithJavac', ':sonar',
      '--isolated-projects', '-Dorg.gradle.isolated-projects.diagnostics=true',
      dumpArgument(projectDir.resolve('diagnostics.properties'))).build()

    then:
    result.task(':sonar').outcome == SUCCESS
    !result.output.contains('isolated projects violation')
    taskIndex(result, ':app:compileDebugJavaWithJavac') < taskIndex(result, ':sonar')
    taskIndex(result, ':library:compileDebugJavaWithJavac') < taskIndex(result, ':sonar')
    taskIndex(result, ':app:sonarResolver') < taskIndex(result, ':sonar')
    taskIndex(result, ':library:sonarResolver') < taskIndex(result, ':sonar')
  }

  private String androidAndSonarClasspath() {
    """
      buildscript {
        repositories { google(); mavenCentral() }
        dependencies {
          classpath files(System.getProperty('sonarPluginClasspath').split(File.pathSeparator))
          classpath 'com.android.tools.build:gradle:9.2.1'
        }
      }
    """
  }

  private GradleRunner runner(String... arguments) {
    GradleRunner.create()
      .withGradleVersion('9.7.0')
      .withProjectDir(projectDir.toFile())
      .withEnvironment(System.getenv() + ['GRADLE_USER_HOME': projectDir.resolve('gradle-user-home').toString()])
      .withArguments(arguments + ['-DsonarPluginClasspath=' + pluginClasspath(), '--stacktrace'])
  }

  private String pluginClasspath() {
    getClass().classLoader.getResource('plugin-under-test-metadata.properties').withInputStream { stream ->
      def properties = new Properties()
      properties.load(stream)
      properties.getProperty('implementation-classpath')
    }
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
    properties.collectEntries { key, value -> [(key.toString()): value.toString()] }
  }
}
