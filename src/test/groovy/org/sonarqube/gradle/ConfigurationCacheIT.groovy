/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2026 SonarSource
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
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Integration tests for Gradle configuration cache support.
 * Tests verify that the SonarQube plugin is compatible with Gradle's configuration cache
 * and that the cache is properly used, reused, and invalidated.
 */
class ConfigurationCacheIT extends Specification {

    @TempDir
    Path projectDir
    Path outFile

    Path settingsFile
    Path buildFile

    def setup() {
        settingsFile = projectDir.resolve('settings.gradle')
        buildFile = projectDir.resolve('build.gradle')
        outFile = projectDir.resolve('out.properties')
    }

    def "configuration cache is stored and reused across builds"() {
        given: "a simple Java project with sonarqube plugin"
        settingsFile << "rootProject.name = 'config-cache-test'"
        buildFile << """
            plugins {
                id 'java'
                id 'org.sonarqube'
            }

            repositories {
                mavenCentral()
            }

            sonarqube {
                properties {
                    property "sonar.projectKey", "test-project"
                    property "sonar.projectName", "Test Project"
                }
            }
        """

        def srcDir = projectDir.resolve('src/main/java')
        srcDir.toFile().mkdirs()
        srcDir.resolve('Main.java').toFile() << """
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello World");
                }
            }
        """

        when: "run sonar task with configuration cache - FIRST RUN"
        def firstRun = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .forwardOutput()
            .withArguments('--configuration-cache', '--info', 'sonar',
          '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
            .withPluginClasspath()
            .build()

        then: "configuration cache should be stored"
        firstRun.task(":sonar").outcome == SUCCESS
        firstRun.output.contains("Configuration cache entry stored")

        when: "run sonar task again with configuration cache - SECOND RUN"
        def secondRun = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .forwardOutput()
            .withArguments('--configuration-cache', '--info', 'sonar',
              '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
            .withPluginClasspath()
            .build()

        then: "configuration cache should be reused"
        secondRun.task(":sonar").outcome == SUCCESS
        secondRun.output.contains("Reusing configuration cache")

        and: "output should indicate cache hit"
        // On cache hit, the output should be shorter
        secondRun.output.length() < firstRun.output.length()
    }

    def "configuration cache detects changes in sonar properties"() {
        given: "a Java project with sonarqube plugin"
        settingsFile << "rootProject.name = 'config-cache-invalidation'"
        buildFile << """
            plugins {
                id 'java'
                id 'org.sonarqube'
            }

            sonarqube {
                properties {
                    property "sonar.projectKey", "test-project"
                    property "sonar.projectName", "Original Name"
                }
            }
        """

        when: "run with configuration cache - first time"
        def firstRun = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .forwardOutput()
            .withArguments('--configuration-cache', '--info', 'sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
            .withPluginClasspath()
            .build()

        then:
        firstRun.task(":sonar").outcome == SUCCESS
        firstRun.output.contains("Configuration cache entry stored")

        when: "modify sonar properties in build script"
        buildFile.text = buildFile.text.replace("Original Name", "Modified Name")

        and: "run again with configuration cache"
        def secondRun = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .forwardOutput()
            .withArguments('--configuration-cache', '--info', 'sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
            .withPluginClasspath()
            .build()

        then: "configuration cache should be invalidated and rebuilt"
        secondRun.task(":sonar").outcome == SUCCESS
        // Cache should be invalidated due to build script change
        secondRun.output.contains("Calculating task graph as configuration cache cannot be reused")
        secondRun.output.contains("Configuration cache entry stored")
        !secondRun.output.contains("Reusing configuration cache")
    }

    def "configuration cache works with sonarResolver task"() {
        given: "a Java project with dependencies"
        settingsFile << "rootProject.name = 'resolver-cache-test'"
        buildFile << """
            plugins {
                id 'java'
                id 'org.sonarqube'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation 'com.google.guava:guava:32.1.3-jre'
                testImplementation 'junit:junit:4.13.2'
            }
        """

        def srcDir = projectDir.resolve('src/main/java')
        srcDir.toFile().mkdirs()
        srcDir.resolve('Example.java').toFile() << """
            import com.google.common.collect.ImmutableList;

            public class Example {
                public static void main(String[] args) {
                    ImmutableList<String> list = ImmutableList.of("a", "b");
                }
            }
        """

        when: "run sonarResolver with configuration cache - first run"
        GradleRunner.create().withArguments("clean").withProjectDir(projectDir.toFile()).withPluginClasspath().build()
        def runner = GradleRunner.create()
          .withGradleVersion(gradleVersion)
          .withProjectDir(projectDir.toFile())
          .forwardOutput()
          .withArguments('--configuration-cache', '--info', 'sonarResolver')
          .withPluginClasspath()
        
        def firstRun = runner.build()

        then:
        firstRun.task(":sonarResolver").outcome == SUCCESS
        firstRun.output.contains("Configuration cache entry stored")

        and: "verify the resolver output file was created"
        def propertiesFile = projectDir.resolve('build').resolve('sonar-resolver').resolve('properties').toFile()

        then:
        propertiesFile.exists()

        when: "run sonarResolver again - should reuse cache"
        def secondRun = runner.build()

        then:
        if (gradleVersion >= '9.0') {
            secondRun.task(":sonarResolver").outcome == FROM_CACHE
        } else {
            secondRun.task(":sonarResolver").outcome == SUCCESS
        }
        secondRun.output.contains("Reusing configuration cache")

        where:
        gradleVersion << ['8.14.3', '9.2.1']
    }

    def "configuration cache works with multi-module projects"() {
        given: "a multi-module project"
        settingsFile << """
            rootProject.name = 'multi-module-cache'
            include 'module1', 'module2'
        """
        buildFile << """
            plugins {
                id 'org.sonarqube'
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
            }

            subprojects {
                apply plugin: 'java'
                apply plugin: 'org.sonarqube'
            }
        """

        def module1Dir = projectDir.resolve('module1')
        module1Dir.toFile().mkdirs()
        module1Dir.resolve('build.gradle').toFile() << """
            sonarqube {
                properties {
                    property "sonar.projectName", "Module 1"
                }
            }
        """
        def module1Src = module1Dir.resolve('src/main/java')
        module1Src.toFile().mkdirs()
        module1Src.resolve('Module1.java').toFile() << "public class Module1 {}"

        def module2Dir = projectDir.resolve('module2')
        module2Dir.toFile().mkdirs()
        module2Dir.resolve('build.gradle').toFile() << """
            sonarqube {
                properties {
                    property "sonar.projectName", "Module 2"
                }
            }
        """
        def module2Src = module2Dir.resolve('src/main/java')
        module2Src.toFile().mkdirs()
        module2Src.resolve('Module2.java').toFile() << "public class Module2 {}"

        when: "run with configuration cache - first run"
        def firstRun = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .forwardOutput()
            .withArguments('--configuration-cache', '--info', 'sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
            .withPluginClasspath()
            .build()

        then: "should succeed and store cache"
        firstRun.task(":sonar").outcome == SUCCESS
        firstRun.output.contains("Configuration cache entry stored")

        when: "run again with configuration cache"
        def secondRun = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .forwardOutput()
            .withArguments('--configuration-cache', '--info', 'sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
            .withPluginClasspath()
            .build()

        then: "should reuse cache"
        secondRun.task(":sonar").outcome == SUCCESS
        secondRun.output.contains("Reusing configuration cache")
    }

    def "configuration cache is compatible with parallel execution"() {
        given: "a multi-module project"
        settingsFile << """
            rootProject.name = 'parallel-cache-test'
            include 'module1', 'module2', 'module3'
        """
        buildFile << """
            plugins {
                id 'org.sonarqube'
            }

            subprojects {
                apply plugin: 'java'
                apply plugin: 'org.sonarqube'

                repositories {
                    mavenCentral()
                }
            }
        """

        ['module1', 'module2', 'module3'].each { moduleName ->
            def moduleDir = projectDir.resolve(moduleName)
            moduleDir.toFile().mkdirs()
            moduleDir.resolve('build.gradle').toFile() << "// ${moduleName}"

            def srcDir = moduleDir.resolve('src/main/java')
            srcDir.toFile().mkdirs()
            srcDir.resolve("${moduleName.capitalize()}.java").toFile() <<
                "public class ${moduleName.capitalize()} {}"
        }

        when: "run with configuration cache and parallel execution"
        def result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .forwardOutput()
            .withArguments('--configuration-cache', '--parallel', '--info', 'sonar', '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
            .withPluginClasspath()
            .build()

        then: "should succeed without configuration cache issues"
        result.task(":sonar").outcome == SUCCESS

        and: "no configuration cache problems should be reported"
        !result.output.contains("Configuration cache problems found")
        !result.output.contains("configuration cache problem")
    }

    def "configuration cache handles property changes via system properties"() {
        given: "a simple Java project"
        settingsFile << "rootProject.name = 'system-prop-test'"
        buildFile << """
            plugins {
                id 'java'
                id 'org.sonarqube'
            }
        """

        when: "run with configuration cache and a system property"
        def firstRun = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .forwardOutput()
            .withArguments('--configuration-cache', '--info', 'sonar',
                '-Dsonar.projectKey=first-key',
                '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
            .withPluginClasspath()
            .build()

        then:
        firstRun.task(":sonar").outcome == SUCCESS

        when: "run again with different system property value"
        def secondRun = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .forwardOutput()
            .withArguments('--configuration-cache', '--info', 'sonar',
                '-Dsonar.projectKey=second-key',
                '-Dsonar.scanner.internal.dumpToFile=' + outFile.toAbsolutePath())
            .withPluginClasspath()
            .build()

        then: "configuration cache should be invalidated due to property change"
        secondRun.task(":sonar").outcome == SUCCESS
        // System property changes should trigger cache invalidation
        secondRun.output.contains("Calculating task graph as configuration cache cannot be reused")
    }
}
