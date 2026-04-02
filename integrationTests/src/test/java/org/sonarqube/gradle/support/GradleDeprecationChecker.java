/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarqube.gradle.support;

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.SoftAssertions;

final class GradleDeprecationChecker {
  private static final List<String> DEPRECATION_MARKERS = List.of("has been deprecated");
  private static final List<String> EXPECTED_ORIGINS = List.of("SonarResolverTask.java:152", "SonarResolverTask.java:155");

  private GradleDeprecationChecker() {
  }

  static void assertNoUnexpectedWarnings(String text) {
    SoftAssertions softly = new SoftAssertions();
    String[] lines = text.split(System.lineSeparator());
    for (int i = 0; i < lines.length; i++) {
      if (!isDeprecationLine(lines[i])) {
        continue;
      }
      List<String> warning = collectWarning(lines, i);
      softly.assertThat(warning).noneMatch(GradleDeprecationChecker::isUnexpectedWarning);
      i += warning.size() - 1;
    }
    softly.assertAll();
  }

  static boolean isUnexpectedWarning(String line) {
    return line.contains("org.sonarqube.gradle") && EXPECTED_ORIGINS.stream().noneMatch(line::contains);
  }

  private static boolean isDeprecationLine(String line) {
    return DEPRECATION_MARKERS.stream().anyMatch(line::contains);
  }

  private static List<String> collectWarning(String[] lines, int start) {
    List<String> warning = new ArrayList<>();
    warning.add(lines[start]);
    for (int i = start + 1; i < lines.length && lines[i].matches("^\\s++at\\s++.+"); i++) {
      warning.add(lines[i]);
    }
    return warning;
  }
}
