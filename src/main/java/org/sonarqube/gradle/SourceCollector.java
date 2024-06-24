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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class SourceCollector implements FileVisitor<Path> {
  private static final Set<String> EXCLUDED_DIRECTORIES = new HashSet<>(
    Arrays.asList(
      "bin",
      "build",
      "dist",
      "nbbuild",
      "nbdist",
      "out",
      "target",
      "tmp"
    )
  );

  private static final Set<String> EXCLUDED_EXTENSIONS_WITH_JAVA_AND_KOTLIN = Stream.of(
    ".jar",
    ".war",
    ".class",
    ".ear",
    ".nar",
    // Archives
    ".DS_Store",
    ".zip",
    ".7z",
    ".rar",
    ".gz",
    ".tar",
    ".xz",
    // log
    ".log",
    // temp files
    ".bak",
    ".tmp",
    ".swp",
    // ide files
    ".iml",
    ".ipr",
    ".iws",
    ".nib",
    ".log")
    .map(ext -> ext.toLowerCase(Locale.ROOT))
    .collect(Collectors.toSet());

  private static final Set<String> EXCLUDED_EXTENSIONS_WITHOUT_JAVA_AND_KOTLIN = Stream.concat(EXCLUDED_EXTENSIONS_WITH_JAVA_AND_KOTLIN.stream(), Stream.of(
    ".java",
    ".jav",
    ".kt")).map(ext -> ext.toLowerCase(Locale.ROOT))
    .collect(Collectors.toSet());

  private final Set<Path> existingSources;
  private final Set<Path> directoriesToIgnore;
  private final Set<Path> excludedFiles;
  private final Set<String> excludedExtensions;

  public Set<Path> getCollectedSources() {
    return collectedSources;
  }

  private final Set<Path> collectedSources = new HashSet<>();

  public SourceCollector(Set<Path> existingSources, Set<Path> directoriesToIgnore, Set<Path> excludedFiles, boolean shouldCollectJavaAndKotlinSources) {
    this.existingSources = existingSources;
    this.directoriesToIgnore = directoriesToIgnore;
    this.excludedFiles = excludedFiles;
    this.excludedExtensions = shouldCollectJavaAndKotlinSources ? EXCLUDED_EXTENSIONS_WITH_JAVA_AND_KOTLIN : EXCLUDED_EXTENSIONS_WITHOUT_JAVA_AND_KOTLIN;
  }

  @Override
  public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
    if (
      isHidden(path) ||
      isExcludedDirectory(path) ||
      isCoveredByExistingSources(path)
    ) {
      return FileVisitResult.SKIP_SUBTREE;
    }
    return FileVisitResult.CONTINUE;
  }

  private static boolean isHidden(Path path) {
    return StreamSupport.stream(path.spliterator(), true)
      .anyMatch(token -> token.toString().startsWith("."));
  }

  private boolean isExcludedDirectory(Path path) {
    String pathAsString = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return EXCLUDED_DIRECTORIES.contains(pathAsString) || directoriesToIgnore.contains(path);
  }

  private boolean isCoveredByExistingSources(Path path) {
    return existingSources.contains(path);
  }

  @Override
  public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) {
    if (!basicFileAttributes.isSymbolicLink() && !excludedFiles.contains(path) && existingSources.stream().noneMatch(path::equals)) {
      String lowerCaseFileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
      if (excludedExtensions.stream().noneMatch(lowerCaseFileName::endsWith)) {
        collectedSources.add(path);
      }
    }
    return FileVisitResult.CONTINUE;
  }

  @Override
  public FileVisitResult visitFileFailed(Path path, IOException e) throws IOException {
    return null;
  }

  @Override
  public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {
    return FileVisitResult.CONTINUE;
  }
}
