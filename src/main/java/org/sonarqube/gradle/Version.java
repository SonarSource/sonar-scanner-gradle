/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2022 SonarSource
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

import java.util.Comparator;
import java.util.Objects;
import javax.annotation.Nonnull;

public class Version implements Comparable<Version> {
  private final int major;
  private final int minor;

  private Version(int major, int minor) {
    this.major = major;
    this.minor = minor;
  }

  public int major() {
    return major;
  }

  public int minor() {
    return minor;
  }

  public static Version of(int major, int minor) {
    return new Version(major, minor);
  }

  public static Version of(String version) {
    String[] split = version.split("\\.");
    int major = 0;
    int minor = 0;

    if (split.length > 0) {
      major = parseInt(split[0]);
      if (split.length > 1) {
        minor = parseInt(split[1]);
      }
    }
    return new Version(major, minor);
  }

  private static int parseInt(String field) {
    try {
      return Integer.parseInt(field);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Version version = (Version) o;
    return major == version.major && minor == version.minor;
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor);
  }

  @Override
  public String toString() {
    return major + "." + minor;
  }

  @Override
  public int compareTo(@Nonnull Version o) {
    return Comparator.comparingInt(Version::major)
      .thenComparing(Version::minor)
      .compare(this, o);
  }
}
