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
package org.sonarqube.gradle;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sonarqube.gradle.run_configuration.DefaultRunConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

public class Gradle9Test extends AbstractGradleIT {

  private static final Gson GSON = new Gson();
  private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
  private static final String EXPECTED_PROPERTIES_RESOURCE = "/org/sonarqube/gradle/Gradle9Test/gradle9-expected.json";

  @BeforeClass
  public static void beforeAll() {
    ignoreThisTestIfGradleVersionIsLessThan("9.0.0");
  }

  @Test
  public void gradle9Example() throws Exception {
    Map<String, String> env = Collections.emptyMap();
    Properties props = runGradlewSonarSimulationModeWithEnv("/gradle-9-example", env, new DefaultRunConfiguration(), "--console=plain", "build");
    assertThat(extractComparableProperties(props)).containsAllEntriesOf(loadExpectedProperties());
  }

  private static Map<String, String> loadExpectedProperties() throws IOException {
    try (Reader reader = new InputStreamReader(
      java.util.Objects.requireNonNull(Gradle9Test.class.getResourceAsStream(EXPECTED_PROPERTIES_RESOURCE)),
      StandardCharsets.UTF_8)) {
      return GSON.fromJson(reader, MAP_TYPE);
    }
  }
}
