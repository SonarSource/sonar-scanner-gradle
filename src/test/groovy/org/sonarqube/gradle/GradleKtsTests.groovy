/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2024 SonarSource
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

import org.assertj.core.api.Assertions
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class GradleKtsTests extends Specification {
    @TempDir
    Path testProjectDir
    Path settingsFile
    Path buildFile
    Path outFile

    def setup() {
        outFile = testProjectDir.resolve('out.properties')
        // For JaCoCo coverage, see https://github.com/koral--/jacoco-gradle-testkit-plugin
        InputStream is = GradleKtsTests.class.getClassLoader().getResourceAsStream('testkit-gradle.properties')
        Files.copy(is, testProjectDir.resolve('gradle.properties'), StandardCopyOption.REPLACE_EXISTING)
    }

    def "add build and settings gradle files to sources when both are in kotlin dsl"() {
        given:
        addBuildKts()
        addSettingsKts()

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonarqube', '--info', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()

        def props = new Properties()
        props.load(outFile.newDataInputStream())

        then:
        def sonarSources = props["sonar.sources"].split(",")
        Assertions.assertThat(sonarSources)
                .containsExactlyInAnyOrder(settingsFile.toRealPath().toString(), buildFile.toRealPath().toString())

    }

    def "add only build and file to sources when only build is in kotlin dsl"() {
        given:
        addBuildKts()
        addSettings()

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonarqube', '--info', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()

        def props = new Properties()
        props.load(outFile.newDataInputStream())

        then:
        props["sonar.sources"] == buildFile.toRealPath().toString()
        props["sonar.tests"] == ""
    }

    def "add only settings file to sources when only settings file is in kotlin dsl"() {
        given:
        addBuild()
        addSettingsKts()

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonarqube', '--info', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()

        def props = new Properties()
        props.load(outFile.newDataInputStream())

        then:
        props["sonar.sources"] == settingsFile.toRealPath().toString()
        props["sonar.tests"] == ""
    }

    def "add only build file to sources when no settings found"() {
        given:
        addBuildKts()

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonarqube', '--info', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()

        def props = new Properties()
        props.load(outFile.newDataInputStream())

        then:
        props["sonar.sources"] == buildFile.toRealPath().toString()
        props["sonar.tests"] == ""
    }

    def "add nothing to sources when Groovy dsl is used"() {
        given:
        addBuild()
        addSettings()

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonarqube', '--info', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()

        def props = new Properties()
        props.load(outFile.newDataInputStream())

        then:
        props["sonar.sources"] == ""
        props["sonar.tests"] == ""
    }

    def "add nothing to sources when Groovy dsl is used and no settings"() {
        given:
        addBuild()

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonarqube', '--info', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()

        def props = new Properties()
        props.load(outFile.newDataInputStream())

        then:
        props["sonar.sources"] == ""
        props["sonar.tests"] == ""
    }

    def "add .gradle.kts files to sources only once"() {
        given:
        addBuildKts()
        addSettingsKts()
        Path subProjectBuildFile = addSubProject()

        when:
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonarqube', '--info', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()

        def props = new Properties()
        props.load(outFile.newDataInputStream())

        then:
        def subProjectSonarSources = props[":subproject.sonar.sources"]
        Assertions.assertThat(subProjectSonarSources).isEmpty()

        def sonarSources = props["sonar.sources"].split(",")
        Assertions.assertThat(sonarSources)
            .containsExactlyInAnyOrder(
              settingsFile.toRealPath().toString(),
              buildFile.toRealPath().toString(),
              subProjectBuildFile.toRealPath().toString()
            )

    }

    def "don't add .gradle.kts files if sources are overridden"() {
        given:
        addBuildKtsWithCustomSources()
        addSettingsKts()

        when:
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonarqube', '--info', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()

        def props = new Properties()
        props.load(outFile.newDataInputStream())

        then:
        props["sonar.sources"] == "src/main/custom"

    }

    private def addSettings() {
        settingsFile = testProjectDir.resolve('settings.gradle')
        settingsFile << "rootProject.name = 'java-task-toolchains'"
    }

    private def addSettingsKts() {
        settingsFile = testProjectDir.resolve('settings.gradle.kts')
        settingsFile << """rootProject.name = "java-task-toolchains"
                        """
    }

    private def addBuild() {
        buildFile = testProjectDir.resolve('build.gradle')
        buildFile << """
                     plugins {
                         id 'java'
                         id 'org.sonarqube'
                     }
                     """
    }

    private def addBuildKts() {
        buildFile = testProjectDir.resolve('build.gradle.kts')
        buildFile << """
                     plugins {
                         java
                         id("org.sonarqube")
                     }
                     """
    }

    private def addBuildKtsWithCustomSources() {
        buildFile = testProjectDir.resolve('build.gradle.kts')
        buildFile << """
                     plugins {
                         java
                         id("org.sonarqube")
                     }
                     
                     sonar {
                         properties {
                             property("sonar.sources", "src/main/custom")
                         }
                     }
                     """
    }

    private def addSubProject() {
        settingsFile << """
                        include("subproject")
                        """
        Path subProject = testProjectDir.resolve('subproject')
        Files.createDirectory(subProject)
        Path subProjectBuildFile = subProject.resolve('build.gradle.kts')
        subProjectBuildFile << """
                     plugins {
                         java
                     }
                     """

        return subProjectBuildFile
    }
}

