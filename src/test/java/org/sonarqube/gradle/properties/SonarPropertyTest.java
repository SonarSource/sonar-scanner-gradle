/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2026 SonarSource
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
package org.sonarqube.gradle.properties;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class SonarPropertyTest {

  @ParameterizedTest
  @MethodSource("provideInvalidProperties")
  void parseInvalidPropertyReturnsEmpty(String input) {
    Optional<SonarProperty> result = SonarProperty.parse(input);
    assertThat(result).isEmpty();
  }

  private static Stream<Arguments> provideInvalidProperties() {
    return Stream.of(
      Arguments.of((String) null),
      Arguments.of(""),
      Arguments.of("invalid"),
      Arguments.of("sonar.unknown")
    );
  }

  @ParameterizedTest
  @MethodSource("provideValidProperties")
  void parseValidProperty(String input, String expectedSubproject, String expectedProperty) {
    Optional<SonarProperty> result = SonarProperty.parse(input);
    assertThat(result).isPresent();
    SonarProperty property = result.get();
    assertThat(property.getSubproject()).isEqualTo(expectedSubproject);
    assertThat(property.getProperty()).isEqualTo(expectedProperty);
  }

  @Test
  void parseRoundTrip() {
    SonarProperty original = new SonarProperty("my.module", SonarProperty.SKIP);
    String stringValue = original.toString();
    Optional<SonarProperty> parsed = SonarProperty.parse(stringValue);
    assertThat(parsed).contains(original);
  }

  @Test
  void rootProjectPropertyCreatesPropertyWithEmptySubproject() {
    SonarProperty property = SonarProperty.rootProjectProperty(SonarProperty.PROJECT_KEY);
    assertThat(property.getSubproject()).isNull();
    assertThat(property.getProperty()).isEqualTo(SonarProperty.PROJECT_KEY);
  }

  @Test
  void constructorAndGetters() {
    String module = "test.module";
    String prop = SonarProperty.VERBOSE;
    SonarProperty property = new SonarProperty(module, prop);
    assertThat(property.getSubproject()).isEqualTo(module);
    assertThat(property.getProperty()).isEqualTo(prop);
  }

  @Test
  void toStringWithSubproject() {
    SonarProperty property = new SonarProperty("my.module", SonarProperty.SKIP);
    assertThat(property).hasToString("my.module." + SonarProperty.SKIP);
  }

  @Test
  void toStringWithoutSubproject() {
    SonarProperty property = new SonarProperty(null, SonarProperty.SKIP);
    assertThat(property).hasToString(SonarProperty.SKIP);
  }

  @Test
  void equalsAndHashCode() {
    SonarProperty p1 = new SonarProperty("mod", SonarProperty.SKIP);
    SonarProperty p2 = new SonarProperty("mod", SonarProperty.SKIP);
    SonarProperty p3 = new SonarProperty("other", SonarProperty.SKIP);
    SonarProperty p4 = new SonarProperty("mod", SonarProperty.VERBOSE);

    assertThat(p1)
      .isEqualTo(p2)
      .isNotEqualTo(p3)
      .isNotEqualTo(p4);


    assertThat(p1.hashCode()).hasSameHashCodeAs(p2.hashCode());
  }

  private static Stream<Arguments> provideValidProperties() {
    return Stream.of(
      Arguments.of(SonarProperty.SKIP, null, SonarProperty.SKIP),
      Arguments.of(SonarProperty.PROJECT_KEY, null, SonarProperty.PROJECT_KEY),
      Arguments.of("mySubproject." + SonarProperty.SKIP, "mySubproject", SonarProperty.SKIP),
      Arguments.of("a.b.c." + SonarProperty.PROJECT_KEY, "a.b.c", SonarProperty.PROJECT_KEY),
      Arguments.of("module.with.dots." + SonarProperty.VERBOSE, "module.with.dots", SonarProperty.VERBOSE)
    );
  }
}
