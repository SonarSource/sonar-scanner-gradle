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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.gradle.util.GradleVersion;

import static org.sonarqube.gradle.SonarUtils.capitalize;
import static org.sonarqube.gradle.SonarUtils.getMainClassPath;
import static org.sonarqube.gradle.SonarUtils.getTestClassPath;
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

      List<File> resolverFiles = registerAndConfigureResolverTasks(project);

      TaskContainer tasks = project.getTasks();
      tasks.register(SonarExtension.SONAR_DEPRECATED_TASK_NAME, SonarTask.class, task -> {
        task.setDescription("Analyzes " + project + " and its subprojects with Sonar. This task is deprecated. Use 'sonar' instead.");
        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        task.setResolverFiles(resolverFiles);
        configureTask(task, project, actionBroadcastMap);
      });

      tasks.register(SonarExtension.SONAR_TASK_NAME, SonarTask.class, task -> {
        task.setDescription("Analyzes " + project + " and its subprojects with Sonar.");
        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        task.setResolverFiles(resolverFiles);
        configureTask(task, project, actionBroadcastMap);
      });
    }
  }

  /**
   * Register and configure a resolver task per (sub-)project.
   * As the tasks are configured, we capture list of output files where the resolved properties will be written.
   */
  private static List<File> registerAndConfigureResolverTasks(Project topLevelProject) {
    List<File> resolverFiles = new ArrayList<>();
    topLevelProject.getAllprojects().forEach(target ->
      target.getTasks().register(SonarResolverTask.TASK_NAME, SonarResolverTask.class, task -> {
        task.setDescription(SonarResolverTask.TASK_DESCRIPTION);
        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        if (target == topLevelProject) {
          task.setTopLevelProject(true);
        }
        task.setProjectName(SonarUtils.constructPrefixedProjectName(target.getPath()));

        FileCollection mainClassPath = getMainClassPath(target);
        task.setCompileClasspath(mainClassPath);
        FileCollection testClassPath = getTestClassPath(target);
        task.setTestCompileClasspath(testClassPath);

        DirectoryProperty buildDirectory = target.getLayout().getBuildDirectory();
        File localSonarResolver = new File(buildDirectory.getAsFile().get(), "sonar-resolver");
        localSonarResolver.mkdirs();
        task.setOutputDirectory(localSonarResolver);
        try {
          resolverFiles.add(task.getOutputFile());
        } catch (IOException e) {
          LOGGER.warn("Could not get output file from task {} on project {}", task.getName(), target.getName());
        }
      })
    );
    return resolverFiles;
  }

  private static void addExtensions(Project project, String name, Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap) {
    project.getAllprojects().forEach(p -> {
      LOGGER.debug("Adding " + name + " extension to " + p);
      ActionBroadcast<SonarProperties> actionBroadcast = addBroadcaster(actionBroadcastMap, p);
      p.getExtensions().create(name, SonarExtension.class, actionBroadcast);
    });
  }

  private static void configureTask(SonarTask sonarTask, Project project, Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap) {
    Provider<Map<String, String>> conventionProvider = project.provider(() -> new SonarPropertyComputer(actionBroadcastMap, project).computeSonarProperties())
      .map(m -> m.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue())));

    if (isGradleVersionGreaterOrEqualTo("6.1")) {
      MapProperty<String, String> mapProperty = project.getObjects().mapProperty(String.class, String.class);
      mapProperty.convention(conventionProvider);
      mapProperty.finalizeValueOnRead();
      sonarTask.setProperties(mapProperty);
    } else {
      sonarTask.setProperties(conventionProvider);
    }

    sonarTask.mustRunAfter(getJavaCompileTasks(project));
    sonarTask.mustRunAfter(getAndroidCompileTasks(project));
    sonarTask.mustRunAfter(getJavaTestTasks(project));
    sonarTask.mustRunAfter(getJacocoTasks(project));
  }

  private static boolean isGradleVersionGreaterOrEqualTo(String version) {
    return GradleVersion.current().compareTo(GradleVersion.version(version)) >= 0;
  }

  private static Callable<Iterable<? extends Task>> getJacocoTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> p.getPlugins().hasPlugin(JacocoPlugin.class) && notSkipped(p))
      .map(p -> p.getTasks().withType(JacocoReport.class))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  private static Callable<Iterable<? extends Task>> getJavaTestTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> p.getPlugins().hasPlugin(JavaPlugin.class) && notSkipped(p))
      .map(p -> p.getTasks().getByName(JavaPlugin.TEST_TASK_NAME))
      .collect(Collectors.toList());
  }

  private static Callable<Iterable<? extends Task>> getJavaCompileTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> p.getPlugins().hasPlugin(JavaPlugin.class) && notSkipped(p))
      .flatMap(p -> Stream.of(p.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME), p.getTasks().getByName(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME)))
      .collect(Collectors.toList());
  }

  private static Callable<Iterable<? extends Task>> getClassPathResolverTask(Project project) {
    return () -> project.getAllprojects().stream()
      .map(p -> p.getTasks().getByName(SonarResolverTask.TASK_NAME))
      .collect(Collectors.toList());
  }

  static boolean notSkipped(Project p) {
    return !isSkipped(p);
  }

  static boolean isSkipped(Project p) {
    return getSonarExtensions(p).stream().anyMatch(SonarExtension::isSkipProject);
  }

  private static List<SonarExtension> getSonarExtensions(Project p) {
    return Stream.of(SonarExtension.SONAR_EXTENSION_NAME, SonarExtension.SONAR_DEPRECATED_EXTENSION_NAME)
      .map(name -> (SonarExtension) p.getExtensions().getByName(name))
      .collect(Collectors.toList());
  }

  @Nullable
  static String getConfiguredAndroidVariant(Project p) {
    return getSonarExtensions(p).stream()
      .map(SonarExtension::getAndroidVariant)
      .filter(Objects::nonNull)
      .findFirst().orElse(null);
  }

  private static Callable<Iterable<? extends Task>> getAndroidCompileTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> isAndroidProject(p) && notSkipped(p))
      .map(p -> {
        AndroidUtils.AndroidVariantAndExtension androidVariantAndExtension = AndroidUtils.findVariantAndExtension(p, getConfiguredAndroidVariant(p));

        List<Task> allCompileTasks = new ArrayList<>();
        if (androidVariantAndExtension != null && androidVariantAndExtension.getVariant() != null) {
          final String compileTaskPrefix = "compile" + capitalize(androidVariantAndExtension.getVariant().getName());
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
