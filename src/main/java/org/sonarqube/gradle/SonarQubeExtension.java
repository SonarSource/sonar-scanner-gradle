/**
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2019 SonarSource
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

import org.gradle.api.Action;

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
 * For details on what properties are available, see <a href="http://docs.sonarqube.org/display/SONAR/Analysis+Parameters">Analysis Parameters</a>
 * in the SonarQube documentation.
 * <p>
 * The {@code "sonar-gradle"} plugin adds default values for several plugins depending on the nature of the project.
 * Please see the <a href="http://docs.sonarqube.org/display/SONAR/Analyzing+with+Gradle">SonarQube Gradle documentation</a>
 * for details on which properties are set and their values.
 * <p>
 * Please see the {@link SonarQubeProperties} class for more information on the mechanics of setting SonarQube properties,
 * including laziness and property types.
 */
public class SonarQubeExtension {

  public static final String SONARQUBE_EXTENSION_NAME = "sonarqube";
  public static final String SONARQUBE_TASK_NAME = "sonarqube";

  private boolean skipProject;
  private final ActionBroadcast<SonarQubeProperties> propertiesActions;
  private String androidVariant;

  public SonarQubeExtension(ActionBroadcast<SonarQubeProperties> propertiesActions) {
    this.propertiesActions = propertiesActions;
  }

  /**
   * Adds an action that configures SonarQube properties for the associated Gradle project.
   * <p>
   * <em>Global</em> SonarQube properties (e.g. server connection settings) have to be set on the "root" project of the SonarQube run.
   * This is the project that has the {@code sonar-gradle} plugin applied.
   * <p>
   * The action is passed an instance of {@code SonarQubeProperties}.
   * Evaluation of the action is deferred until {@code sonarqube.properties} is requested.
   * Hence it is safe to reference other Gradle model properties from inside the action.
   * <p>
   * SonarQube properties can also be set via system properties (and therefore from the command line).
   * This is mainly useful for global SonarQube properties like database credentials.
   * Every system property starting with {@code "sonar."} is automatically set on the "root" project of the SonarQube run 
   * (i.e. the project that has the {@code sonar-gradle} plugin applied).
   * System properties take precedence over properties declared in build scripts.
   *
   * @param action an action that configures SonarQube properties for the associated Gradle project
   */
  public void properties(Action<? super SonarQubeProperties> action) {
    propertiesActions.add(action);
  }

  /**
   * Defaults to {@code false}.
   * @return true if the project should be excluded from analysis.
   */
  public boolean isSkipProject() {
    return skipProject;
  }

  public void setSkipProject(boolean skipProject) {
    this.skipProject = skipProject;
  }

  /**
   * @return Name of the variant to analyze. If null we'll take the first release variant
   */
  public String getAndroidVariant() {
    return androidVariant;
  }

  public void setAndroidVariant(String androidVariant) {
    this.androidVariant = androidVariant;
  }
}
