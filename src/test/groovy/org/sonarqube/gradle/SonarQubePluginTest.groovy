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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.initialization.GradlePropertiesController
import org.gradle.internal.impldep.org.apache.commons.lang.SystemUtils
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import spock.lang.Specification

import java.nio.file.Paths
import java.util.stream.Collectors

import static org.assertj.core.api.Assertions.assertThat
import static org.hamcrest.Matchers.contains
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when
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
    (parentProject as ProjectInternal).services.get(GradlePropertiesController.class).loadGradlePropertiesFrom(parentProject.rootDir)

    rootProject.allprojects {
      group = "group"
      version = 1.3
      description = "description"
      buildDir = "buildDir"
    }
  }

  def "adds a sonar extension to the target project (i.e. the project to which the plugin is applied) and its subprojects"() {
    expect:
    rootProject.extensions.findByName("sonar") == null
    parentProject.extensions.findByName("sonar") instanceof SonarExtension
    childProject.extensions.findByName("sonar") instanceof SonarExtension
  }

  def "adds a sonar extension to each project once in multimodule projects"() {
    when:
    parentProject.allprojects { pluginManager.apply(SonarQubePlugin) }

    then:
    rootProject.extensions.findByName("sonar") == null
    parentProject.extensions.findByName("sonar") instanceof SonarExtension
    childProject.extensions.findByName("sonar") instanceof SonarExtension
  }

  def "adds a sonar task to the target project"() {
    expect:
    parentProject.tasks.findByName("sonar") instanceof SonarTask
    parentSonarTask().group == JavaBasePlugin.VERIFICATION_GROUP
    parentSonarTask().description == "Analyzes project ':parent' and its subprojects with Sonar."

    childProject.tasks.findByName("sonar") == null
  }

  def "adds a sonarqube deprecated task to the target project"() {
    expect:
    parentProject.tasks.findByName("sonarqube") instanceof SonarTask
    (parentProject.tasks.sonarqube as SonarTask).group == JavaBasePlugin.VERIFICATION_GROUP
    (parentProject.tasks.sonarqube as SonarTask).description == "Analyzes project ':parent' and its subprojects with Sonar. This task is deprecated. Use 'sonar' instead."
  }

  def "sets log output level"() {
    when:
    parentSonarTask().useLoggerLevel(LogLevel.DEBUG)

    then:
    parentSonarTask().getLogOutput().getClass().getSimpleName() == "LifecycleLogOutput"
  }

  def "skipping all projects does nothing"() {
    when:
    parentProject.allprojects { sonar { skipProject = true } }
    parentSonarTask().run()
    def properties = parentSonarTask().properties.get()

    then:
    noExceptionThrown()
    properties.isEmpty()
  }

  def "skipping all projects with old extension does nothing"() {
    when:
    parentProject.allprojects { sonarqube { skipProject = true } }
    parentSonarTask().run()
    def properties = parentSonarTask().properties.get()

    then:
    noExceptionThrown()
    properties.isEmpty()
  }

  def "adds a sonar task once in multimodule projects"() {
    when:
    parentProject.allprojects { pluginManager.apply(SonarQubePlugin) }

    then:
    parentProject.tasks.findByName("sonar") instanceof SonarTask
    parentSonarTask().description == "Analyzes project ':parent' and its subprojects with Sonar."

    childProject.tasks.findByName("sonar") == null
  }

  def "makes sonar task must run after compile and test tasks of the target project and its subprojects"() {
    when:
    rootProject.pluginManager.apply(JavaPlugin)
    parentProject.pluginManager.apply(JavaPlugin)
    childProject.pluginManager.apply(JavaPlugin)

    then:
    mustRunAfterTasks(parentSonarTask()).containsAll([
      "parent:compileJava",
      "parent:compileTestJava",
      "child:compileJava",
      "child:compileTestJava",
      "parent:test",
      "child:test",
    ])
  }

  def "doesn't make sonar task depend on test task of skipped projects"() {
    when:
    rootProject.pluginManager.apply(JavaPlugin)
    parentProject.pluginManager.apply(JavaPlugin)
    childProject.pluginManager.apply(JavaPlugin)
    childProject.sonar.skipProject = true

    then:
    mustRunAfterTasks(parentSonarTask()).containsAll([
      "parent:compileJava",
      "parent:compileTestJava",
      "parent:test",
    ])
  }

  def "adds default properties for target project and its subprojects"() {
    when:
    def properties = parentSonarTask().properties.get()

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
    def properties = parentSonarTask().properties.get()

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
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.projectKey"] == "root:parent"
  }

  def "compute source and target properties for 'java' projects"() {
    parentProject.pluginManager.apply(JavaPlugin)
    childProject.pluginManager.apply(JavaPlugin)
    parentProject.sourceCompatibility = 1.5
    parentProject.targetCompatibility = 1.6
    childProject.sourceCompatibility = 1.6
    childProject.targetCompatibility = 1.8

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.java.source"] == "1.5"
    properties["sonar.java.target"] == "1.6"
    properties[":parent:child.sonar.java.source"] == "1.6"
    properties[":parent:child.sonar.java.target"] == "1.8"
  }

  def "compute source and target properties for 'java' projects from release"() {
    parentProject.pluginManager.apply(JavaPlugin)
    childProject.pluginManager.apply(JavaPlugin)
    parentProject.compileJava.options.release = 8
    childProject.compileJava.options.release = 8

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.java.source"] == "8"
    properties["sonar.java.target"] == "8"
    properties[":parent:child.sonar.java.source"] == "8"
    properties[":parent:child.sonar.java.target"] == "8"
  }

  def "enable preview"() {
    parentProject.pluginManager.apply(JavaPlugin)
    parentProject.compileJava.options.compilerArgs.add("--enable-preview")

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.java.enablePreview"] == "true"
  }

  def "disable preview"() {
    parentProject.pluginManager.apply(JavaPlugin)

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.java.enablePreview"] == "false"
  }

  def "compute source and target properties for 'groovy' projects"() {
    parentProject.pluginManager.apply(GroovyPlugin)
    childProject.pluginManager.apply(GroovyPlugin)
    parentProject.sourceCompatibility = 1.5
    parentProject.targetCompatibility = 1.6
    childProject.sourceCompatibility = 1.6
    childProject.targetCompatibility = 1.8

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.java.source"] == "1.5"
    properties["sonar.java.target"] == "1.6"
    properties[":parent:child.sonar.java.source"] == "1.6"
    properties[":parent:child.sonar.java.target"] == "1.8"
  }

  def "adds additional default properties for 'java' projects"() {
    def rootProject = ProjectBuilder.builder().withName("root").build()
    def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).withProjectDir(new File("src/test/projects/java-project")).build()

    project.pluginManager.apply(SonarQubePlugin)

    project.pluginManager.apply(JavaPlugin)

    project.sourceSets.main.java.srcDirs = ["src"]
    project.sourceSets.test.java.srcDirs = ["test"]

    setOutputDirs(project, "out", "test-out");
    project.sourceSets.main.compileClasspath += project.files("lib/SomeLib.jar")
    project.sourceSets.test.compileClasspath += project.files("lib/junit.jar")
    project.compileJava.options.encoding = 'ISO-8859-1'

    def testResultsDir = new File(project.buildDir, "test-results/test")
    testResultsDir.mkdirs()
    new File(testResultsDir, 'TEST-.xml').createNewFile()

    when:
    def properties = project.tasks.sonar.properties.get()

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
    def properties = project.tasks.sonar.properties.get()

    then:
    !properties.containsKey("sonar.jacoco.reportPath")
    !properties.containsKey("sonar.jacoco.reportPaths")
  }

  def "adds additional default properties for 'groovy' projects"() {
    def rootProject = ProjectBuilder.builder().withName("root").build()
    def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).withProjectDir(new File("src/test/projects/java-project")).build()

    project.pluginManager.apply(SonarQubePlugin)

    project.pluginManager.apply(GroovyPlugin)

    project.sourceSets.main.groovy.srcDirs = ["src"]
    project.sourceSets.test.groovy.srcDirs = ["test"]
    project.sourceSets.main.output.classesDirs.setFrom(new File(project.buildDir, "out"))
    setOutputDirs(project, "out", "test-out");
    project.sourceSets.main.compileClasspath += project.files("lib/SomeLib.jar")
    project.sourceSets.test.compileClasspath += project.files("lib/junit.jar")
    project.compileJava.options.encoding = 'ISO-8859-1'

    def testResultsDir = new File(project.buildDir, "test-results/test")
    testResultsDir.mkdirs()
    new File(testResultsDir, 'TEST-.xml').createNewFile()

    when:
    def properties = project.tasks.sonar.properties.get()

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

  def "properties with list of file path should be escaped correctly"() {
    Assumptions.assumeFalse(SystemUtils.IS_OS_WINDOWS)
    def rootProject = ProjectBuilder.builder().withName("root").build()
    def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).withProjectDir(new File("src/test/projects/java-escaped-project")).build()

    project.pluginManager.apply(SonarQubePlugin)

    project.pluginManager.apply(GroovyPlugin)

    project.sourceSets.main.groovy.srcDirs = ["sr,c"]
    project.sourceSets.main.groovy.srcDirs += ["src"]
    project.sourceSets.test.groovy.srcDirs = ["tes,t"]
    project.sourceSets.test.groovy.srcDirs += ["test"]
    project.sourceSets.main.output.classesDirs.setFrom(new File(project.buildDir, "comma,out"))
    setOutputDirs(project, "comma,out", "test-out");
    project.sourceSets.main.compileClasspath += project.files("lib/comma,lib.jar")
    project.sourceSets.main.compileClasspath += project.files("lib/comma,quote\"lib.jar")
    project.sourceSets.main.compileClasspath += project.files("lib/otherLib.jar")


    project.sourceSets.test.compileClasspath += project.files("lib/junit.jar")
    project.sourceSets.test.compileClasspath += project.files("lib/comma,junit.jar")
    project.compileJava.options.encoding = 'ISO-8859-1'

    def testResultsDir = new File(project.buildDir, "test-results/test")
    testResultsDir.mkdirs()
    new File(testResultsDir, 'TEST-.xml').createNewFile()

    //File is created on the fly as double quotes are not supported on windows
    new File(project.projectDir, "lib/comma,quote\"lib.jar").createNewFile()

    when:
    Map<String, String> properties = (Map<String, String>) project.tasks.sonar.properties.get()

    then:
    valueShouldBeEscaped(properties["sonar.sources"], new File(project.projectDir, "sr,c") as String)
    valueShouldNotBeEscaped(properties["sonar.sources"], new File(project.projectDir, "src") as String)
    valueShouldBeEscaped(properties["sonar.tests"], new File(project.projectDir, "tes,t") as String)
    valueShouldNotBeEscaped(properties["sonar.tests"], new File(project.projectDir, "test") as String)
    valueShouldBeEscaped(properties["sonar.java.binaries"], new File(project.buildDir, "comma,out") as String)
    valueShouldBeEscaped(properties["sonar.groovy.binaries"], new File(project.buildDir, "comma,out") as String)
    valueShouldBeEscaped(properties["sonar.java.libraries"], new File(project.projectDir, "lib/comma,lib.jar") as String)
    valueShouldBeEscaped(properties["sonar.java.libraries"], new File(project.projectDir, "lib/comma,quote\"lib.jar") as String)
    valueShouldNotBeEscaped(properties["sonar.java.libraries"], new File(project.projectDir, "lib/otherLib.jar") as String)
    valueShouldNotBeEscaped(properties["sonar.java.test.binaries"], new File(project.buildDir, "test-out") as String)
    valueShouldNotBeEscaped(properties["sonar.java.test.libraries"], new File(project.projectDir, "lib/junit.jar") as String)
    valueShouldBeEscaped(properties["sonar.java.test.libraries"], new File(project.projectDir, "lib/comma,junit.jar") as String)
    valueShouldBeEscaped(properties["sonar.binaries"], new File(project.buildDir, "comma,out") as String)
    valueShouldBeEscaped(properties["sonar.libraries"], new File(project.projectDir, "lib/comma,lib.jar") as String)


    properties["sonar.surefire.reportsPath"] == new File(project.buildDir, "test-results/test") as String
    properties["sonar.junit.reportsPath"] == new File(project.buildDir, "test-results/test") as String
    properties["sonar.junit.reportPaths"] == new File(project.buildDir, "test-results/test") as String
    properties["sonar.sourceEncoding"] == "ISO-8859-1"
  }

  private static void valueShouldBeEscaped(Object propertiesList, String propertyValue) {
    Assertions.assertTrue(((String) propertiesList).contains(getEscapedValue(propertyValue)));
  }

  private static void valueShouldNotBeEscaped(Object propertiesList, String propertyValue) {
    Assertions.assertFalse(((String) propertiesList).contains(getEscapedValue(propertyValue)));
    Assertions.assertTrue(((String) propertiesList).contains(propertyValue));
  }

  private static String getEscapedValue(String propertyValue) {
    "\"" + propertyValue.replace("\"", "\\\"") + "\""
  }

  def "adds coverage properties for 'groovy' projects"() {
    def rootProject = ProjectBuilder.builder().withName("root").build()
    def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).withProjectDir(new File("src/test/projects/java-project")).build()

    project.pluginManager.apply(SonarQubePlugin)
    project.pluginManager.apply(GroovyPlugin)
    project.pluginManager.apply(JacocoPlugin)

    project.sourceSets.main.java.srcDirs = ["src"]

    when:
    def properties = project.tasks.sonar.properties.get()

    then:
    !properties.containsKey("sonar.groovy.jacoco.reportPath")
  }

  def "only adds existing directories"() {
    parentProject.pluginManager.apply(JavaPlugin)

    when:
    def properties = parentSonarTask().properties.get()

    then:
    !properties.containsKey("sonar.tests")
    !properties.containsKey("sonar.surefire.reportsPath")
    !properties.containsKey("sonar.junit.reportsPath")
  }

  def "adds empty 'sonar.sources' property if no sources exist (because Sonar Runner 2.0 always expects this property to be set)"() {
    childProject2.pluginManager.apply(JavaPlugin)

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.sources"] == ""
    properties[":parent:child.sonar.sources"] == ""
    properties[":parent:child2.sonar.sources"] == ""
    properties[":parent:child.:parent:child:leaf.sonar.sources"] == ""
  }

  def "allows to configure Sonar properties via 'sonar' extension"() {
    parentProject.sonar.properties {
      property "sonar.some.key", "some value"
    }

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.some.key"] == "some value"
  }

  def "allows to configure Sonar properties via deprecated 'sonarqube' extension"() {
    parentProject.sonarqube.properties {
      property "sonar.some.key", "some value"
    }

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.some.key"] == "some value"
  }

  def "allows to configure project key via 'sonar' extension"() {
    parentProject.sonar.properties {
      property "sonar.projectKey", "myProject"
    }

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.projectKey"] == "myProject"
    properties[":parent:child.sonar.moduleKey"] == "myProject:parent:child"
  }

  def "prefixes property keys of subprojects"() {
    childProject.sonar.properties {
      property "sonar.some.key", "other value"
    }
    leafProject.sonar.properties {
      property "sonar.some.key", "other value leaf"
    }

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties[":parent:child.sonar.some.key"] == "other value"
    properties[":parent:child.:parent:child:leaf.sonar.some.key"] == "other value leaf"
  }

  def "adds 'modules' properties declaring (prefixes of) subprojects"() {
    when:
    def properties = parentSonarTask().properties.get()

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
    def properties = rootProject.tasks.sonar.properties.get()

    then:
    properties["sonar.modules"] == ":parent,:parent2"
    properties[":parent.sonar.modules"] == ":parent:child"
    !properties.containsKey(":parent2.sonar.modules")
    !properties.containsKey(":parent:child.sonar.modules")

  }

  def "evaluates 'sonar' block lazily"() {
    parentProject.version = "1.0"
    parentProject.sonar.properties {
      property "sonar.projectVersion", parentProject.version
    }
    parentProject.version = "1.2.3"

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.projectVersion"] == "1.2.3"
  }

  def "converts sonar property values to strings"() {
    def object = new Object() {
      String toString() {
        "object"
      }
    }

    parentProject.sonar.properties {
      property "some.object", object
      property "some.list", [1, object, 2]
    }

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["some.object"] == "object"
    properties["some.list"] == "1,object,2"
  }

  def "removes sonar properties with null values"() {
    parentProject.sonar.properties {
      property "some.key", null
    }

    when:
    def properties = parentSonarTask().properties.get()

    then:
    !properties.containsKey("some.key")
  }

  def "allows to set sonar properties for target project via 'sonar.xyz' system properties"() {
    System.setProperty("sonar.some.key", "some value")
    System.setProperty("sonar.projectVersion", "3.2")

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.some.key"] == "some value"
    properties["sonar.projectVersion"] == "3.2"

    and:
    !properties.containsKey(":parent:child.sonar.some.key")
    properties[":parent:child.sonar.projectVersion"] == "1.3"
  }

  def "handles system properties correctly if plugin is applied to root project"() {
    def rootProject = ProjectBuilder.builder().withName("root").build()
    def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).build()

    rootProject.allprojects { version = 1.3 }
    rootProject.pluginManager.apply(SonarQubePlugin)
    System.setProperty("sonar.some.key", "some value")
    System.setProperty("sonar.projectVersion", "3.2")

    when:
    def properties = rootProject.tasks.sonar.properties.get()

    then:
    properties["sonar.some.key"] == "some value"
    properties["sonar.projectVersion"] == "3.2"

    and:
    !properties.containsKey(":parent.sonar.some.key")
    properties[":parent.sonar.projectVersion"] == "1.3"
  }

  def "system properties win over values set in build script"() {
    System.setProperty("sonar.some.key", "win")
    parentProject.sonar.properties {
      property "sonar.some.key", "lose"
    }

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.some.key"] == "win"
  }

  def "doesn't add sonar properties for skipped child project when using old sonarqube task"() {
    childProject2.sonarqube.skipProject = true

    when:
    def properties = parentSonarTask().properties.get()

    then:
    !properties.any { key, value -> value.contains("child2") }
  }

  def "doesn't add sonar properties for skipped child projects"() {
    childProject.sonar.skipProject = true

    when:
    def properties = parentSonarTask().properties.get()

    then:
    !properties.any { key, value -> key.startsWith("child.sonar.") }
    !properties.any { key, value -> key.startsWith(":parent:child.") }
  }

  def "doesn't add sonar properties for skipped projects"() {
    parentProject.sonar.skipProject = true

    when:
    def properties = parentSonarTask().properties.get()

    then:
    !properties.any { key, value -> key.startsWith("sonar.") }
    !properties.any { key, value -> key.startsWith(":parent.") }
  }

  def "set jdkHome for 'java' projects"() {
    parentProject.pluginManager.apply(JavaPlugin)

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.java.jdkHome"] != null
  }

  def "set jdkHome for 'groovy' projects"() {
    parentProject.pluginManager.apply(GroovyPlugin)

    when:
    def properties = parentSonarTask().properties.get()

    then:
    properties["sonar.java.jdkHome"] != null
  }

  def JVM_SOURCE_FILE_JAVA = normalizePathString("src/test/projects/kotlin-multiplatform-project/src/jvmMain/java/me/user/application/Sample.java")
  def JVM_SOURCE_FILE_JAVA_TEST = normalizePathString("src/test/projects/kotlin-multiplatform-project/src/jvmTest/java/me/user/application/SampleTest.java")
  def JVM_SOURCE_FILE_KOTLIN = normalizePathString("src/test/projects/kotlin-multiplatform-project/src/jvmMain/kotlin/me.user.application/Sample.kt")
  def JVM_SOURCE_FILE_JS = normalizePathString("src/test/projects/kotlin-multiplatform-project/src/jsMain/kotlin/Sample.js")

  def "add Kotlin multiplatform sources"() {
    def rootProject = ProjectBuilder.builder().withName("root").build()
    def project = ProjectBuilder.builder().withName("parent")
      .withParent(rootProject)
      .withProjectDir(new File("src/test/projects/kotlin-multiplatform-project"))
      .build()

    setupKotlinMultiplatformExtension(project)
    project.pluginManager.apply(SonarQubePlugin)

    when:
    def properties = project.tasks.sonar.properties.get()

    then:
    def sonarSources = properties["sonar.sources"].split(",")
    assertThat(normalizePathArray(sonarSources))
      .containsExactlyInAnyOrder(JVM_SOURCE_FILE_KOTLIN, JVM_SOURCE_FILE_JAVA, JVM_SOURCE_FILE_JS)
    def sonarTestSources = properties["sonar.tests"].split(",")
    assertThat(normalizePathArray(sonarTestSources))
      .containsExactlyInAnyOrder(JVM_SOURCE_FILE_JAVA_TEST)

    properties["sonar.java.binaries"] == null
    properties["sonar.java.libraries"] == null
  }

  def "add Kotlin multiplatform sources with Java libraries and binaries"() {
    def rootProject = ProjectBuilder.builder().withName("root").build()
    def project = ProjectBuilder.builder().withName("parent")
      .withParent(rootProject)
      .withProjectDir(new File("src/test/projects/kotlin-multiplatform-project")).build()

    setupKotlinMultiplatformExtension(project)
    project.pluginManager.apply(SonarQubePlugin)
    project.pluginManager.apply(JavaPlugin)

    project.sourceSets.main.java.srcDirs = ["src"]
    project.sourceSets.test.java.srcDirs = ["test"]
    setOutputDirs(project, "out", "test-out");
    project.sourceSets.main.compileClasspath += project.files("lib/SomeLib.jar")
    project.sourceSets.test.compileClasspath += project.files("lib/junit.jar")
    project.compileJava.options.encoding = 'ISO-8859-1'

    when:
    def properties = project.tasks.sonar.properties.get()

    then:
    def sonarSources = properties["sonar.sources"].split(",")
    assertThat(normalizePathArray(sonarSources))
      .containsExactlyInAnyOrder(JVM_SOURCE_FILE_KOTLIN, JVM_SOURCE_FILE_JAVA, JVM_SOURCE_FILE_JS)

    properties["sonar.java.libraries"].contains(new File(project.projectDir, "lib/SomeLib.jar") as String)
    properties["sonar.java.binaries"].contains(new File(project.buildDir, "out") as String)
  }

  def "handles root project property correctly if plugin is applied to root project"() {
    def rootProject = ProjectBuilder.builder().withName("root").build()
    def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).build()

    rootProject.allprojects { version = 1.3 }
    rootProject.pluginManager.apply(SonarQubePlugin)

    when:
    def properties = rootProject.tasks.sonar.properties.get()

    then:
    properties["sonar.kotlin.gradleProjectRoot"] == rootProject.projectDir as String
  }

  def "handles root project property correctly if plugin is applied to the subproject"() {
    def rootProject = ProjectBuilder.builder().withName("root").build()
    def project = ProjectBuilder.builder().withName("parent").withParent(rootProject).build()

    rootProject.allprojects { version = 1.3 }
    project.pluginManager.apply(SonarQubePlugin)

    when:
    def properties = project.tasks.sonar.properties.get()

    then:
    properties["sonar.kotlin.gradleProjectRoot"] == rootProject.projectDir as String
  }

  def "Do not add implicit compile dependencies"() {
    def rootProject = ProjectBuilder.builder().withName("root").build()
    rootProject.pluginManager.apply(JavaPlugin)

    when:
    rootProject.pluginManager.apply(SonarQubePlugin)

    then:
    def sonarTask = rootProject.tasks.sonar

    dependsOnTasks(sonarTask) == []
    mustRunAfterTasks(sonarTask).size() == 3
    mustRunAfterTasks(sonarTask).containsAll(["root:test", "root:compileJava", "root:compileTestJava"])
  }

  private List<String> mustRunAfterTasks(Task sonarTask) {
    sonarTask.getMustRunAfter().getDependencies(sonarTask)
      .stream()
      .map(t -> (t as Task))
      .map(t -> t.project.name + ":" + t.name)
      .collect(Collectors.toList())
  }

  private List<String> dependsOnTasks(Task sonarTask) {
    sonarTask.getTaskDependencies().getDependencies(sonarTask)
      .stream().map(t -> (t as Task).name)
      .collect(Collectors.toList())
  }

  private void setupKotlinMultiplatformExtension(Project project) {
    def jvMainSourceSet = mockKotlinSourceSet("jvmMain", Set.of(new File(JVM_SOURCE_FILE_JAVA), new File(JVM_SOURCE_FILE_KOTLIN)))
    def jvTestSourceSet = mockKotlinSourceSet("jvmTest", Set.of(new File(JVM_SOURCE_FILE_JAVA_TEST)))
    def jsMainSourceSet = mockKotlinSourceSet("jsMain", Set.of(new File(JVM_SOURCE_FILE_JS)))

    def kotlinSourceSetContainer = mock(NamedDomainObjectContainer<KotlinSourceSet>.class)
    // return a new stream on each invocation
    when(kotlinSourceSetContainer.stream()).then(invocation -> Arrays.stream(jvMainSourceSet, jvTestSourceSet, jsMainSourceSet))

    def multiplatformExtension = mock(KotlinMultiplatformExtension.class)
    when(multiplatformExtension.getSourceSets()).thenReturn(kotlinSourceSetContainer)

    project.extensions.add("kotlin", multiplatformExtension)
  }

  private KotlinSourceSet mockKotlinSourceSet(String name, Set<File> srcFiles) {
    def sourceDirectorySet = mock(SourceDirectorySet.class)
    when(sourceDirectorySet.getSrcDirs()).thenReturn(srcFiles)

    def kotlinSourceSet = mock(KotlinSourceSet)
    when(kotlinSourceSet.getName()).thenReturn(name)
    when(kotlinSourceSet.getKotlin()).thenReturn(sourceDirectorySet)
    return kotlinSourceSet
  }

  private SonarTask parentSonarTask() {
    parentProject.tasks.sonar as SonarTask
  }

  private void setOutputDirs(Project project, String mainDir, String testDir) {
    if (project.sourceSets.main.java.hasProperty("outputDir")) {
      //for Gradle 7 and lower compatibility
      project.sourceSets.main.java.outputDir = new File(project.buildDir, mainDir)
      project.sourceSets.test.java.outputDir = new File(project.buildDir, testDir)
    } else {
      project.sourceSets.main.java.destinationDirectory = new File(project.buildDir, mainDir)
      project.sourceSets.test.java.destinationDirectory = new File(project.buildDir, testDir)
    }
  }

  def "multi module project build files should be inside root sonar.sources"() {
    def parent = ProjectBuilder.builder().withName("java-multi-module")
      .withProjectDir(new File("src/test/projects/java-multi-module")).build()
    def module1 = ProjectBuilder.builder().withName("module1")
      .withProjectDir(new File("src/test/projects/java-multi-module/module1"))
      .withParent(parent)
      .build()
    def module2 = ProjectBuilder.builder().withName("module2")
      .withProjectDir(new File("src/test/projects/java-multi-module/module2"))
      .withParent(parent)
      .build()
    parent.pluginManager.apply(JavaPlugin)
    parent.pluginManager.apply(SonarQubePlugin)
    def props = parent.tasks.sonar.properties.get()
    when:
    def parentSources = props["sonar.sources"].split(",")
    def module1Sources = props[":module1.sonar.sources"]
    def module2Sources = props[":module2.sonar.sources"]
    then:
    assert parentSources.size() == 4
    assert normalizePathArray(parentSources).contains(normalizePathString("src/test/projects/java-multi-module/build.gradle.kts"))
    assert normalizePathArray(parentSources).contains(normalizePathString("src/test/projects/java-multi-module/settings.gradle.kts"))
    assert normalizePathArray(parentSources).contains(normalizePathString("src/test/projects/java-multi-module/module1/build.gradle.kts"))
    assert normalizePathArray(parentSources).contains(normalizePathString("src/test/projects/java-multi-module/module2/build.gradle.kts"))
    assert module1Sources.length() == 0
    assert module2Sources.length() == 0
  }

  private List<String> normalizePathArray(String[] pathStrings) {
    return Arrays.stream(pathStrings)
      .map(this::normalizePathString)
      .collect(Collectors.toList())
  }

  private String normalizePathString(String pathString) {
    return Paths.get(pathString).normalize().toAbsolutePath();
  }

  def "avoid nested paths inside sonar.sources"() {
    def dir = new File("src/test/projects/java-nested-sources")
    def project = ProjectBuilder.builder()
      .withName("java-nested-sources")
      .withProjectDir(dir)
      .build()
    project.pluginManager.apply(JavaPlugin)

    JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class)
    SourceSetContainer sourceSets = javaExtension.getSourceSets();
    SourceSet mainSourceSet = sourceSets.getByName("main");
    mainSourceSet.getJava().srcDir("src/main/java/pck");
    mainSourceSet.getJava().srcDir("src/main/java/pck2");
    project.pluginManager.apply(SonarQubePlugin)

    when:
    def properties = project.tasks.sonar.properties.get()
    def sources = properties["sonar.sources"].split(",")
    def srcMainJavaPath = normalizePathString((dir as String) + "/src/main/java")
    def buildFilePath = normalizePathString((dir as String) + "/build.gradle.kts")

    then:
    assert sources.size() == 2
    assert normalizePathArray(sources).contains(buildFilePath)
    assert normalizePathArray(sources).contains(srcMainJavaPath)

  }

}
