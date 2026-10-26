/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2025 SonarSource
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.tasks.JacocoReport;

/** Owns each project's Sonar extensions, metadata, and dependency resolution. */
public class SonarProjectPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    Map<String, ActionBroadcast<SonarProperties>> actions = new HashMap<>();
    Map<String, AndroidConfig> android = new HashMap<>();
    SonarQubePlugin.registerSonarExtensions(project, actions);
    TaskProvider<SonarResolverTask> resolver = SonarQubePlugin.registerResolverTask(project, project, new HashSet<>(), true);
    resolver.configure(task -> {
      task.getTopLevelProject().set(project.getPath().equals(":"));
      task.mustRunAfter(project.getTasks().withType(Test.class), project.getTasks().withType(JacocoReport.class));
    });
    SonarQubePlugin.configureAndroid(project, android, resolver);
    Provider<ComputedProperties> properties = project.provider(() -> new SonarPropertyComputer(actions, android, project).computeIsolatedProperties());
    TaskProvider<SonarProjectPropertiesTask> metadata = project.getTasks().register("sonarProjectProperties", SonarProjectPropertiesTask.class, task -> {
      task.getProjectPath().set(project.getPath());
      task.getBuildScript().set(project.getBuildFile().getAbsolutePath());
      task.getSkipProject().set(project.provider(() -> SonarUtils.isSkipped(project)));
      task.getAnalysisProperties().set(properties.map(computed -> computed.properties.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()))));
      task.getUserDefinedKeys().set(properties.map(computed -> new ArrayList<>(computed.userDefinedKeys)));
      task.getOutputFile().set(project.getLayout().getBuildDirectory().file("sonar/project.properties"));
    });
    Configuration metadataElements = project.getConfigurations().consumable("sonarProjectPropertiesElements").get();
    project.getArtifacts().add(metadataElements.getName(), metadata.flatMap(SonarProjectPropertiesTask::getOutputFile));
    Configuration resolverElements = project.getConfigurations().consumable("sonarResolverElements").get();
    project.getArtifacts().add(resolverElements.getName(), resolver.map(SonarResolverTask::getOutputFile), artifact -> artifact.builtBy(resolver));
    if (project.getPath().equals(":")) {
      Configuration moduleProperties = inputs(project, "sonarProjectProperties");
      Configuration resolvers = inputs(project, "sonarResolver");
      for (String name : new String[] {SonarExtension.SONAR_TASK_NAME, SonarExtension.SONAR_DEPRECATED_TASK_NAME}) {
        project.getTasks().register(name, SonarTask.class, task -> {
          task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
          task.setDescription("Analyzes this build with Sonar.");
          task.setResolverFiles(Collections.emptySet());
          task.getIsolatedResolverFiles().from(resolvers, resolver.map(SonarResolverTask::getOutputFile));
          task.dependsOn(resolver);
          task.getModulePropertiesFiles().from(moduleProperties);
          task.setBuildSonar(project.getLayout().getBuildDirectory().dir("sonar"));
          task.setProperties(properties.map(computed -> computed.properties.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()))), properties.map(computed -> computed.userDefinedKeys));
          task.getRootBuildScript().set(project.getBuildFile().getAbsolutePath());
        });
      }
    }
  }

  private static Configuration inputs(Project project, String name) {
    Configuration dependencies = project.getConfigurations().dependencyScope(name + "Dependencies").get();
    Configuration result = project.getConfigurations().resolvable(name + "Inputs", configuration -> configuration.extendsFrom(dependencies)).get();
    // Only immutable project paths cross the boundary. Gradle resolves the producers through their artifacts.
    project.getAllprojects().stream().map(Project::getPath).filter(path -> !path.equals(":")).forEach(path ->
      project.getDependencies().add(dependencies.getName(), project.getDependencies().project(Map.of("path", path, "configuration", name + "Elements")))
    );
    return result;
  }
}
