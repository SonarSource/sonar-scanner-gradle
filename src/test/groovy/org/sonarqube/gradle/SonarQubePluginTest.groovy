/*
 * SonarQube Gradle Plugin
 * Copyright (C) 2015-2017 SonarSource
 * sonarqube@googlegroups.com
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

import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import spock.lang.Specification

import static org.hamcrest.Matchers.contains
import static org.hamcrest.Matchers.containsInAnyOrder
import static spock.util.matcher.HamcrestSupport.expect

class SonarQubePluginTest extends Specification {

    def rootProject = ProjectBuilder.builder().withName("root").build()
    def parentProject = ProjectBuilder.builder().withName("parent").withParent(rootProject).build()
    def childProject = ProjectBuilder.builder().withName("child").withParent(parentProject).build()
    def childProject2 = ProjectBuilder.builder().withName("child2").withParent(parentProject).build()
    def leafProject = ProjectBuilder.builder().withName("leaf").withParent(childProject).build()

    def setup() {
        parentProject.apply plugin: SonarQubePlugin
        parentProject.repositories {
            mavenCentral()
        }

        rootProject.allprojects {
            group = "group"
            version = 1.3
            description = "description"
            buildDir = "buildDir"
        }
    }

    def "adds a sonarqube extension to the target project (i.e. the project to which the plugin is applied) and its subprojects"() {
        expect:
        rootProject.extensions.findByName("sonarqube") == null
        parentProject.extensions.findByName("sonarqube") instanceof SonarQubeExtension
        childProject.extensions.findByName("sonarqube") instanceof SonarQubeExtension
    }

    def "adds a sonarqube task to the target project"() {
        expect:
        parentProject.tasks.findByName("sonarqube") instanceof SonarQubeTask
        parentSonarQubeTask().description == "Analyzes project ':parent' and its subprojects with SonarQube."

        childProject.tasks.findByName("sonarqube") == null
    }

    def "makes sonarqube task depend on test tasks of the target project and its subprojects"() {
        when:
        rootProject.pluginManager.apply(JavaPlugin)
        parentProject.pluginManager.apply(JavaPlugin)
        childProject.pluginManager.apply(JavaPlugin)

        then:
        expect(parentSonarQubeTask(), TaskDependencyMatchers.dependsOnPaths(containsInAnyOrder(":parent:test", ":parent:child:test")))
    }

    def "doesn't make sonarqube task depend on test task of skipped projects"() {
        when:
        rootProject.pluginManager.apply(JavaPlugin)
        parentProject.pluginManager.apply(JavaPlugin)
        childProject.pluginManager.apply(JavaPlugin)
        childProject.sonarqube.skipProject = true

        then:
        expect(parentSonarQubeTask(), TaskDependencyMatchers.dependsOnPaths(contains(":parent:test")))
    }

    def "adds default properties for target project and its subprojects"() {
        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties["sonar.sources"] == ""
        properties["sonar.projectName"] == "parent"
        properties["sonar.projectDescription"] == "description"
        properties["sonar.projectVersion"] == "1.3"
        properties["sonar.projectBaseDir"] == parentProject.projectDir as String
        properties["sonar.working.directory"] == new File(parentProject.buildDir, "sonar") as String

        and:
        properties[":parent:child.sonar.sources"] == ""
        properties[":parent:child.sonar.projectName"] == "child"
        properties[":parent:child.sonar.projectDescription"] == "description"
        properties[":parent:child.sonar.projectVersion"] == "1.3"
        properties[":parent:child.sonar.projectBaseDir"] == childProject.projectDir as String
    }

    def "adds additional default properties for target project"() {
        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties["sonar.projectKey"] == "group:root:parent"
        properties[":parent:child.sonar.moduleKey"] == "group:root:parent:parent:child"
        properties["sonar.working.directory"] == new File(parentProject.buildDir, "sonar") as String

        and:
        !properties.containsKey(":parent:child.sonar.projectKey")
        !properties.containsKey(':parent:child.sonar.working.directory')
    }

    def "defaults projectKey to project.name if project.group isn't set"() {
        rootProject.group = null

        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties["sonar.projectKey"] == "root:parent"
    }

    def "adds additional default properties for 'java-base' projects"() {
        parentProject.pluginManager.apply(JavaBasePlugin)
        childProject.pluginManager.apply(JavaBasePlugin)
        parentProject.sourceCompatibility = 1.5
        parentProject.targetCompatibility = 1.6
        childProject.sourceCompatibility = 1.6
        childProject.targetCompatibility = 1.7

        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties["sonar.java.source"] == "1.5"
        properties["sonar.java.target"] == "1.6"
        properties[":parent:child.sonar.java.source"] == "1.6"
        properties[":parent:child.sonar.java.target"] == "1.7"
    }

    def "adds additional default properties for 'groovy-base' projects"() {
        parentProject.pluginManager.apply(GroovyBasePlugin)
        childProject.pluginManager.apply(GroovyBasePlugin)
        parentProject.sourceCompatibility = 1.5
        parentProject.targetCompatibility = 1.6
        childProject.sourceCompatibility = 1.6
        childProject.targetCompatibility = 1.7

        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties["sonar.java.source"] == "1.5"
        properties["sonar.java.target"] == "1.6"
        properties[":parent:child.sonar.java.source"] == "1.6"
        properties[":parent:child.sonar.java.target"] == "1.7"
    }

    def "adds additional default properties for 'java' projects"() {
        def rootProject = ProjectBuilder.builder().withName("root").build()
        def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).withProjectDir(new File("src/test/projects/java-project")).build()

        project.pluginManager.apply(SonarQubePlugin)

        project.pluginManager.apply(JavaPlugin)

        project.sourceSets.main.java.srcDirs = ["src"]
        project.sourceSets.test.java.srcDirs = ["test"]
        project.sourceSets.main.java.outputDir = new File(project.buildDir, "out")
        project.sourceSets.main.compileClasspath += project.files("lib/SomeLib.jar")
        project.sourceSets.test.java.outputDir = new File(project.buildDir, "test-out")
        project.sourceSets.test.compileClasspath += project.files("lib/junit.jar")
        project.compileJava.options.encoding = 'ISO-8859-1'

        def testResultsDir = new File(project.buildDir, "test-results/test")
        testResultsDir.mkdirs()
        new File(testResultsDir, 'TEST-.xml').createNewFile()

        when:
        def properties = project.tasks.sonarqube.properties

        then:
        properties["sonar.sources"] == new File(project.projectDir, "src") as String
        properties["sonar.tests"] == new File(project.projectDir, "test") as String
        properties["sonar.java.binaries"].contains(new File(project.buildDir, "out") as String)
        properties["sonar.java.libraries"].contains(new File(project.projectDir, "lib/SomeLib.jar") as String)
        properties["sonar.java.test.binaries"].contains(new File(project.buildDir, "test-out") as String)
        properties["sonar.java.test.libraries"].contains(new File(project.projectDir, "lib/junit.jar") as String)
        properties["sonar.java.test.libraries"].contains(new File(project.buildDir, "out") as String)
        properties["sonar.binaries"].contains(new File(project.buildDir, "out") as String)
        properties["sonar.libraries"].contains(new File(project.projectDir, "lib/SomeLib.jar") as String)
        properties["sonar.surefire.reportsPath"] == new File(project.buildDir, "test-results/test") as String
        properties["sonar.junit.reportsPath"] == new File(project.buildDir, "test-results/test") as String
        properties["sonar.junit.reportPaths"] == new File(project.buildDir, "test-results/test") as String
        properties["sonar.sourceEncoding"] == "ISO-8859-1"
    }

    def "adds coverage properties for 'java' projects"() {
        def rootProject = ProjectBuilder.builder().withName("root").build()
        def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).withProjectDir(new File("src/test/projects/java-project")).build()

        project.pluginManager.apply(SonarQubePlugin)
        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply(JacocoPlugin)

        project.sourceSets.main.java.srcDirs = ["src"]

        when:
        def properties = project.tasks.sonarqube.properties

        then:
        properties["sonar.jacoco.reportPath"].contains(new File(project.buildDir, "jacoco/test.exec") as String)
        properties["sonar.jacoco.reportPaths"].contains(new File(project.buildDir, "jacoco/test.exec") as String)
    }

    def "don't crash for 'java' projects if test task was overriden"() {
        def rootProject = ProjectBuilder.builder().withName("root").build()
        def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).withProjectDir(new File("src/test/projects/java-project")).build()

        project.pluginManager.apply(SonarQubePlugin)
        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply(JacocoPlugin)

        project.sourceSets.main.java.srcDirs = ["src"]

        project.tasks.remove(project.tasks.findByPath('test'));
        Map taskProperties = [
                name: 'test',
                type: FakeTask,
                group: JavaBasePlugin.VERIFICATION_GROUP,
                description: 'Runs unit tests with the randomized testing framework'
        ]
        FakeTask newTestTask = project.tasks.create(taskProperties)

        when:
        def properties = project.tasks.sonarqube.properties

        then:
        !properties.containsKey("sonar.junit.reportPaths")
        !properties.containsKey("sonar.jacoco.reportPaths")
    }

    def "adds additional default properties for 'groovy' projects"() {
        def rootProject = ProjectBuilder.builder().withName("root").build()
        def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).withProjectDir(new File("src/test/projects/java-project")).build()

        project.pluginManager.apply(SonarQubePlugin)

        project.pluginManager.apply(GroovyPlugin)

        project.sourceSets.main.groovy.srcDirs = ["src"]
        project.sourceSets.test.groovy.srcDirs = ["test"]
        project.sourceSets.main.output.classesDir = "$project.buildDir/out"
        project.sourceSets.main.java.outputDir = new File(project.buildDir, "out")
        project.sourceSets.main.compileClasspath += project.files("lib/SomeLib.jar")
        project.sourceSets.test.java.outputDir = new File(project.buildDir, "test-out")
        project.sourceSets.test.compileClasspath += project.files("lib/junit.jar")
        project.compileJava.options.encoding = 'ISO-8859-1'

        def testResultsDir = new File(project.buildDir, "test-results/test")
        testResultsDir.mkdirs()
        new File(testResultsDir, 'TEST-.xml').createNewFile()

        when:
        def properties = project.tasks.sonarqube.properties

        then:
        properties["sonar.sources"] == new File(project.projectDir, "src") as String
        properties["sonar.tests"] == new File(project.projectDir, "test") as String
        properties["sonar.java.binaries"].contains(new File(project.buildDir, "out") as String)
        properties["sonar.groovy.binaries"].contains(new File(project.buildDir, "out") as String)
        properties["sonar.java.libraries"].contains(new File(project.projectDir, "lib/SomeLib.jar") as String)
        properties["sonar.java.test.binaries"].contains(new File(project.buildDir, "test-out") as String)
        properties["sonar.java.test.libraries"].contains(new File(project.projectDir, "lib/junit.jar") as String)
        properties["sonar.binaries"].contains(new File(project.buildDir, "out") as String)
        properties["sonar.libraries"].contains(new File(project.projectDir, "lib/SomeLib.jar") as String)
        properties["sonar.surefire.reportsPath"] == new File(project.buildDir, "test-results/test") as String
        properties["sonar.junit.reportsPath"] == new File(project.buildDir, "test-results/test") as String
        properties["sonar.junit.reportPaths"] == new File(project.buildDir, "test-results/test") as String
        properties["sonar.sourceEncoding"] == "ISO-8859-1"
    }

    def "adds coverage properties for 'groovy' projects"() {
        def rootProject = ProjectBuilder.builder().withName("root").build()
        def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).withProjectDir(new File("src/test/projects/java-project")).build()

        project.pluginManager.apply(SonarQubePlugin)
        project.pluginManager.apply(GroovyPlugin)
        project.pluginManager.apply(JacocoPlugin)

        project.sourceSets.main.java.srcDirs = ["src"]

        when:
        def properties = project.tasks.sonarqube.properties

        then:
        properties["sonar.groovy.jacoco.reportPath"].contains(new File(project.buildDir, "jacoco/test.exec") as String)
    }

    def "only adds existing directories"() {
        parentProject.pluginManager.apply(JavaPlugin)

        when:
        def properties = parentSonarQubeTask().properties

        then:
        !properties.containsKey("sonar.tests")
        !properties.containsKey("sonar.surefire.reportsPath")
        !properties.containsKey("sonar.junit.reportsPath")
    }

    def "adds empty 'sonar.sources' property if no sources exist (because Sonar Runner 2.0 always expects this property to be set)"() {
        childProject2.pluginManager.apply(JavaPlugin)

        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties["sonar.sources"] == ""
        properties[":parent:child.sonar.sources"] == ""
        properties[":parent:child2.sonar.sources"] == ""
        properties[":parent:child.:parent:child:leaf.sonar.sources"] == ""
    }

    def "allows to configure Sonar properties via 'sonarqube' extension"() {
        parentProject.sonarqube.properties {
            property "sonar.some.key", "some value"
        }

        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties["sonar.some.key"] == "some value"
    }

    def "allows to configure project key via 'sonarqube' extension"() {
        parentProject.sonarqube.properties {
            property "sonar.projectKey", "myProject"
        }

        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties["sonar.projectKey"] == "myProject"
        properties[":parent:child.sonar.moduleKey"] == "myProject:parent:child"
    }

    def "prefixes property keys of subprojects"() {
        childProject.sonarqube.properties {
            property "sonar.some.key", "other value"
        }
        leafProject.sonarqube.properties {
            property "sonar.some.key", "other value leaf"
        }

        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties[":parent:child.sonar.some.key"] == "other value"
        properties[":parent:child.:parent:child:leaf.sonar.some.key"] == "other value leaf"
    }

    def "adds 'modules' properties declaring (prefixes of) subprojects"() {
        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties["sonar.modules"].contains(":parent:child")
        properties["sonar.modules"].contains(":parent:child2")
        properties[":parent:child.sonar.modules"] == ":parent:child:leaf"
        !properties.containsKey(":parent:child2.sonar.modules")
        !properties.containsKey(":parent:child:leaf.sonar.modules")
    }

    def "handles 'modules' properties correctly if plugin is applied to root project"() {
        def rootProject = ProjectBuilder.builder().withName("root").build()
        def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).build()
        def project2 = ProjectBuilder.builder().withName("parent2").withParent(rootProject).build()
        def childProject = ProjectBuilder.builder().withName("child").withParent(project).build()

        rootProject.pluginManager.apply(SonarQubePlugin)

        when:
        def properties = rootProject.tasks.sonarqube.properties

        then:
        properties["sonar.modules"] == ":parent,:parent2"
        properties[":parent.sonar.modules"] == ":parent:child"
        !properties.containsKey(":parent2.sonar.modules")
        !properties.containsKey(":parent:child.sonar.modules")

    }

    def "evaluates 'sonarqube' block lazily"() {
        parentProject.version = "1.0"
        parentProject.sonarqube.properties {
            property "sonar.projectVersion", parentProject.version
        }
        parentProject.version = "1.2.3"

        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties["sonar.projectVersion"] == "1.2.3"
    }

    def "converts SonarQube property values to strings"() {
        def object = new Object() {
            String toString() {
                "object"
            }
        }

        parentProject.sonarqube.properties {
            property "some.object", object
            property "some.list", [1, object, 2]
        }

        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties["some.object"] == "object"
        properties["some.list"] == "1,object,2"
    }

    def "removes SonarQube properties with null values"() {
        parentProject.sonarqube.properties {
            property "some.key", null
        }

        when:
        def properties = parentSonarQubeTask().properties

        then:
        !properties.containsKey("some.key")
    }

    def "allows to set SonarQube properties for target project via 'sonar.xyz' system properties"() {
        System.setProperty("sonar.some.key", "some value")
        System.setProperty("sonar.projectVersion", "3.2")

        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties["sonar.some.key"] == "some value"
        properties["sonar.projectVersion"] == "3.2"

        and:
        !properties.containsKey(":parent:child.sonar.some.key")
        properties[":parent:child.sonar.projectVersion"] == "1.3"
    }

    def "handles invalid java home"() {
        def rootProject = ProjectBuilder.builder().withName("root").build()
        def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).withProjectDir(new File("src/test/projects/java-project")).build()

        project.pluginManager.apply(SonarQubePlugin)
        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply(JacocoPlugin)

        project.sourceSets.main.java.srcDirs = ["src"]

        System.setProperty("java.home", "invalid")

        when:
        def properties = project.tasks.sonarqube.properties

        then:
        !properties.any { key, value -> value.contains("invalid") }
        !properties.any { key, value -> value.contains("rt.jar") }
    }

    def "handles system properties correctly if plugin is applied to root project"() {
        def rootProject = ProjectBuilder.builder().withName("root").build()
        def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).build()

        rootProject.allprojects { version = 1.3 }
        rootProject.pluginManager.apply(SonarQubePlugin)
        System.setProperty("sonar.some.key", "some value")
        System.setProperty("sonar.projectVersion", "3.2")

        when:
        def properties = rootProject.tasks.sonarqube.properties

        then:
        properties["sonar.some.key"] == "some value"
        properties["sonar.projectVersion"] == "3.2"

        and:
        !properties.containsKey(":parent.sonar.some.key")
        properties[":parent.sonar.projectVersion"] == "1.3"
    }

    def "system properties win over values set in build script"() {
        System.setProperty("sonar.some.key", "win")
        parentProject.sonarqube.properties {
            property "sonar.some.key", "lose"
        }

        when:
        def properties = parentSonarQubeTask().properties

        then:
        properties["sonar.some.key"] == "win"
    }

    def "doesn't add SonarQube properties for skipped child projects"() {
        childProject.sonarqube.skipProject = true

        when:
        def properties = parentSonarQubeTask().properties

        then:
        !properties.any { key, value -> key.startsWith("child.sonar.") }
        !properties.any { key, value -> key.startsWith(":parent:child.") }
    }

    def "doesn't add SonarQube properties for skipped projects"() {
        parentProject.sonarqube.skipProject = true

        when:
        def properties = parentSonarQubeTask().properties

        then:
        !properties.any { key, value -> key.startsWith("sonar.") }
        !properties.any { key, value -> key.startsWith(":parent.") }
    }

    private SonarQubeTask parentSonarQubeTask() {
        parentProject.tasks.sonarqube as SonarQubeTask
    }

}
