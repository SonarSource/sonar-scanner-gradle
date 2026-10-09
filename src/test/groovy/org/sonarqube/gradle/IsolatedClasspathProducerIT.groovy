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
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/** Checks ordering for classpath files produced by custom tasks under Isolated Projects. */
class IsolatedClasspathProducerIT extends Specification {

  @TempDir
  Path projectDir

  def 'resolver follows a selected transitive custom classpath producer with #outputKind output'() {
    given:
    TestKitGradleProperties.configure(projectDir)
    write('settings.gradle', "rootProject.name = 'custom-classpath'\n")
    write('build.gradle', '''
      plugins { id 'java-library'; id 'org.sonarqube' }

      def generatedJar = layout.buildDirectory.file('custom/generated.jar')
      def generateCustomClasspath = tasks.register('generateCustomClasspath') {
        OUTPUT_DECLARATION
        doLast {
          def jar = generatedJar.get().asFile
          jar.parentFile.mkdirs()
          new java.util.jar.JarOutputStream(new FileOutputStream(jar)).close()
        }
      }
      def customClasspath = tasks.register('customClasspath') {
        dependsOn(generateCustomClasspath)
      }
      sourceSets.main.compileClasspath += files(generatedJar).builtBy(customClasspath)
    '''.replace('OUTPUT_DECLARATION', outputDeclaration))
    write('src/main/java/Example.java', 'class Example {}')

    when:
    def result = runner(':generateCustomClasspath', ':sonarResolver', '--isolated-projects',
      '-Dorg.gradle.isolated-projects.diagnostics=true').build()
    def cachedResult = runner(':generateCustomClasspath', ':sonarResolver', '--isolated-projects',
      '-Dorg.gradle.isolated-projects.diagnostics=true').build()
    def resolverOnly = runner(':sonarResolver', '--isolated-projects').build()

    then:
    result.task(':generateCustomClasspath').outcome == SUCCESS
    result.task(':sonarResolver').outcome == SUCCESS
    !result.output.contains('isolated projects violation')
    result.tasks*.path.indexOf(':generateCustomClasspath') < result.tasks*.path.indexOf(':sonarResolver')
    cachedResult.output.contains('Reusing configuration cache')
    resolverOnly.task(':generateCustomClasspath') == null
    def resolverProperties = new JsonSlurper().parse(projectDir.resolve('build/sonar-resolver/properties').toFile())
    resolverProperties.compileClasspath.any {
      new File(it).canonicalFile == projectDir.resolve('build/custom/generated.jar').toFile().canonicalFile
    }

    where:
    outputKind   | outputDeclaration
    'file'       | 'outputs.file(generatedJar)'
    'directory'  | "outputs.dir(layout.buildDirectory.dir('custom'))"
  }

  private GradleRunner runner(String... arguments) {
    GradleRunner.create()
      .withGradleVersion('9.7.0')
      .withProjectDir(projectDir.toFile())
      .withPluginClasspath()
      .withEnvironment(System.getenv() + ['GRADLE_USER_HOME': projectDir.resolve('gradle-user-home').toString()])
      .withArguments(arguments)
  }

  private void write(String path, String content) {
    def file = projectDir.resolve(path)
    Files.createDirectories(file.parent)
    Files.writeString(file, content)
  }
}
