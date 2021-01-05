/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2021 SonarSource
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

import java.util.Objects;
import java.util.Optional;

class JavaCompilerConfiguration {
  private String release;
  private String target;
  private String source;
  private String jdkHome;
  private final String taskName;

  JavaCompilerConfiguration(String taskName) {
    this.taskName = taskName;
  }

  public Optional<String> getRelease() {
    return Optional.ofNullable(release);
  }

  public Optional<String> getTarget() {
    return Optional.ofNullable(target);
  }

  public Optional<String> getSource() {
    return Optional.ofNullable(source);
  }

  public Optional<String> getJdkHome() {
    return Optional.ofNullable(jdkHome);
  }

  public String getTaskName() {
    return taskName;
  }

  public static boolean same(JavaCompilerConfiguration one, JavaCompilerConfiguration two) {
    return Objects.equals(one.jdkHome, two.jdkHome)
      && Objects.equals(one.release, two.release)
      && Objects.equals(one.source, two.source)
      && Objects.equals(one.target, two.target);
  }

  public void setJdkHome(String jdkHome) {
    this.jdkHome = jdkHome;
  }

  public void setRelease(String release) {
    this.release = release;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public void setSource(String source) {
    this.source = source;
  }
}
