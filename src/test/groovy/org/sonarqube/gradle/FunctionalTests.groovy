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


import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class FunctionalTests extends Specification {
    String gradleVersion = "7.6.2"

    @TempDir
    Path testProjectDir
    Path settingsFile
    Path buildFile
    Path outFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle')
        buildFile = testProjectDir.resolve('build.gradle')
        outFile = testProjectDir.resolve('out.properties')
        // For JaCoCo coverage, see https://github.com/koral--/jacoco-gradle-testkit-plugin
        InputStream is = FunctionalTests.class.getClassLoader().getResourceAsStream('testkit-gradle.properties')
        Files.copy(is, testProjectDir.resolve('gradle.properties'), StandardCopyOption.REPLACE_EXISTING)
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
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonarqube', '-Dsonar.scanner.dumpToFile=' + outFile.toAbsolutePath())
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
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonarqube', '-Dsonar.scanner.dumpToFile=' + outFile.toAbsolutePath())
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

    def "set java release version"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        
        compileJava {
          options.release = 10
        }
        """

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonar', '-Dsonar.scanner.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()

        then:
        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.java.source" == '10'
        props."sonar.java.target" == '10'
    }

    def "set java release version with compiler arguments"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        
        compileJava {
          options.compilerArgs.addAll(['--release', '10'])
        }
        """

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonar', '-Dsonar.scanner.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()

        then:
        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.java.source" == '10'
        props."sonar.java.target" == '10'
    }

    def "warn if using deprecated sonarqube task"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
            plugins {
                id 'org.sonarqube'
            }
            """

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonarqube', '-Dsonar.scanner.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()

        then:
        result.task(":sonarqube").outcome == SUCCESS
        result.output.contains("Task 'sonarqube' is deprecated. Use 'sonar' instead.")
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
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonarqube', '--info', '-Dsonar.scanner.dumpToFile=' + outFile.toAbsolutePath())
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
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonarqube', '-Dsonar.scanner.dumpToFile=' + outFile.toAbsolutePath())
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
        props."sonar.java.enablePreview" == "false"
    }

    def "enable preview without JDK toolchain"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        
        compileJava {
          options.compilerArgs.addAll("--enable-preview")
        }
        """

        when:
        def result = GradleRunner.create()
          .withGradleVersion(gradleVersion)
          .withProjectDir(testProjectDir.toFile())
          .forwardOutput()
          .withArguments('sonarqube', '-Dsonar.scanner.dumpToFile=' + outFile.toAbsolutePath())
          .withPluginClasspath()
          .build()

        then:
        result.task(":sonarqube").outcome == SUCCESS
        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.java.enablePreview" == "true"
    }

    def "enable preview with JDK toolchain"() {
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
          options.compilerArgs.addAll("--enable-preview")
        }
        """

        when:
        def result = GradleRunner.create()
          .withGradleVersion(gradleVersion)
          .withProjectDir(testProjectDir.toFile())
          .forwardOutput()
          .withArguments('sonarqube', '--info', '-Dsonar.scanner.dumpToFile=' + outFile.toAbsolutePath())
          .withPluginClasspath()
          .build()

        then:
        result.task(":sonarqube").outcome == SUCCESS
        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.java.enablePreview" == "true"
    }

    def "warn if using implicit compilation"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
            plugins {
                id 'org.sonarqube'
            }
            """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonar', '-Dsonar.scanner.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()
        then:
        result.task(":sonar").outcome == SUCCESS

        result.output.contains("The 'sonar' task depends on compile tasks. This behavior is now deprecated and will be removed in version 5.x. To avoid implicit compilation, set property 'sonar.gradle.skipCompile' to 'true' and make sure your project is compiled, before analysis has started.")
    }

    def "do not warn if implicit compilation is disabled"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
            plugins {
                id 'org.sonarqube'
            }
            """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonar', '-Dsonar.gradle.skipCompile=true', '-Dsonar.scanner.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()

        then:
        result.task(":sonar").outcome == SUCCESS
        !result.output.contains("The 'sonar' task depends on compile tasks. This behavior is now deprecated and will be removed in version 5.x. To avoid implicit compilation, set property 'sonar.gradle.skipCompile' to 'true' and make sure your project is compiled, before analysis has started.")
    }

    def "warn if `sonar.gradle.skipCompile` is set to false"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
            plugins {
                id 'org.sonarqube'
            }
            """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .forwardOutput()
                .withArguments('sonar', '-Dsonar.gradle.skipCompile=false', '-Dsonar.scanner.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .build()

        then:
        result.task(":sonar").outcome == SUCCESS

        result.output.contains("The 'sonar' task depends on compile tasks. This behavior is now deprecated and will be removed in version 5.x. To avoid implicit compilation, set property 'sonar.gradle.skipCompile' to 'true' and make sure your project is compiled, before analysis has started.")
    }
}
