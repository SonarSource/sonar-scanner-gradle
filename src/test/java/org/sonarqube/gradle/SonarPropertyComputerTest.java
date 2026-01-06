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
package org.sonarqube.gradle;

import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarPropertyComputerTest {

  @Test
  void getSonarEnvironmentVariables_shouldFallbackWhenNoSuchMethodError() {
    // Arrange
    Project project = mock(Project.class);
    ProviderFactory providers = mock(ProviderFactory.class);

    when(project.getProviders()).thenReturn(providers);
    when(providers.environmentVariablesPrefixedBy("SONAR"))
      .thenThrow(new NoSuchMethodError("environmentVariablesPrefixedBy"));

    // Act
    Map<String, String> result = SonarPropertyComputer.getSonarEnvironmentVariables(project);

    // Assert
    assertThat(result).isNotNull();
  }

  @Test
  void getSonarEnvironmentVariables_shouldUseNewApiWhenAvailable() {
    // Arrange
    Project project = mock(Project.class);
    ProviderFactory providers = mock(ProviderFactory.class);
    Provider<Map<String, String>> envProvider = mock(Provider.class);
    Map<String, String> inputEnvVars = Map.of("SONAR_HOST_URL", "http://localhost:9000");

    when(project.getProviders()).thenReturn(providers);
    when(providers.environmentVariablesPrefixedBy("SONAR")).thenReturn(envProvider);
    when(envProvider.get()).thenReturn(inputEnvVars);

    // Act
    Map<String, String> result = SonarPropertyComputer.getSonarEnvironmentVariables(project);

    // Assert
    assertThat(result).containsEntry("sonar.host.url", "http://localhost:9000");
  }

  @Test
  void getSonarSystemProperties_shouldFallbackWhenNoSuchMethodError() {
    // Arrange
    Project project = mock(Project.class);
    ProviderFactory providers = mock(ProviderFactory.class);

    when(project.getProviders()).thenReturn(providers);
    when(providers.systemPropertiesPrefixedBy("sonar"))
      .thenThrow(new NoSuchMethodError("systemPropertiesPrefixedBy"));

    // Act
    Map<String, String> result = SonarPropertyComputer.getSonarSystemProperties(project);

    // Assert
    assertThat(result).isNotNull().allSatisfy((key, value) -> assertThat(key).startsWith("sonar."));
  }
}