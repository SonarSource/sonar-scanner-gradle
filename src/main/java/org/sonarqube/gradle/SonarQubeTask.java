/**
 * SonarQube Gradle Plugin
 * Copyright (C) 2015 SonarSource
 * sonarqube@googlegroups.com
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

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Properties;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.sonar.runner.api.EmbeddedRunner;

/**
 * Analyses one or more projects with the <a href="http://redirect.sonarsource.com/doc/analyzing-with-sq-gradle.html">SonarQube Runner</a>.
 * Can be used with or without the {@code "sonar-gradle"} plugin.
 * If used together with the plugin, {@code properties} will be populated with defaults based on Gradle's object model and user-defined
 * values configured via {@link SonarQubeExtension}.
 * If used without the plugin, all properties have to be configured manually.
 * For more information on how to configure the SonarQube Runner, and on which properties are available, see the
 * <a href="http://redirect.sonarsource.com/doc/analyzing-with-sq-gradle.html">SonarQube Runner documentation</a>.
 */
public class SonarQubeTask extends DefaultTask {

  private Map<String, Object> sonarProperties;

  @TaskAction
  public void run() {
    Map<String, Object> properties = getProperties();

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
  public Map<String, Object> getProperties() {
    if (sonarProperties == null) {
      sonarProperties = Maps.newLinkedHashMap();
    }

    return sonarProperties;
  }

}
