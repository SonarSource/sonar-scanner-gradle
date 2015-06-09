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

import org.gradle.api.Action;
import org.gradle.listener.ActionBroadcast;

/**
 * An extension for configuring the <a href="http://docs.sonarqube.org/display/SONAR/Analyzing+with+Gradle">SonarQube</a> analysis.
 * <p>
 * The extension is added to all projects that have the {@code "sonar-gradle"} plugin applied, and all of their subprojects.
 * <p>
 * Example usage:
 * <pre>
 * sonarqube {
 *   skipProject = false // this is the default
 *   properties {
 *     property "sonar.host.url", "http://my.sonar.server" // adding a single property
 *     properties mapOfProperties // adding multiple properties at once
 *     properties["sonar.sources"] += sourceSets.other.java.srcDirs // manipulating an existing property }
 *   }
 * }
 * </pre>
 * <h3>SonarQube Properties</h3>
 * <p>
 * The SonarQube configuration is provided by using the {@link #properties(org.gradle.api.Action)} method and specifying properties.
 * Certain properties are required, such as {@code "sonar.host.url"} which provides the address of the SonarQube server.
 * For details on what properties are available, see <a href="http://docs.sonarqube.org/display/SONAR/Analysis+Parameters">Analysis Parameters</a> in the SonarQube documentation.
 * <p>
 * The {@code "sonar-gradle"} plugin adds default values for several plugins depending on the nature of the project.
 * Please see the <a href="http://docs.sonarqube.org/display/SONAR/Analyzing+with+Gradle">SonarQube Gradle documentation</a> for details on which properties are set and their values.
 * <p>
 * Please see the {@link SonarQubeProperties} class for more information on the mechanics of setting SonarQube properties, including laziness and property types.
 */
public class SonarQubeExtension {

  public static final String SONARQUBE_EXTENSION_NAME = "sonarqube";
  public static final String SONARQUBE_TASK_NAME = "sonarqube";

  private boolean skipProject;
  private final ActionBroadcast<SonarQubeProperties> propertiesActions;

  public SonarQubeExtension(ActionBroadcast<SonarQubeProperties> propertiesActions) {
    this.propertiesActions = propertiesActions;
  }

  /**
   * Adds an action that configures SonarQube properties for the associated Gradle project.
   * <p>
   * <em>Global</em> SonarQube properties (e.g. database connection settings) have to be set on the "root" project of the Sonar run.
   * This is the project that has the {@code sonar-gradle} plugin applied.
   * <p>
   * The action is passed an instance of {@code SonarQubeProperties}.
   * Evaluation of the action is deferred until {@code sonarqube.properties} is requested.
   * Hence it is safe to reference other Gradle model properties from inside the action.
   * <p>
   * SonarQube properties can also be set via system properties (and therefore from the command line).
   * This is mainly useful for global SonarQube properties like database credentials.
   * Every system property starting with {@code "sonar."} is automatically set on the "root" project of the SonarQube run (i.e. the project that has the {@code sonar-gradle} plugin applied).
   * System properties take precedence over properties declared in build scripts.
   *
   * @param action an action that configures SonarQube properties for the associated Gradle project
   */
  public void properties(Action<? super SonarQubeProperties> action) {
    propertiesActions.add(action);
  }

  /**
   * If the project should be excluded from analysis.
   * <p>
   * Defaults to {@code false}.
   */
  public boolean isSkipProject() {
    return skipProject;
  }

  public void setSkipProject(boolean skipProject) {
    this.skipProject = skipProject;
  }

}
