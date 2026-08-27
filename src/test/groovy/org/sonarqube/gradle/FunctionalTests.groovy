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
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import static java.util.Objects.nonNull
import static org.assertj.core.api.Assertions.assertThat
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class FunctionalTests extends Specification {
    String gradleVersion = "8.14"

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
        configureJacocoGradleTestkitPlugin(projectDir)
    }

    /**
     * Configure gradle.properties for Gradle TestKit projects.
     * @param dir the root of the test project
     */
    static def configureJacocoGradleTestkitPlugin(Path dir) {
        TestKitGradleProperties.configure(dir)
    }

    def "'java' project"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'org.sonarqube'
            id 'java'
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
        props.containsKey("sonar.java.jdkHome")
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
            languageVersion = JavaLanguageVersion.of(11)
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
          .err.text.contains("\"11.")
        props."sonar.java.source" == '11'
        props."sonar.java.target" == '11'
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

        tasks.withType(JavaCompile).configureEach {
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
            languageVersion = JavaLanguageVersion.of(11)
          }
        }
        compileTestJava {
          javaCompiler = javaToolchains.compilerFor {
            languageVersion = JavaLanguageVersion.of(17)
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
          .err.text.contains("\"11.")
        props."sonar.java.source" == '11'
        props."sonar.java.target" == '11'
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
          options.release = 11
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
        props."sonar.java.source" == '11'
        props."sonar.java.target" == '11'
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
            languageVersion = JavaLanguageVersion.of(11)
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

    @IgnoreIf({ System.getenv("SONAR_REGION") != null })
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
                .withArguments('sonar', '-Dsonar.host.url=http://localhost:0', '--info')
                .withPluginClasspath()
                .buildAndFail()

        then:
        assert result.task(":sonar").getOutcome() == TaskOutcome.FAILED
        assert result.getOutput().contains("Failed to query server version: Call to URL [http://localhost:")
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
          .build()

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
          .build()

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
          .buildAndFail()

        then:
        assert result.task(":sonar").getOutcome() == TaskOutcome.FAILED
        assert result.getOutput().contains("Invalid region 'invalid'.")
    }

    def "clean sonar does not fail on a clean project"() {
        given:
        settingsFile << "rootProject.name = 'java-task-toolchains'"
        buildFile << """
        plugins {
            id 'java'
            id 'org.sonarqube'
        }
        """
        assert !projectDir.resolve("build").resolve("sonar-resolver").resolve("properties").toFile().exists()

        when:
        def result = GradleRunner.create()
          .withProjectDir(projectDir.toFile())
          .withGradleVersion("9.1.0")
          .forwardOutput()
          .withArguments('clean', 'sonar',  '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
          .withPluginClasspath()
          .build()

        then:
        assert result.task(":clean").getOutcome() == UP_TO_DATE
        assert result.task(":sonar").getOutcome() == SUCCESS
    }

  def "multi module gradle project"() {
    given:
    def multiModuleProjectDir = projectDir("gradle-multimodule")
    // skip jacoco execution due to lock conflict on "build/jacoco/test.exec" when executing on windows
    if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
      configureJacocoGradleTestkitPlugin(multiModuleProjectDir)
    }


    when:
    // Note that this test uses the current Gradle version instead of the one defined in gradleVersion.
    // This is because there seem to be a bug with older versions of Gradle TestKit when dealing with multi-module projects.
    // Older versions need to be tested using end-to-end tests (in the `integrationTests` module).
    def result = GradleRunner.create()
      .withProjectDir(multiModuleProjectDir.toFile())
      .forwardOutput()
      .withArguments('clean', 'build', 'sonar',  '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
      .withPluginClasspath()
      .build()

    then:
    def sonarResolver = multiModuleProjectDir.resolve("module-1/build/sonar-resolver")
    assert result.task(":sonar").getOutcome() == SUCCESS
    assert Files.notExists(sonarResolver.resolve("properties"))

  }

  @Requires({ System.getenv("JAVA_HOME") != null && System.getenv("ANDROID_HOME") != null })
  def "Android resolved sources do not duplicate KMP source directories"() {
    given:
    // Gradle TestKit isolates the plugin under test from the test project's other plugins when using withPluginClasspath():
    // https://github.com/gradle/gradle/issues/22466
    // This fixture keeps Sonar and AGP on the buildscript classpath so the public sonar task can exercise the real resolver flow.
    def kmpAndroidProjectDir = kmpAndroidProject()

    when:
    def result = GradleRunner.create()
      .withProjectDir(kmpAndroidProjectDir.toFile())
      .withGradleVersion("9.5.1")
      .forwardOutput()
      .withArguments(
        'sonar',
        '--info',
        '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath(),
        '-DsonarPluginClasspath=' + pluginClasspath()
      )
      .build()

    then:
    result.task(":sonar").getOutcome() == SUCCESS

    def props = new Properties()
    props.load(outFile.newDataInputStream())

    def sources = dumpedPathsRelativeTo(props, "sonar.sources", kmpAndroidProjectDir)
    def tests = dumpedPathsRelativeTo(props, "sonar.tests", kmpAndroidProjectDir)

    assertThat(sources).contains("src/androidMain/kotlin", "src/commonMain/kotlin")
    assertThat(tests).contains("src/androidUnitTest/kotlin")
    assertThat(sources).doesNotHaveDuplicates()
    assertThat(tests).doesNotHaveDuplicates()
  }

  @Requires({ System.getenv("JAVA_HOME") != null && System.getenv("ANDROID_HOME") != null })
  def "Android KMP new DSL fixture exposes current unsupported library resolution behavior"() {
    given:
    def kmpAndroidProjectDir = kmpAndroidProjectNewDsl()

    when:
    def result = GradleRunner.create()
      .withProjectDir(kmpAndroidProjectDir.toFile())
      .withGradleVersion("9.5.1")
      .forwardOutput()
      .withArguments(
        'sonar',
        '--info',
        '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath(),
        '-DsonarPluginClasspath=' + pluginClasspath()
      )
      .build()

    then:
    result.task(":sonar").getOutcome() == SUCCESS

    def props = new Properties()
    props.load(outFile.newDataInputStream())

    def sources = dumpedPathsRelativeTo(props, "sonar.sources", kmpAndroidProjectDir)
    def tests = dumpedPathsRelativeTo(props, "sonar.tests", kmpAndroidProjectDir)

    assertThat(sources).contains("src/androidMain/kotlin", "src/commonMain/kotlin")
    assertThat(tests).contains("src/androidHostTest/kotlin")
    assertThat(sources).doesNotHaveDuplicates()
    assertThat(tests).doesNotHaveDuplicates()
    assertThat(props.getProperty("sonar.java.libraries", "")).isEmpty()
    assertThat(props.getProperty("sonar.java.test.libraries", "")).isEmpty()
  }

   def "check sonarResolver can be up to date while sonar is not"() {
     given:
     settingsFile << "rootProject.name = 'java-task-toolchains'"
     buildFile << """
        plugins {
            id 'org.sonarqube'
            id 'java'
        }
        """

     when:
     def command = GradleRunner.create()
       .withProjectDir(projectDir.toFile())
       .forwardOutput()
       .withArguments('sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath(), '--info')
       .withPluginClasspath()

     def run1 = command.build()
     def run2 = command.build()

     then:
     assert run1.task(":sonar").getOutcome() == SUCCESS
     assert run2.task(":sonar").getOutcome() == SUCCESS
     assert run2.task(":sonarResolver").getOutcome() == UP_TO_DATE
     assert run2.getOutput().contains("':sonar' is not up-to-date")
   }

   def "sonarResolver is not up to date when compile classpath changes"() {
     given:
     settingsFile << "rootProject.name = 'java-task-toolchains'"
     def compileOnlyJar = projectDir.resolve("libs/compile-only.jar")
     def testCompileOnlyJar = projectDir.resolve("libs/test-compile-only.jar")
     writeJar(compileOnlyJar, "initial/CompileOnly.txt", "initial compile classpath")
     writeJar(testCompileOnlyJar, "initial/TestCompileOnly.txt", "initial test compile classpath")
     buildFile << """
        plugins {
            id 'org.sonarqube'
            id 'java'
        }

        dependencies {
            compileOnly files('libs/compile-only.jar')
            testCompileOnly files('libs/test-compile-only.jar')
        }
        """

     when:
     def command = GradleRunner.create()
       .withProjectDir(projectDir.toFile())
       .forwardOutput()
       .withArguments('sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath(), '--info')
       .withPluginClasspath()

     def run1 = command.build()
     def run2 = command.build()
     writeJar(compileOnlyJar, "changed/CompileOnly.txt", "changed compile classpath contents")
     writeJar(testCompileOnlyJar, "changed/TestCompileOnly.txt", "changed test compile classpath contents")
     def run3 = command.build()

     then:
     assert run1.task(":sonar").getOutcome() == SUCCESS
     assert run2.task(":sonar").getOutcome() == SUCCESS
     assert run2.task(":sonarResolver").getOutcome() == UP_TO_DATE
     assert run3.task(":sonar").getOutcome() == SUCCESS
     assert run3.task(":sonarResolver").getOutcome() == SUCCESS
   }

   def "sonarResolver is not up to date when compile classpath directory changes"() {
     given:
     settingsFile << "rootProject.name = 'java-task-toolchains'"
     def compileOnlyDir = projectDir.resolve("libs/compile-only-dir")
     def testCompileOnlyDir = projectDir.resolve("libs/test-compile-only-dir")
     writeFile(compileOnlyDir.resolve("initial/CompileOnly.class"), "initial compile classpath")
     writeFile(testCompileOnlyDir.resolve("initial/TestCompileOnly.class"), "initial test compile classpath")
     buildFile << """
        plugins {
            id 'org.sonarqube'
            id 'java'
        }

        dependencies {
            compileOnly files('libs/compile-only-dir')
            testCompileOnly files('libs/test-compile-only-dir')
        }
        """

     when:
     def command = GradleRunner.create()
       .withProjectDir(projectDir.toFile())
       .forwardOutput()
       .withArguments('sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath(), '--info')
       .withPluginClasspath()

     def run1 = command.build()
     def run2 = command.build()
     writeFile(compileOnlyDir.resolve("changed/CompileOnly.class"), "changed compile classpath contents")
     writeFile(testCompileOnlyDir.resolve("changed/TestCompileOnly.class"), "changed test compile classpath contents")
     def run3 = command.build()

     then:
     assert run1.task(":sonar").getOutcome() == SUCCESS
     assert run2.task(":sonar").getOutcome() == SUCCESS
     assert run2.task(":sonarResolver").getOutcome() == UP_TO_DATE
     assert run3.task(":sonar").getOutcome() == SUCCESS
     assert run3.task(":sonarResolver").getOutcome() == SUCCESS
   }

  def "sonarResolver tracks Java resource outputs through their producer"() {
    given:
    settingsFile << "rootProject.name = 'root'"
    buildFile << """
        plugins {
            id 'org.sonarqube'
            id 'java-library'
        }
        """
    writeFile(projectDir.resolve("src/main/resources/f.txt"), "x")

    when:
    def result = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withGradleVersion("9.5.1")
      .forwardOutput()
      .withPluginClasspath()
      .withArguments(':processResources', ':sonarResolver')
      .build()

    then:
    result.task(":processResources").getOutcome() == SUCCESS
    result.task(":sonarResolver").getOutcome() == SUCCESS
    result.tasks*.path.indexOf(":processResources") < result.tasks*.path.indexOf(":sonarResolver")
    def resolverPropertiesFile = projectDir.resolve("build/sonar-resolver/properties").toFile()
    def resolverProperties = new JsonSlurper().parse(resolverPropertiesFile)
    def resourcesOutput = projectDir.resolve("build/resources/main").toAbsolutePath().toString()
    new File(resourcesOutput).exists()
    resolverProperties.testCompileClasspath.contains(resourcesOutput)
  }

  def "sonarResolver skips compile classpaths that fail to resolve"() {
    given:
    settingsFile << "rootProject.name = 'root'"
    Files.createDirectories(projectDir.resolve("empty-repository"))
    def existingLibrary = projectDir.resolve("existing-library.txt")
    writeFile(existingLibrary, "existing library")
    buildFile << """
        plugins {
            id 'org.sonarqube'
            id 'java-library'
        }

        repositories {
            maven { url = uri('empty-repository') }
        }

        dependencies {
            implementation 'invalid.example:missing-artifact:1.0'
        }

        tasks.named('sonarResolver') {
            mainLibraries.from(file('existing-library.txt'))
        }
        """

    when:
    def result = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withGradleVersion("9.5.1")
      .forwardOutput()
      .withPluginClasspath()
      .withArguments(':sonarResolver')
      .build()

    then:
    result.task(":sonarResolver").getOutcome() == SUCCESS
    result.output.contains("Failed to resolve file collection input; skipping it.")
    def resolverPropertiesFile = projectDir.resolve("build/sonar-resolver/properties").toFile()
    def resolverProperties = new JsonSlurper().parse(resolverPropertiesFile)
    resolverProperties.compileClasspath.isEmpty()
    resolverProperties.testCompileClasspath.isEmpty()
    resolverProperties.mainLibraries == [existingLibrary.toAbsolutePath().toString()]
  }

  def "sonarResolver tracks a Kotlin Multiplatform JVM artifact through jvmJar"() {
    given:
    settingsFile << """
        pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
        rootProject.name = 'root'
        include 'consumer', 'subdependency'
        """
    buildFile << """
        plugins {
            id 'org.sonarqube'
            id 'org.jetbrains.kotlin.multiplatform' version '2.2.20' apply false
        }

        allprojects { repositories { mavenCentral() } }

        project(':subdependency') {
            apply plugin: 'org.jetbrains.kotlin.multiplatform'
            kotlin { jvm() }
        }

        project(':consumer') {
            apply plugin: 'java-library'
            dependencies { implementation project(':subdependency') }
        }
        """
    writeFile(
      projectDir.resolve("subdependency/src/commonMain/kotlin/example/Dependency.kt"),
      "package example\nclass Dependency"
    )
    writeFile(
      projectDir.resolve("consumer/src/main/java/example/Consumer.java"),
      "package example;\npublic class Consumer {}"
    )

    when:
    def result = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withGradleVersion("9.5.1")
      .forwardOutput()
      .withPluginClasspath()
      .withArguments(':consumer:sonarResolver', ':subdependency:jvmJar')
      .build()

    then:
    result.task(":subdependency:jvmJar").getOutcome() == SUCCESS
    result.task(":consumer:sonarResolver").getOutcome() == SUCCESS
    result.tasks*.path.indexOf(":subdependency:jvmJar") < result.tasks*.path.indexOf(":consumer:sonarResolver")
    def resolverPropertiesFile = projectDir.resolve("consumer/build/sonar-resolver/properties").toFile()
    def resolverProperties = new JsonSlurper().parse(resolverPropertiesFile)
    resolverProperties.compileClasspath.any {
      it.startsWith(projectDir.resolve("subdependency/build/libs").toAbsolutePath().toString()) && it.endsWith(".jar")
    }

    when:
    def resolverOnlyResult = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withGradleVersion("9.5.1")
      .forwardOutput()
      .withPluginClasspath()
      .withArguments(':consumer:sonarResolver', '--rerun-tasks')
      .build()

    then:
    resolverOnlyResult.task(":subdependency:jvmJar") == null
    resolverOnlyResult.task(":consumer:sonarResolver").getOutcome() == SUCCESS
    def resolverOnlyProperties = new JsonSlurper().parse(resolverPropertiesFile)
    resolverOnlyProperties.compileClasspath.any {
      it.startsWith(projectDir.resolve("subdependency/build/libs").toAbsolutePath().toString()) && it.endsWith(".jar")
    }
  }

  private Path projectDir(String project) {
    return Path.of("src", "test", "projects", project)
  }

  private static void writeFile(Path file, String content) {
    Files.createDirectories(file.getParent())
    Files.writeString(file, content)
  }

  private static void writeJar(Path jar, String entryName, String content) {
    Files.createDirectories(jar.getParent())
    Files.deleteIfExists(jar)
    jar.toFile().withOutputStream { output ->
      new JarOutputStream(output).withCloseable { jarOutput ->
        jarOutput.putNextEntry(new JarEntry(entryName))
        jarOutput.write(content.getBytes("UTF-8"))
        jarOutput.closeEntry()
      }
    }
  }

  private Path kmpAndroidProject() {
    return copiedFixtureProject("kmp-android-double-indexing")
  }

  private Path kmpAndroidProjectNewDsl() {
    return copiedFixtureProject("kmp-android-double-indexing-new-dsl")
  }

  private Path copiedFixtureProject(String fixtureName) {
    def fixtureDir = projectDir(fixtureName)
    def targetDir = projectDir.resolve(fixtureName)
    Files.createDirectories(targetDir)
    Files.copy(fixtureDir.resolve("settings.gradle.kts"), targetDir.resolve("settings.gradle.kts"))
    Files.copy(fixtureDir.resolve("gradle.properties"), targetDir.resolve("gradle.properties"))
    Files.copy(fixtureDir.resolve("build.gradle.kts"), targetDir.resolve("build.gradle.kts"))
    copyDirectory(fixtureDir.resolve("src"), targetDir.resolve("src"))
    // Android SDK locations are machine-local, so the checked-in fixture must not hardcode one.
    targetDir.resolve("local.properties") << "sdk.dir=${androidSdkPath()}\n"
    return targetDir
  }

  private String pluginClasspath() {
    return getClass().classLoader.getResource("plugin-under-test-metadata.properties")
      .withInputStream { stream ->
        def props = new Properties()
        props.load(stream)
        return props.getProperty("implementation-classpath")
      }
  }

  private static List<String> dumpedPaths(Properties properties, String propertyName) {
    return properties.getProperty(propertyName, "").split(",")
      .findAll { !it.isBlank() }
      .collect { Path.of(it).toAbsolutePath().normalize().toString() }
  }

  private static List<String> dumpedPathsRelativeTo(Properties properties, String propertyName, Path baseDir) {
    def normalizedBaseDir = baseDir.toRealPath()
    return dumpedPaths(properties, propertyName)
      .collect { existingRealPathOrNormalizedPath(it) }
      .findAll { it.startsWith(normalizedBaseDir) }
      .collect { normalizedBaseDir.relativize(it).toString().replace(File.separator, '/') }
  }

  private static Path existingRealPathOrNormalizedPath(String path) {
    def normalizedPath = Path.of(path).toAbsolutePath().normalize()
    return Files.exists(normalizedPath) ? normalizedPath.toRealPath() : normalizedPath
  }

  private static String androidSdkPath() {
    return (System.getenv("ANDROID_HOME")
      ?: System.getenv("ANDROID_SDK_ROOT")
      ?: Path.of(System.getProperty("user.home"), "Android", "Sdk").toString())
      .replace("\\", "\\\\")
  }

  private static void copyDirectory(Path sourceDir, Path targetDir) {
    Files.walk(sourceDir).withCloseable { paths ->
      paths.forEach { source ->
        def target = targetDir.resolve(sourceDir.relativize(source).toString())
        if (Files.isDirectory(source)) {
          Files.createDirectories(target)
        } else {
          Files.createDirectories(target.parent)
          Files.copy(source, target)
        }
      }
    }
  }

  // some analyzer accept and expand path containing wildcards, they must not be removed
  def "path containing wildcards are not removed"() {
    given:
    var sonarSourcesProperty = "property 'sonar.sources', '$mainSources'"
    var sonarTestsProperty = "property 'sonar.tests', '$testSource'"
    var sonarJavaJdkHomeProperty = "property 'sonar.java.jdkHome', '$javaJdkHome'"
    var sonarJavaBinariesProperty = "property 'sonar.java.binaries', '$javaBinaries'"
    var sonarJavaLibrariesProperty = "property 'sonar.java.libraries', '$javaLibraries'"
    var sonarJavaTestBinariesProperty = "property 'sonar.java.test.binaries', '$javaTestBinaries'"
    var sonarJavaTestLibrariesProperty = "property 'sonar.java.test.libraries', '$javaTestLibraries'"
    var sonarLibrariesProperty = "property 'sonar.libraries', '$libraries'"
    var sonarGroovyBinariesProperty = "property 'sonar.groovy.binaries', '$groovyBinaries'"
    var sonarKotlinGradleProjectRootProperty = "property 'sonar.kotlin.gradle.project.root', '$kotlinGradleProjectRoot'"
    var sonarJunitReportPathsProperty = "property 'sonar.junit.reportPaths', '$junitReportPaths'"
    var sonarJunitReportsPathProperty = "property 'sonar.junit.reportsPath', '$junitReportsPath'"
    var sonarSurefireReportsPathProperty = "property 'sonar.surefire.reportsPath', '$surefireReportsPath'"
    var sonarJacocoXmlReportPathsProperty = "property 'sonar.coverage.jacoco.xmlReportPaths', '$jacocoXmlReportPaths'"
    var sonarAndroidLintReportPathsProperty = "property 'sonar.android.lint.reportPaths', '$androidLintReportPaths'"
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
                $sonarJavaJdkHomeProperty
                $sonarJavaBinariesProperty
                $sonarJavaLibrariesProperty
                $sonarJavaTestBinariesProperty
                $sonarJavaTestLibrariesProperty
                $sonarLibrariesProperty
                $sonarGroovyBinariesProperty
                $sonarKotlinGradleProjectRootProperty
                $sonarJunitReportPathsProperty
                $sonarJunitReportsPathProperty
                $sonarSurefireReportsPathProperty
                $sonarJacocoXmlReportPathsProperty
                $sonarAndroidLintReportPathsProperty
            }
        }
        """

    when:
    def result = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .forwardOutput()
      .withArguments('sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath(), "--stacktrace")
      .withPluginClasspath()
      .build()

    then:
    result.task(":sonar").outcome == SUCCESS

    def props = new Properties()
    props.load(outFile.newDataInputStream())
    props."sonar.java.jdkHome" == javaJdkHome
    props."sonar.java.binaries" == javaBinaries
    props."sonar.java.libraries" == javaLibraries
    props."sonar.java.test.binaries" == javaTestBinaries
    props."sonar.java.test.libraries" == javaTestLibraries
    props."sonar.libraries" == libraries
    props."sonar.groovy.binaries" == groovyBinaries
    props."sonar.kotlin.gradle.project.root" == kotlinGradleProjectRoot
    props."sonar.junit.reportPaths" == junitReportPaths
    props."sonar.junit.reportsPath" == junitReportsPath
    props."sonar.surefire.reportsPath" == surefireReportsPath
    props."sonar.coverage.jacoco.xmlReportPaths" == jacocoXmlReportPaths
    props."sonar.android.lint.reportPaths" == androidLintReportPaths

    where:
    // first test path that do not exists
    // second test wildcard values and invalid values
    mainSources | testSource  | javaJdkHome | javaBinaries | javaLibraries | javaTestBinaries | javaTestLibraries | libraries | groovyBinaries | kotlinGradleProjectRoot | junitReportPaths | junitReportsPath | surefireReportsPath | jacocoXmlReportPaths | androidLintReportPaths
    "source/*/" | "**/tests"  | "jdkH?/"    | "*?.*.*/"    | "*?.*.*/"     | "*?.*.*/,**/?"   | "*?.*.*/"         | "*?.*.*/" | "*?.*.*/"      | "*?.*.*/"               | "*?.*.*/"        | "*?.*.*/"        | "*?.*.*/"           | "*?.*.*/"            | "*?.*.*/"
  }
}
