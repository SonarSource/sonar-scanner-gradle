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

import com.android.build.gradle.api.BaseVariant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPlugin;

import static org.sonarqube.gradle.SonarUtils.capitalize;
import static org.sonarqube.gradle.SonarUtils.isAndroidProject;

/**
 * A plugin for analyzing projects with the <a href="http://redirect.sonarsource.com/doc/analyzing-with-sq-gradle.html">SonarQube Scanner</a>.
 * When applied to a project, both the project itself and its subprojects will be analyzed (in a single run).
 */
public class SonarQubePlugin implements Plugin<Project> {
  private static final Logger LOGGER = Logging.getLogger(SonarQubePlugin.class);

  private ActionBroadcast<SonarQubeProperties> addBroadcaster(Map<String, ActionBroadcast<SonarQubeProperties>> actionBroadcastMap, Project project) {
    ActionBroadcast<SonarQubeProperties> actionBroadcast = new ActionBroadcast<>();
    actionBroadcastMap.put(project.getPath(), actionBroadcast);
    return actionBroadcast;
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
    if (project.getExtensions().findByName(SonarQubeExtension.SONARQUBE_EXTENSION_NAME) == null) {
      Map<String, ActionBroadcast<SonarQubeProperties>> actionBroadcastMap = new HashMap<>();
      addExtensions(project, actionBroadcastMap);
      LOGGER.debug("Adding " + SonarQubeExtension.SONARQUBE_TASK_NAME + " task to " + project);
      SonarQubeTask sonarQubeTask = project.getTasks().create(SonarQubeExtension.SONARQUBE_TASK_NAME, SonarQubeTask.class);
      sonarQubeTask.setDescription("Analyzes " + project + " and its subprojects with SonarQube.");
      configureTask(sonarQubeTask, project, actionBroadcastMap);
    }
  }

  private void addExtensions(Project project, Map<String, ActionBroadcast<SonarQubeProperties>> actionBroadcastMap) {
    project.getAllprojects().forEach(p -> {
      LOGGER.debug("Adding " + SonarQubeExtension.SONARQUBE_EXTENSION_NAME + " extension to " + p);
      ActionBroadcast<SonarQubeProperties> actionBroadcast = addBroadcaster(actionBroadcastMap, p);
      p.getExtensions().create(SonarQubeExtension.SONARQUBE_EXTENSION_NAME, SonarQubeExtension.class, actionBroadcast);
    });
  }

  private void configureTask(SonarQubeTask sonarQubeTask, Project project, Map<String, ActionBroadcast<SonarQubeProperties>> actionBroadcastMap) {
    ConventionMapping conventionMapping = sonarQubeTask.getConventionMapping();
    // this will call the SonarPropertyComputer to populate the properties of the task just before running it
    conventionMapping.map("properties", () -> new SonarPropertyComputer(actionBroadcastMap, project)
      .computeSonarProperties());

    Callable<Iterable<? extends Task>> testTasks = () -> project.getAllprojects().stream()
      .filter(p -> p.getPlugins().hasPlugin(JavaPlugin.class) && !p.getExtensions().getByType(SonarQubeExtension.class).isSkipProject())
      .map(p -> p.getTasks().getByName(JavaPlugin.TEST_TASK_NAME))
      .collect(Collectors.toList());
    sonarQubeTask.dependsOn(testTasks);

    Callable<Iterable<? extends Task>> compileTasks = () -> project.getAllprojects().stream()
      .filter(p -> isAndroidProject(p) && !p.getExtensions().getByType(SonarQubeExtension.class).isSkipProject())
      .map(p -> {
        BaseVariant variant = AndroidUtils.findVariant(p, p.getExtensions().getByType(SonarQubeExtension.class).getAndroidVariant());
        List<Task> allCompileTasks = new ArrayList<>();
        boolean unitTestTaskDepAdded = addTaskByName(p, "compile" + capitalize(variant.getName()) + "UnitTestJavaWithJavac", allCompileTasks);
        boolean androidTestTaskDepAdded = addTaskByName(p, "compile" + capitalize(variant.getName()) + "AndroidTestJavaWithJavac", allCompileTasks);
        // unit test compile and android test compile tasks already depends on main code compile so don't add a useless dependency
        // that would lead to run main compile task several times
        if (!unitTestTaskDepAdded && !androidTestTaskDepAdded) {
          addTaskByName(p, "compile" + capitalize(variant.getName()) + "JavaWithJavac", allCompileTasks);
        }
        return allCompileTasks;
      })
      .flatMap(List::stream)
      .collect(Collectors.toList());
    sonarQubeTask.dependsOn(compileTasks);
  }

}
