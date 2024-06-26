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
package org.sonarqube.gradle;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceCollectorTest {
  @TempDir
  Path emptyProjectBasedir;

  @TempDir
  static Path singleFileProjectBaseDir;

  @TempDir
  static Path simpleProjectBasedDir;

  @BeforeAll
  static void setup() throws IOException {
    Path singlePomXml = singleFileProjectBaseDir.resolve("pom.xml");
    singlePomXml.toFile().createNewFile();
  }

  @Test
  void testSourceCollectorBuilder() {
    SourceCollector.Builder sourceCollectorBuilder = SourceCollector.builder();
    assertThatThrownBy(sourceCollectorBuilder::build)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Root path must be set");
  }

  @Test
  void testPrevisitDirectories() throws IOException {
    Path src = createDirectory(simpleProjectBasedDir, "src");
    Path srcMain = createDirectory(src, "main");
    Path srcMainJava = createDirectory(srcMain, "java");
    Path srcMainJs = createDirectory(srcMain, "js");

    Set<Path> existingSources = Collections.singleton(srcMainJava);
    FileVisitor<Path> visitor = SourceCollector.builder()
      .setRoot(simpleProjectBasedDir)
      .setExistingSources(existingSources)
      .build();

    Path gitFolder = createDirectory(simpleProjectBasedDir, ".git");
    Path gitHooksFolder = createDirectory(gitFolder, "hooks");
    Path sources = createDirectory(simpleProjectBasedDir, "scripts");

    assertThat(visitor.preVisitDirectory(gitFolder, null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(gitHooksFolder, null)).isEqualTo(FileVisitResult.CONTINUE);

    assertThat(visitor.preVisitDirectory(srcMainJava, null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(createDirectory(simpleProjectBasedDir, "bin"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(createDirectory(simpleProjectBasedDir, "build"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(createDirectory(simpleProjectBasedDir, "target"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(createDirectory(simpleProjectBasedDir, "out"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(createDirectory(simpleProjectBasedDir, "tmp"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(createDirectory(simpleProjectBasedDir, "dist"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(createDirectory(simpleProjectBasedDir, "nbdist"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(createDirectory(simpleProjectBasedDir, "nbbuild"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);

    assertThat(visitor.preVisitDirectory(sources, null)).isEqualTo(FileVisitResult.CONTINUE);
    assertThat(visitor.preVisitDirectory(srcMainJs, null)).isEqualTo(FileVisitResult.CONTINUE);
  }

  @Test
  void visitorCollectsConsistently() throws IOException {
    // File in the existing source is not repeated in the collected files
    SourceCollector visitor = SourceCollector.builder().setRoot(emptyProjectBasedir).build();
    Files.walkFileTree(emptyProjectBasedir, visitor);
    assertThat(visitor.getCollectedSources()).isEmpty();

    SourceCollector otherVisitor = SourceCollector.builder().setRoot(singleFileProjectBaseDir).build();
    Files.walkFileTree(singleFileProjectBaseDir, otherVisitor);
    assertThat(otherVisitor.getCollectedSources()).containsOnly(singleFileProjectBaseDir.resolve("pom.xml"));

    SourceCollector visitorAvoidingPomXml = SourceCollector.builder()
      .setRoot(singleFileProjectBaseDir)
      .setExistingSources(Collections.singleton(singleFileProjectBaseDir.resolve("pom.xml")))
      .build();
    Files.walkFileTree(singleFileProjectBaseDir, visitorAvoidingPomXml);
    assertThat(visitorAvoidingPomXml.getCollectedSources()).isEmpty();
  }

  @Test
  void visitorIgnoresFilesInDirectoriesToIgnore() throws IOException {
    Path simpleProjectPom = createFile(simpleProjectBasedDir, "pom.xml");
    Path rootJavaFile = createFile(simpleProjectBasedDir, "ProjectRoot.java");
    Path subModule = createDirectory(simpleProjectBasedDir, "submodule");
    Path fileInSubModule = createFile(subModule, "ignore-me.php");

    SourceCollector visitor = SourceCollector.builder()
      .setRoot(simpleProjectBasedDir)
      .setDirectoriesToIgnore(Collections.singleton(subModule))
      .setShouldCollectJavaAndKotlinSources(true)
      .build();
    Files.walkFileTree(simpleProjectBasedDir, visitor);
    assertThat(visitor.getCollectedSources())
      .contains(simpleProjectPom)
      .contains(rootJavaFile)
      .doesNotContain(fileInSubModule);
  }

  @Test
  void visitorIgnoresJavaAndKotlinFiles() throws IOException {
    Path simpleProjectPom = createFile(simpleProjectBasedDir, "pom.xml");
    Path rootJavaFile = createFile(simpleProjectBasedDir, "ProjectRoot.java");
    Path rootKotlinFile = createFile(simpleProjectBasedDir, "ProjectRoot.kt");

    SourceCollector visitor = SourceCollector.builder().setRoot(simpleProjectBasedDir).build();
    Files.walkFileTree(simpleProjectBasedDir, visitor);
    assertThat(visitor.getCollectedSources())
      .contains(simpleProjectPom)
      .doesNotContain(rootJavaFile)
      .doesNotContain(rootKotlinFile);
  }

  @Test
  void visitorIgnoresExcludedFiles() throws IOException {
    Path pythonScript = simpleProjectBasedDir.resolve("run.py");
    pythonScript.toFile().createNewFile();
    Path cppFile = simpleProjectBasedDir.resolve("hello.cpp");
    cppFile.toFile().createNewFile();

    SourceCollector visitor = SourceCollector.builder().setRoot(simpleProjectBasedDir).setExcludedFiles(Set.of(cppFile)).setShouldCollectJavaAndKotlinSources(false).build();
    Files.walkFileTree(simpleProjectBasedDir, visitor);
    assertThat(visitor.getCollectedSources())
      .contains(pythonScript)
      .doesNotContain(cppFile);
  }

  @Test
  void visitorIgnoresSymbolicLinks() throws IOException {
    Path simpleProjectPom = simpleProjectBasedDir.resolve("pom.xml");
    simpleProjectPom.toFile().createNewFile();
    Path link = simpleProjectBasedDir.resolve("pom.xml.symbolic.link");
    Files.createSymbolicLink(link, simpleProjectPom);

    SourceCollector visitor = SourceCollector.builder().setRoot(simpleProjectBasedDir).build();
    Files.walkFileTree(simpleProjectBasedDir, visitor);
    assertThat(visitor.getCollectedSources())
      .contains(simpleProjectPom)
      .doesNotContain(link);
  }

  @Test
  void visitorCollectsExpectedHiddenFiles() throws IOException {
    Path hiddenDirectory = createDirectory(simpleProjectBasedDir, ".hidden");
    Path hiddenFile = createFile(hiddenDirectory, "file.txt");
    Path hiddenFile2 = createFile(hiddenDirectory, "configuration");
    Path hiddenFile3 = createFile(hiddenDirectory, "non-relevant-file");

    SourceCollector visitor = SourceCollector.builder().setRoot(simpleProjectBasedDir).build();
    Files.walkFileTree(simpleProjectBasedDir, visitor);
    assertThat(visitor.getCollectedSources())
      .contains(hiddenFile)
      .contains(hiddenFile2)
      .doesNotContain(hiddenFile3);
  }

  private Path createFile(Path parent, String name) throws IOException {
    Path file = parent.resolve(name);
    file.toFile().createNewFile();
    return file;
  }

  private Path createDirectory(Path parent, String name) {
    Path directory = parent.resolve(name);
    directory.toFile().mkdirs();
    return directory;
  }
}
