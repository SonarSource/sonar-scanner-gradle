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
      "tmp",
      ".gradle",
      ".git",
      ".npm",
      ".venv",
      ".cache",
      ".env",
      ".jruby",
      ".m2",
      ".node_modules",
      ".pycache",
      ".pytest_cache"
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

  private static final Set<String> INCLUDE_EXTENSIONS_FOR_HIDDEN_FILES = Set.of(
    ".env",
    ".json",
    ".yml", ".yaml",
    ".properties",
    ".db",
    ".htpasswd",
    ".xml",
    ".sh", ".bash", ".ksh", ".zsh", ".bat", ".ps1",
    ".txt",
    ".config",
    ".settings",
    ".cnf"
  );

  private static final Set<String> INCLUDE_HIDDEN_FILES_KEYWORDS = Set.of(
    "config", "cfg",
    "credential",
    "token",
    "secret",
    "private",
    "access",
    "password", "pwd",
    "key",
    ".env.",
    "history",
    "sessions",
    "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519"
  );

  private final Path root;
  private final Set<Path> existingSources;
  private final Set<Path> directoriesToIgnore;
  private final Set<Path> excludedFiles;
  private final Set<String> excludedExtensions;

  public Set<Path> getCollectedSources() {
    return collectedSources;
  }

  private final Set<Path> collectedSources = new HashSet<>();

  public static Builder builder() {
    return new Builder();
  }

  private SourceCollector(Path root, Set<Path> existingSources, Set<Path> directoriesToIgnore, Set<Path> excludedFiles, boolean shouldCollectJavaAndKotlinSources) {
    this.root = root;
    this.existingSources = existingSources;
    this.directoriesToIgnore = directoriesToIgnore;
    this.excludedFiles = excludedFiles;
    this.excludedExtensions = shouldCollectJavaAndKotlinSources ? EXCLUDED_EXTENSIONS_WITH_JAVA_AND_KOTLIN : EXCLUDED_EXTENSIONS_WITHOUT_JAVA_AND_KOTLIN;
  }

  @Override
  public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) {
    boolean isHiddenAndTooFarDownTheTree = isHidden(path) && !isChildOrGrandChildOfRoot(path);

    if (isHiddenAndTooFarDownTheTree || isExcludedDirectory(path) || isCoveredByExistingSources(path)) {
      return FileVisitResult.SKIP_SUBTREE;
    }
    return FileVisitResult.CONTINUE;
  }

  private boolean isHidden(Path path) {
    return StreamSupport.stream(path.spliterator(), true)
      .anyMatch(token -> token.toString().startsWith("."));
  }

  private boolean isChildOrGrandChildOfRoot(Path path) {
    return root.equals(path.getParent()) || root.equals(path.getParent().getParent());
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

      boolean isHidden = isHidden(path);
      boolean isHiddenFileToCollect = INCLUDE_HIDDEN_FILES_KEYWORDS.stream().anyMatch(lowerCaseFileName::contains)
        || INCLUDE_EXTENSIONS_FOR_HIDDEN_FILES.stream().anyMatch(lowerCaseFileName::endsWith);
      boolean isNotExcludedExtension = excludedExtensions.stream().noneMatch(lowerCaseFileName::endsWith);

      if ((isHidden && isHiddenFileToCollect) || (!isHidden && isNotExcludedExtension)) {
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

  public static class Builder {
    private Path root = null;
    private Set<Path> existingSources = new HashSet<>();
    private Set<Path> directoriesToIgnore = new HashSet<>();
    private Set<Path> excludedFiles = new HashSet<>();
    private boolean shouldCollectJavaAndKotlinSources = false;

    private Builder() { }

    public Builder setRoot(Path root) {
      this.root = root;
      return this;
    }

    public Builder setExistingSources(Set<Path> existingSources) {
      this.existingSources = existingSources;
      return this;
    }

    public Builder setDirectoriesToIgnore(Set<Path> directoriesToIgnore) {
      this.directoriesToIgnore = directoriesToIgnore;
      return this;
    }

    public Builder setExcludedFiles(Set<Path> excludedFiles) {
      this.excludedFiles = excludedFiles;
      return this;
    }

    public Builder setShouldCollectJavaAndKotlinSources(boolean shouldCollectJavaAndKotlinSources) {
      this.shouldCollectJavaAndKotlinSources = shouldCollectJavaAndKotlinSources;
      return this;
    }

    public SourceCollector build() {
      if (root == null) {
        throw new IllegalStateException("Root path must be set");
      }
      return new SourceCollector(root, existingSources, directoriesToIgnore, excludedFiles, shouldCollectJavaAndKotlinSources);
    }
  }
}
