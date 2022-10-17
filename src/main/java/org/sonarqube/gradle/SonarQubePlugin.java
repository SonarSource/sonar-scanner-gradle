/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2022 SonarSource
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

import com.android.build.gradle.api.BaseVariant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.gradle.util.GradleVersion;

import static org.sonarqube.gradle.SonarUtils.capitalize;
import static org.sonarqube.gradle.SonarUtils.isAndroidProject;

/**
 * A plugin for analyzing projects with the <a href="http://redirect.sonarsource.com/doc/analyzing-with-sq-gradle.html">SonarScanner for Gradle</a>.
 * When applied to a project, both the project itself and its subprojects will be analyzed (in a single run).
 */
public class SonarQubePlugin implements Plugin<Project> {

  private static final Logger LOGGER = Logging.getLogger(SonarQubePlugin.class);

  private static ActionBroadcast<SonarProperties> addBroadcaster(Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap, Project project) {
    return actionBroadcastMap.computeIfAbsent(project.getPath(), s -> new ActionBroadcast<>());
  }

  private static boolean addTaskByName(Project p, String name, List<Task> allCompileTasks) {
    try {
      allCompileTasks.add(p.getTasks().getByName(name));
      return true;
    } catch (UnknownTaskException e) {
      return false;
    }
  }

  @Override
  public void apply(Project project) {
    // don't try to see if the task was added to any project in the hierarchy. If you do it, it will try to resolve recursively the configuration of all
    // the projects, failing if a project has a sonarqube configuration since the extension wasn't added to it yet.
    if (project.getExtensions().findByName(SonarExtension.SONAR_EXTENSION_NAME) == null) {
      Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap = new HashMap<>();
      addExtensions(project, SonarExtension.SONAR_EXTENSION_NAME, actionBroadcastMap);
      addExtensions(project, SonarExtension.SONAR_DEPRECATED_EXTENSION_NAME, actionBroadcastMap);
      LOGGER.debug("Adding '{}' task to '{}'", SonarExtension.SONAR_TASK_NAME, project);

      SonarTask sonarqubeTask = project.getTasks().create(SonarExtension.SONAR_DEPRECATED_TASK_NAME, SonarTask.class);
      sonarqubeTask.setDescription("Analyzes " + project + " and its subprojects with Sonar. This task is deprecated. Use 'sonar' instead.");
      sonarqubeTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);

      SonarTask sonarTask = project.getTasks().create(SonarExtension.SONAR_TASK_NAME, SonarTask.class);
      sonarTask.setDescription("Analyzes " + project + " and its subprojects with Sonar.");
      sonarTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);

      configureTask(sonarqubeTask, project, actionBroadcastMap);
      configureTask(sonarTask, project, actionBroadcastMap);
    }
  }

  private static void addExtensions(Project project, String name, Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap) {
    project.getAllprojects().forEach(p -> {
      LOGGER.debug("Adding " + name + " extension to " + p);
      ActionBroadcast<SonarProperties> actionBroadcast = addBroadcaster(actionBroadcastMap, p);
      p.getExtensions().create(name, SonarExtension.class, actionBroadcast);
    });
  }

  private static void configureTask(SonarTask sonarTask, Project project, Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap) {
    ConventionMapping conventionMapping = sonarTask.getConventionMapping();
    // this will call the SonarPropertyComputer to populate the properties of the task just before running it
    conventionMapping.map("properties", () -> new SonarPropertyComputer(actionBroadcastMap, project)
      .computeSonarProperties());

    sonarTask.mustRunAfter(getJavaTestTasks(project));
    sonarTask.dependsOn(getJavaCompileTasks(project));
    sonarTask.mustRunAfter(getJacocoTasks(project));
    sonarTask.dependsOn(getAndroidCompileTasks(project));
    setNotCompatibleWithConfigurationCache(sonarTask);

  }

  private static void setNotCompatibleWithConfigurationCache(SonarTask sonarQubeTask) {
    if (isGradleVersionGreaterOrEqualTo("7.4.0")) {
      sonarQubeTask.notCompatibleWithConfigurationCache("Plugin is not compatible with configuration cache");
    }
  }

  private static boolean isGradleVersionGreaterOrEqualTo(String version) {
    return GradleVersion.current().compareTo(GradleVersion.version(version)) >=0;
  }

  private static Callable<Iterable<? extends Task>> getJacocoTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> p.getPlugins().hasPlugin(JacocoPlugin.class) && !p.getExtensions().getByType(SonarExtension.class).isSkipProject())
      .map(p -> p.getTasks().withType(JacocoReport.class))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  private static Callable<Iterable<? extends Task>> getJavaTestTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> p.getPlugins().hasPlugin(JavaPlugin.class) && !p.getExtensions().getByType(SonarExtension.class).isSkipProject())
      .map(p -> p.getTasks().getByName(JavaPlugin.TEST_TASK_NAME))
      .collect(Collectors.toList());
  }

  private static Callable<Iterable<? extends Task>> getJavaCompileTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> p.getPlugins().hasPlugin(JavaPlugin.class) && !p.getExtensions().getByType(SonarExtension.class).isSkipProject())
      .flatMap(p -> Stream.of(p.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME), p.getTasks().getByName(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME)))
      .collect(Collectors.toList());
  }

  private static Callable<Iterable<? extends Task>> getAndroidCompileTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> isAndroidProject(p) && !p.getExtensions().getByType(SonarExtension.class).isSkipProject())
      .map(p -> {
        BaseVariant variant = AndroidUtils.findVariant(p, p.getExtensions().getByType(SonarExtension.class).getAndroidVariant());
        List<Task> allCompileTasks = new ArrayList<>();
        if (variant != null) {
          final String compileTaskPrefix = "compile" + capitalize(variant.getName());
          boolean unitTestTaskDepAdded = addTaskByName(p, compileTaskPrefix + "UnitTestJavaWithJavac", allCompileTasks);
          boolean androidTestTaskDepAdded = addTaskByName(p, compileTaskPrefix + "AndroidTestJavaWithJavac", allCompileTasks);
          // unit test compile and android test compile tasks already depends on main code compile so don't add a useless dependency
          // that would lead to run main compile task several times
          if (!unitTestTaskDepAdded && !androidTestTaskDepAdded) {
            addTaskByName(p, compileTaskPrefix + "JavaWithJavac", allCompileTasks);
          }
        }
        return allCompileTasks;
      })
      .flatMap(List::stream)
      .collect(Collectors.toList());
  }
}
