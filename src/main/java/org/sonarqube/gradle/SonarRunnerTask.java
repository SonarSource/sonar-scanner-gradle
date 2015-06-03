/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sonarqube.gradle;

import com.google.common.collect.Maps;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.sonar.runner.api.EmbeddedRunner;

import java.util.Map;
import java.util.Properties;

/**
 * Analyses one or more projects with the <a href="http://redirect.sonarsource.com/doc/analyzing-with-sq-gradle.html">SonarQube Runner</a>.
 * Can be used with or without the {@code "sonar-gradle"} plugin.
 * If used together with the plugin, {@code sonarProperties} will be populated with defaults based on Gradle's object model and user-defined
 * values configured via {@link org.sonarqube.gradle.SonarRunnerExtension}.
 * If used without the plugin, all properties have to be configured manually.
 * For more information on how to configure the SonarQube Runner, and on which properties are available, see the
 * <a href="http://redirect.sonarsource.com/doc/analyzing-with-sq-gradle.html">SonarQube Runner documentation</a>.
 */
public class SonarRunnerTask extends DefaultTask {

  private static final Logger LOGGER = Logging.getLogger(SonarRunnerTask.class);

  private Map<String, Object> sonarProperties;

  @TaskAction
  public void run() {
    Map<String, Object> properties = getSonarProperties();

    Properties propertiesObject = new Properties();
    propertiesObject.putAll(properties);

    EmbeddedRunner.create()
        .setApp("Gradle", getProject().getGradle().getGradleVersion())
        .addProperties(propertiesObject)
        .execute();
  }

  /**
   * The String key/value pairs to be passed to the SonarQube Runner.
   * {@code null} values are not permitted.
   */
  @Input
  public Map<String, Object> getSonarProperties() {
    if (sonarProperties == null) {
      sonarProperties = Maps.newLinkedHashMap();
    }

    return sonarProperties;
  }

}
