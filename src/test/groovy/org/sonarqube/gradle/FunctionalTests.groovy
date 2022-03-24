/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2022 SonarSource
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

import org.gradle.internal.impldep.org.junit.Rule
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.StandardCopyOption

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class FunctionalTests extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
    File settingsFile
    File buildFile
    File outFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
        outFile = testProjectDir.newFile('out.properties')
        // For JaCoCo coverage, see https://github.com/koral--/jacoco-gradle-testkit-plugin
        InputStream is = FunctionalTests.class.getClassLoader().getResourceAsStream('testkit-gradle.properties')
        Files.copy(is, testProjectDir.newFile('gradle.properties').toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    def "no jdkHome, source and target for non 'java' projects"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'org.sonarqube'
        }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .forwardOutput()
                .withArguments('sonarqube', '-Dsonar.scanner.dumpToFile=' + outFile.absolutePath )
                .withPluginClasspath()
                .build()

        then:
        result.task(":sonarqube").outcome == SUCCESS
        def props = new Properties()
        props.load(outFile.newDataInputStream())
        !props.containsKey("sonar.java.jdkHome")
        !props.containsKey("sonar.java.source")
        !props.containsKey("sonar.java.target")
    }

    def "set jdkHome, source and target for 'java' projects from global toolchains"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        java {
          toolchain {
            languageVersion = JavaLanguageVersion.of(8)
          }
        }
        """

        when:
        def result = GradleRunner.create()
                .withGradleVersion("6.7.1")
                .withProjectDir(testProjectDir.root)
                .forwardOutput()
                .withArguments('sonarqube', '-Dsonar.scanner.dumpToFile=' + outFile.absolutePath )
                .withPluginClasspath()
                .build()

        then:
        result.task(":sonarqube").outcome == SUCCESS
        def props = new Properties()
        props.load(outFile.newDataInputStream())
        new File(props."sonar.java.jdkHome").exists()
        "${props."sonar.java.jdkHome"}${File.separator}bin${File.separator}java -version".execute()
                .err.text.contains("\"1.8.")
        props."sonar.java.source" == '8'
        props."sonar.java.target" == '8'
    }

    def "set jdkHome, source and target for 'java' projects from task toolchains"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        compileJava {
          javaCompiler = javaToolchains.compilerFor {
            languageVersion = JavaLanguageVersion.of(8)
          }
        }
        """

        when:
        def result = GradleRunner.create()
                .withGradleVersion("6.7.1")
                .withProjectDir(testProjectDir.root)
                .forwardOutput()
                .withArguments('sonarqube', '--info', '-Dsonar.scanner.dumpToFile=' + outFile.absolutePath )
                .withPluginClasspath()
                .build()

        then:
        result.output.contains('Heterogeneous compiler configuration has been detected. Using compiler configuration from task: \'compileJava\'')
        result.task(":sonarqube").outcome == SUCCESS
        def props = new Properties()
        props.load(outFile.newDataInputStream())
        new File(props."sonar.java.jdkHome").exists()
        "${props."sonar.java.jdkHome"}${File.separator}bin${File.separator}java -version".execute()
                .err.text.contains("\"1.8.")
        props."sonar.java.source" == '8'
        props."sonar.java.target" == '8'
    }

    // https://docs.gradle.org/6.6/release-notes.html#javacompile-release
    def "set jdkHome, source and target for 'java' projects from task release"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        
        compileJava {
          options.release = 8
        }
        """

        when:
        def result = GradleRunner.create()
                .withGradleVersion("6.6")
                .withProjectDir(testProjectDir.root)
                .forwardOutput()
                .withArguments('sonarqube', '-Dsonar.scanner.dumpToFile=' + outFile.absolutePath )
                .withPluginClasspath()
                .build()

        then:
        result.task(":sonarqube").outcome == SUCCESS
        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.java.source" == '8'
        props."sonar.java.target" == '8'
        // sonar.java.jdkHome will be the runtime JDK used to run Gradle, so we can't really assert its particular value
        // just check that it points to a valid path
        new File(props."sonar.java.jdkHome").exists()
    }
}
