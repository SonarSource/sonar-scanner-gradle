/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2023 SonarSource
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionTest {
  @Test
  void parses_versions() {
    assertVersion(Version.of("1.2.3"), 1, 2);
    assertVersion(Version.of("1"), 1, 0);
    assertVersion(Version.of("11.2.3"), 11, 2);
    assertVersion(Version.of(""), 0, 0);
    assertVersion(Version.of("1.20"), 1, 20);
    assertVersion(Version.of("1.invalid.3"), 1, 0);
  }

  @Test
  void equals_and_hashcode() {
    assertThat(Version.of(1, 1)).isEqualTo(Version.of(1, 1));
    assertThat(Version.of(1, 2)).isNotEqualTo(Version.of(1, 1));
    assertThat(Version.of(2, 1)).isNotEqualTo(Version.of(1, 1));

    assertThat(Version.of(1, 1)).hasSameHashCodeAs(Version.of(1, 1));
  }

  @Test
  void compares_versions() {
    assertThat(Version.of(1, 2)).isGreaterThan(Version.of(1, 1));
    assertThat(Version.of(2, 1)).isGreaterThan(Version.of(1, 1));
    assertThat(Version.of(3, 2)).isGreaterThan(Version.of(2, 9));
    assertThat(Version.of(1, 1)).isEqualTo(Version.of(1, 1));
  }

  private static void assertVersion(Version v, int expectedMajor, int expectedMinor) {
    assertThat(v.major()).isEqualTo(expectedMajor);
    assertThat(v.minor()).isEqualTo(expectedMinor);
  }
}
