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

import groovy.json.JsonSlurper
import org.gradle.internal.impldep.org.junit.Assume
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Requires
import spock.lang.Specification

import java.nio.file.Path
import spock.lang.TempDir

/**
 * Integration tests for AndroidUtils. Uses gradle testkit to run tests against real Gradle builds.
 */
class AndroidUtilsIT extends Specification {
    @TempDir
    Path projectDir
    Path settingsFile
    Path buildFile
    Path outFile
    Path manifestFile

    def setup() {
        settingsFile = projectDir.resolve('settings.gradle')
        buildFile = projectDir.resolve('build.gradle')
        outFile = projectDir.resolve('out.properties')
        def manifestDir = projectDir.resolve('src').resolve('main')
        manifestDir.toFile().mkdirs()
        manifestFile = manifestDir.resolve('AndroidManifest.xml')
        FunctionalTests.configureJacocoGradleTestkitPlugin(projectDir)
    }

    /**
     * Gets the combined classpath including both the plugin under test and Android Gradle Plugin.
     * This is needed because Android classes must be visible to the SonarQube plugin during the test.
     */
    private List<File> getPluginClasspathWithAndroid() {
        def pluginClasspath = getClass().classLoader.getResource("plugin-under-test-metadata.properties")
            .withInputStream { stream ->
                def props = new Properties()
                props.load(stream)
                props.getProperty("implementation-classpath")
                    .split(File.pathSeparator)
                    .collect { new File(it) }
            }

        // Add ALL jars from the test classpath to ensure Android plugin has all its dependencies
        def testClasspath = System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .collect { new File(it) }
            .findAll { file ->
                // Only include jar files and class directories (exclude gradle wrapper, etc.)
                file.name.endsWith(".jar") || file.isDirectory()
            }

        return (pluginClasspath + testClasspath).unique()
    }

    @Requires({ System.getenv("JAVA_HOME") != null && System.getenv("ANDROID_HOME") != null })
    def "Libraries of android project are correctly retrieved"() {
        given: "a simple android project"
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        manifestFile << """<?xml version="1.0" encoding="utf-8"?>
          <manifest xmlns:android="http://schemas.android.com/apk/res/android">
          </manifest>
        """

        buildFile << """

        buildscript {
            repositories {
                mavenCentral()
                mavenLocal()
                google()
            }
            dependencies {
                classpath "com.android.tools.build:gradle:8.1.1"
            }
        }
        plugins {
            id 'org.sonarqube'
        }
        apply plugin: 'com.android.application'

        repositories {
            mavenCentral()
            mavenLocal()
            google()
        }
        dependencies {
            implementation 'joda-time:joda-time:2.7'
            testImplementation 'junit:junit:4.12'
        }
        android {
            compileSdkVersion 30
            namespace "org.hello"
        }
        """

        when: "run sonarResolver task"
        def result = GradleRunner.create()
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('--configuration-cache', '--stacktrace', ':sonarResolver')
          .withPluginClasspath(getPluginClasspathWithAndroid())
          .build()

        then: "sonarResolver task is successful"
        result.task(":sonarResolver").outcome == TaskOutcome.SUCCESS

        when: "Read properties from where sonarResolver actually writes them (JSON format)"
        def propertiesFile = projectDir.resolve('build').resolve('sonar-resolver').resolve('properties').toFile()
        def json = new JsonSlurper().parse(propertiesFile)

        then: "properties file exists and contains expected values"
        propertiesFile.exists()
        json.projectName == ":"
        json.mainLibraries != null
        json.mainLibraries.any { it.contains("android.jar") }
        json.mainLibraries.any { it.contains("joda-time-2.7") }
        json.mainLibraries.every { !it.contains("junit-4.12") }

        json.testLibraries != null
        json.testLibraries.any { it.contains("android.jar") }
        json.testLibraries.any { it.contains("joda-time-2.7") }
        json.testLibraries.any { it.contains("junit-4.12") }

        when: "Run sonar task"
        def sonarResult = GradleRunner.create()
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('--configuration-cache', '--stacktrace', 'sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
          .withPluginClasspath(getPluginClasspathWithAndroid())
          .build()

        then: "sonar task is successful"
        sonarResult.task(":sonar").outcome == TaskOutcome.SUCCESS

        then: "output file contains expected values"
        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.java.libraries".contains("android.jar")
        props."sonar.java.libraries".contains("joda-time-2.7")
        !props."sonar.java.libraries".contains("junit-4.12")

        props."sonar.java.test.libraries".contains("android.jar")
        props."sonar.java.test.libraries".contains("joda-time-2.7")
        props."sonar.java.test.libraries".contains("junit-4.12")
    }
}
