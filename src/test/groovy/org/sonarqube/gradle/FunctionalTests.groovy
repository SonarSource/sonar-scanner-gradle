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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import static java.util.Objects.nonNull
import static org.assertj.core.api.Assertions.assertThat
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class FunctionalTests extends Specification {
    String gradleVersion = "7.6.2"

    @TempDir
    Path projectDir
    Path settingsFile
    Path buildFile
    Path outFile

    def setup() {
        settingsFile = projectDir.resolve('settings.gradle')
        buildFile = projectDir.resolve('build.gradle')
        projectDir.resolve('integrationTests').toFile().mkdir()
        projectDir.resolve('integrationTests').resolve("run-all.sh") << "# a test script file"
        projectDir.resolve('test-license.sh') << "# a test script file"
        outFile = projectDir.resolve('out.properties')
        // For JaCoCo coverage, see https://github.com/koral--/jacoco-gradle-testkit-plugin
        InputStream is = FunctionalTests.class.getClassLoader().getResourceAsStream('testkit-gradle.properties')
        Files.copy(is, projectDir.resolve('gradle.properties'), StandardCopyOption.REPLACE_EXISTING)
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
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('sonarqube', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
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
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('sonarqube', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
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
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
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
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
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
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('sonarqube', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
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
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('sonarqube', '--info', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
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

    def "log execution context"() {
        given:
        settingsFile << "rootProject.name = 'java-task-output-logs'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        """

        when:
        def result = GradleRunner.create()
          .withGradleVersion(gradleVersion)
          .withProjectDir(projectDir.toFile())
          .withEnvironment(Map.of("GRADLE_OPTS", "-Dfoo=bar"))
          .forwardOutput()
          .withArguments('sonarqube', '--info', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
          .withPluginClasspath()
          .build()

        then:
        result.task(":sonarqube").outcome == SUCCESS
        nonNull(
          assertThat(result.output)
            .containsPattern('org.sonarqube Gradle plugin \\d+\\.\\d+')
            .containsPattern('Java \\d+')
            .contains('(64-bit)')
            .contains('GRADLE_OPTS=-Dfoo=bar')
        )
    }

    def "log execution context even when sonar.skip is true"() {
        given:
        settingsFile << "rootProject.name = 'java-task-output-logs'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        """

        when:
        def result = GradleRunner.create()
          .withGradleVersion(gradleVersion)
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('sonarqube', '--info', '-Dsonar.skip=true')
          .withPluginClasspath()
          .build()

        then:
        result.task(":sonarqube").outcome == SUCCESS
        nonNull(
          assertThat(result.output)
            .contains('org.sonarqube Gradle plugin')
        )
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
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('sonarqube', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
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
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('sonarqube', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
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
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('sonarqube', '--info', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
          .withPluginClasspath()
          .build()

        then:
        result.task(":sonarqube").outcome == SUCCESS
        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.java.enablePreview" == "true"
    }

    def "don't crash if compiler arg isn't String"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        
        compileJava {
          options.compilerArgs = [
            file("/")
          ]
        }
        """

        when:
        GradleRunner.create()
          .withGradleVersion(gradleVersion)
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('sonarqube', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
          .withPluginClasspath()
          .build()


        then:
        noExceptionThrown()
    }

    def "scan all is enabled"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        """

        when:
        def result = GradleRunner.create()
          .withGradleVersion(gradleVersion)
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('sonar', '--info',
            '-Dsonar.gradle.scanAll=true',
            '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
          .withPluginClasspath()
          .build()

        then:
        result.task(":sonar").outcome == SUCCESS

        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.gradle.scanAll" == "true"
        result.output.contains("Parameter sonar.gradle.scanAll is enabled. The scanner will attempt to collect additional sources.")

        var mainSources = ((String) props."sonar.sources").split(",")
        mainSources.size() == 3
        var projectPath = projectDir.toFile().getCanonicalPath() + File.separator
        mainSources[0].endsWith("""${projectPath}build.gradle""")
        mainSources[1].endsWith("""${projectPath}gradle.properties""")
        mainSources[2].endsWith("""${projectPath}settings.gradle""")

        var testSources = ((String) props."sonar.tests").split(",")
        testSources.size() == 2
        testSources[0].endsWith("""${projectPath}integrationTests${File.separator}run-all.sh""")
        testSources[1].endsWith("""${projectPath}test-license.sh""")
    }

    def "scan all is enabled but not applied because of overridden properties on the command line"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        """

        when:
        var arguments = ['sonar', '--info',
                         '-Dsonar.gradle.scanAll=true',
                         sonarSourcesOverride != null ? '-Dsonar.sources=' + sonarSourcesOverride : null,
                         sonarTestsOverride != null ? '-Dsonar.tests=' + sonarTestsOverride : null,
                         '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath()]
        def result = GradleRunner.create()
          .withGradleVersion(gradleVersion)
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments(arguments.stream().filter { it != null }.toList())
          .withPluginClasspath()
          .build()

        then:
        result.task(":sonar").outcome == SUCCESS

        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.gradle.scanAll" == "true"
        result.output.contains("Parameter sonar.gradle.scanAll is enabled. The scanner will attempt to collect additional sources.")
        result.output.contains("Parameter sonar.gradle.scanAll is enabled but the scanner will not collect additional sources because sonar.sources or sonar.tests has been overridden.")

        where:
        sonarSourcesOverride | sonarTestsOverride
        "src"                | null
        null                 | "test"
        "src"                | "test"
    }

    def "scan all is enabled but not applied because of overridden properties in build configuration"() {
        given:
        var sonarSourcesProperty = sonarSourcesOverride ? "property 'sonar.sources', '$sonarSourcesOverride'" : ""
        var sonarTestsProperty = sonarTestsOverride ? "property 'sonar.tests', '$sonarTestsOverride'" : ""
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        
        sonar {
            properties {
                $sonarSourcesProperty
                $sonarTestsProperty
            }
        }
        """

        when:
        var arguments = ['sonar', '--info',
                         '-Dsonar.gradle.scanAll=true',
                         '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath()]
        def result = GradleRunner.create()
          .withGradleVersion(gradleVersion)
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments(arguments.stream().filter { it != null }.toList())
          .withPluginClasspath()
          .build()

        then:
        result.task(":sonar").outcome == SUCCESS

        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.gradle.scanAll" == "true"
        result.output.contains("Parameter sonar.gradle.scanAll is enabled. The scanner will attempt to collect additional sources.")
        result.output.contains("Parameter sonar.gradle.scanAll is enabled but the scanner will not collect additional sources because sonar.sources or sonar.tests has been overridden.")

        where:
        sonarSourcesOverride | sonarTestsOverride
        "src"                | null
        null                 | "test"
        "src"                | "test"
    }

    def "scan all excludes coverage report files"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        """
        def extraEmptyScriptThatShouldBeCollected = projectDir.resolve("empty-script.groovy")
        def firstCoverageReport = projectDir.resolve("my-first-coverage-report.xml")
        def secondCoverageReport = projectDir.resolve("my-second-coverage-report.xml")
        def thirdCoverageReport = projectDir.resolve("my-third-coverage-report.xml")
        Files.createFile(extraEmptyScriptThatShouldBeCollected)
        Files.createFile(firstCoverageReport)
        Files.createFile(secondCoverageReport)
        Files.createFile(thirdCoverageReport)

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectDir.toFile())
                .forwardOutput()
                .withArguments('sonar', '--info',
                        '-Dsonar.gradle.scanAll=true',
                        '-Dsonar.coverageReportPaths=my-first-coverage-report.xml,my-second-coverage-report.xml',
                        '-Dsonar.coverage.jacoco.xmlReportPaths=' + thirdCoverageReport.toRealPath().toString(),
                        '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
                .withPluginClasspath()
                .withDebug(true)
                .build()
        print("Hello")
        then:
        result.task(":sonar").outcome == SUCCESS

        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.gradle.scanAll" == "true"
        props."sonar.coverageReportPaths" == "my-first-coverage-report.xml,my-second-coverage-report.xml"
        props."sonar.coverage.jacoco.xmlReportPaths" == thirdCoverageReport.toRealPath().toString()
        result.output.contains("Parameter sonar.gradle.scanAll is enabled. The scanner will attempt to collect additional sources.")

        // Assert that the extra files (empty script and reports) exist on disk
        Files.exists(extraEmptyScriptThatShouldBeCollected)
        Files.exists(firstCoverageReport)
        Files.exists(secondCoverageReport)
        Files.exists(thirdCoverageReport)

        // Test that the empty script is is collected but the reports are not collected
        var mainSources = ((String) props."sonar.sources").split(",")
        mainSources.size() == 4
        var projectPath = projectDir.toFile().getCanonicalPath() + File.separator
        mainSources[0].endsWith("""${projectPath}build.gradle""")
        mainSources[1].endsWith("""${projectPath}empty-script.groovy""")
        mainSources[2].endsWith("""${projectPath}gradle.properties""")
        mainSources[3].endsWith("""${projectPath}settings.gradle""")

        var testSources = ((String) props."sonar.tests").split(",")
        testSources.size() == 2
        testSources[0].endsWith("""${projectPath}integrationTests${File.separator}run-all.sh""")
        testSources[1].endsWith("""${projectPath}test-license.sh""")
    }

    def "sonar task fails when failing to reach the server"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'org.sonarqube'
        }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .forwardOutput()
                .withArguments('sonar', '-Dsonar.host.url=http://localhost:0')
                .withPluginClasspath()
                .buildAndFail()

        then:
        assert result.task(":sonar").getOutcome() == TaskOutcome.FAILED
        assert result.getOutput().contains("Failed to query server version: Invalid URL port: \"0\"")
    }

    def "keep default sonar.region"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        """

        when:
        def result = GradleRunner.create()
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withEnvironment(Map.of())
          .withArguments('sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
          .withPluginClasspath()
          .build();

        then:
        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.host.url" == 'https://sonarcloud.io'
        props."sonar.scanner.apiBaseUrl" == 'https://api.sonarcloud.io'
    }

    def "set sonar.region to us"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        """

        when:
        def result = GradleRunner.create()
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withEnvironment(Map.of())
          .withArguments('sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath(), '-Dsonar.region=us')
          .withPluginClasspath()
          .build();

        then:
        def props = new Properties()
        props.load(outFile.newDataInputStream())
        props."sonar.region" == 'us'
        props."sonar.host.url" == 'https://sonarqube.us'
        props."sonar.scanner.apiBaseUrl" == 'https://api.sonarqube.us'
    }

    def "invalid sonar.region"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        """

        when:
        def result = GradleRunner.create()
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withEnvironment(Map.of())
          .withArguments('sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath(), '-Dsonar.region=invalid')
          .withPluginClasspath()
          .buildAndFail();

        then:
        assert result.task(":sonar").getOutcome() == TaskOutcome.FAILED
        assert result.getOutput().contains("Invalid region 'invalid'.")
    }
}
