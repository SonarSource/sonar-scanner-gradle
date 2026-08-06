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

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class SonarResolverTaskTest extends Specification {

  @TempDir
  Path projectDir

  def "tracked classpaths retain producer dependencies while filtering missing entries"() {
    given:
    def project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
    def compileProducer = project.tasks.register("compileProducer")
    def testProducer = project.tasks.register("testProducer")
    def existingCompileEntry = Files.createFile(projectDir.resolve("compile-entry.jar")).toFile()
    def missingCompileEntry = projectDir.resolve("missing-compile-entry.jar").toFile()
    def existingTestEntry = Files.createFile(projectDir.resolve("test-entry.jar")).toFile()
    def missingTestEntry = projectDir.resolve("missing-test-entry.jar").toFile()
    def compileClasspath = project.files(existingCompileEntry, missingCompileEntry).builtBy(compileProducer)
    def testCompileClasspath = project.files(existingTestEntry, missingTestEntry).builtBy(testProducer)
    def task = project.tasks.create(SonarResolverTask.TASK_NAME, SonarResolverTask)

    when:
    task.setCompileClasspath(project.provider { compileClasspath })
    task.setTestCompileClasspath(project.provider { testCompileClasspath })

    then:
    task.compileClasspath.files == [existingCompileEntry] as Set
    task.testCompileClasspath.files == [existingTestEntry] as Set
    task.taskDependencies.getDependencies(task).containsAll(compileProducer.get(), testProducer.get())
  }

  def "run skips file collection inputs that fail to resolve"() {
    given:
    def project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
    def task = project.tasks.create(SonarResolverTask.TASK_NAME, SonarResolverTask)
    task.projectName.set(":")
    task.topLevelProject.set(true)
    task.skipProject.set(false)
    task.setCompileClasspath(project.provider { project.files() })
    task.setTestCompileClasspath(project.provider { project.files() })
    task.setOutputDirectory(projectDir.resolve("sonar-resolver").toFile())
    def testLibrary = Files.createFile(projectDir.resolve("test-library.jar")).toFile()
    task.mainLibraries.from(project.provider { throw new RuntimeException("cannot resolve main libraries") })
    task.testLibraries.from(testLibrary)

    when:
    task.run()

    then:
    noExceptionThrown()
    def properties = ResolutionSerializer.read(task.outputFile).get()
    properties.mainLibraries.isEmpty()
    properties.testLibraries == [testLibrary.absolutePath]
  }
}
