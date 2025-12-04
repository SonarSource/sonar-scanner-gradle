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

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sonarqube.gradle.run_configuration.DefaultRunConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class Gradle9Test extends AbstractGradleIT {

  @BeforeClass
  public static void beforeAll() {
    ignoreThisTestIfGradleVersionIsLessThan("9.0.0");
  }

  @Test
  public void gradle9Example() throws Exception {
    Map<String, String> env = Collections.emptyMap();
    Properties props = runGradlewSonarSimulationModeWithEnv("/gradle-9-example", env, new DefaultRunConfiguration(), "--console=plain", "build");
    assertThat(extractComparableProperties(props)).containsOnly(
      entry("sonar.binaries", "${parentBaseDir}/gradle-9-example/build/classes/java/main"),
      entry("sonar.host.url", "https://sonarcloud.io"),
      entry("sonar.java.binaries", "${parentBaseDir}/gradle-9-example/build/classes/java/main"),
      entry("sonar.java.enablePreview", "false"),
      entry("sonar.java.jdkHome", "<hidden>"),
      entry("sonar.java.libraries", "${M2}/repository/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar"),
      entry("sonar.java.source", "17"),
      entry("sonar.java.target", "17"),
      entry("sonar.java.test.binaries", "${parentBaseDir}/gradle-9-example/build/classes/java/test"),
      entry("sonar.java.test.libraries", "${parentBaseDir}/gradle-9-example/build/classes/java/main," +
        "${M2}/repository/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar," +
        "${M2}/repository/org/junit/jupiter/junit-jupiter/5.10.0/junit-jupiter-5.10.0.jar," +
        "${M2}/repository/org/junit/jupiter/junit-jupiter-params/5.10.0/junit-jupiter-params-5.10.0.jar," +
        "${M2}/repository/org/junit/jupiter/junit-jupiter-api/5.10.0/junit-jupiter-api-5.10.0.jar," +
        "${M2}/repository/org/junit/platform/junit-platform-commons/1.10.0/junit-platform-commons-1.10.0.jar," +
        "${M2}/repository/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar," +
        "${M2}/repository/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar"),
      entry("sonar.junit.reportPaths", "${parentBaseDir}/gradle-9-example/build/test-results/test"),
      entry("sonar.junit.reportsPath", "${parentBaseDir}/gradle-9-example/build/test-results/test"),
      entry("sonar.kotlin.gradleProjectRoot", "${parentBaseDir}/gradle-9-example"),
      entry("sonar.libraries", "${M2}/repository/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar"),
      entry("sonar.projectBaseDir", "${parentBaseDir}/gradle-9-example"),
      entry("sonar.projectKey", "gradle-9-example"),
      entry("sonar.projectName", "Gradle 9 example"),
      entry("sonar.projectVersion", "1.0-SNAPSHOT"),
      entry("sonar.scanner.apiBaseUrl", "https://api.sonarcloud.io"),
      entry("sonar.scanner.app", "ScannerGradle"),
      entry("sonar.scanner.appVersion", "<hidden>"),
      entry("sonar.scanner.arch", "<hidden>"),
      entry("sonar.scanner.internal.dumpToFile", "<hidden>"),
      entry("sonar.scanner.os", "<hidden>"),
      entry("sonar.sources", "${parentBaseDir}/gradle-9-example/src/main/java," +
        "${parentBaseDir}/gradle-9-example/build.gradle.kts," +
        "${parentBaseDir}/gradle-9-example/settings.gradle.kts"),
      entry("sonar.surefire.reportsPath", "${parentBaseDir}/gradle-9-example/build/test-results/test"),
      entry("sonar.tests", "${parentBaseDir}/gradle-9-example/src/test/java"),
      entry("sonar.working.directory", "${parentBaseDir}/gradle-9-example/build/sonar"));
  }

}
