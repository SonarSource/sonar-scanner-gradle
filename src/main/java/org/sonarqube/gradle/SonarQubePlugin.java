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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.gradle.util.GradleVersion;

import static org.sonarqube.gradle.SonarUtils.isAndroidProject;

/**
 * A plugin for analyzing projects with the <a href="http://redirect.sonarsource.com/doc/analyzing-with-sq-gradle.html">SonarScanner for Gradle</a>.
 * When applied to a project, both the project itself and its subprojects will be analyzed (in a single run).
 */
public class SonarQubePlugin implements Plugin<Project> {
  private static final Logger LOGGER = Logging.getLogger(SonarQubePlugin.class);

  private static ActionBroadcast<SonarProperties> addBroadcaster(Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap, Project project) {
    return actionBroadcastMap.computeIfAbsent(project.getPath(), ignored -> new ActionBroadcast<>());
  }

  /**
   * Register Sonar extensions and Sonar resolver tasks and compute Android specific properties for each project included in some top-level project.
   */
  private static Set<File> configureAllProjects(
    Project topLevelProject,
    Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap,
    Map<String, AndroidConfig> androidConfigMap
  ) {
    Set<File> resolverFiles = new HashSet<>();
    topLevelProject.getAllprojects().forEach(project -> {
      registerSonarExtensions(project, actionBroadcastMap);
      TaskProvider<SonarResolverTask> resolverTaskProvider = registerResolverTask(topLevelProject, project, resolverFiles);
      configureAndroid(project, androidConfigMap, resolverTaskProvider);
    });
    return resolverFiles;
  }

  /**
   * Register Sonar extensions on a project.
   */
  private static void registerSonarExtensions(Project project, Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap) {
    ActionBroadcast<SonarProperties> actionBroadcast = addBroadcaster(actionBroadcastMap, project);

    LOGGER.debug("Adding " + SonarExtension.SONAR_EXTENSION_NAME + " extension to " + project.getName());
    project.getExtensions().create(SonarExtension.SONAR_EXTENSION_NAME, SonarExtension.class, actionBroadcast);

    LOGGER.debug("Adding " + SonarExtension.SONAR_DEPRECATED_EXTENSION_NAME + " extension to " + project.getName());
    project.getExtensions().create(SonarExtension.SONAR_DEPRECATED_EXTENSION_NAME, SonarExtension.class, actionBroadcast);
  }

  /**
   * Register and configure the Sonar resolver task for a project.
   */
  private static TaskProvider<SonarResolverTask> registerResolverTask(Project topLevelProject, Project project, Set<File> resolverFiles) {
    return project.getTasks().register(SonarResolverTask.TASK_NAME, SonarResolverTask.class, resolverTask -> {
      resolverTask.setDescription(SonarResolverTask.TASK_DESCRIPTION);
      resolverTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
      resolverTask.setSkipProject(project.provider(() -> SonarUtils.isSkipped(project)));
      resolverTask.setProjectName(SonarUtils.constructPrefixedProjectName(project.getPath()));
      if (project == topLevelProject) {
        resolverTask.setTopLevelProject(true);
      }
      resolverTask.setCompileClasspath(project.provider(() -> querySourceSet(project, SourceSet.MAIN_SOURCE_SET_NAME)));
      resolverTask.setTestCompileClasspath(project.provider(() -> querySourceSet(project, SourceSet.TEST_SOURCE_SET_NAME)));
      if (!isAndroidProject(project)) {
        resolverTask.setMainLibraries(project.provider(() -> project.files(SonarUtils.getRuntimeJars())));
        resolverTask.setTestLibraries(project.provider(() -> project.files(SonarUtils.getRuntimeJars())));
      }
      File buildDirectory = new File(project.getLayout().getBuildDirectory().getAsFile().get(), "sonar-resolver");
      resolverTask.setOutputDirectory(buildDirectory);
      resolverFiles.add(resolverTask.getOutputFile());
    });
  }

  /**
   * Configure Android specific properties and classpath information for a project if it uses the Android Gradle plugin.
   */
  private static void configureAndroid(Project project, Map<String, AndroidConfig> androidConfigMap, TaskProvider<SonarResolverTask> resolverTaskProvider) {
    try {
      if (AndroidConfig.usesAndroidGradlePlugin9OrGreater()) {
        SonarUtils.ANDROID_PLUGIN_IDS.forEach(pluginId ->
          project.getPlugins().withId(pluginId, plugin -> {
            AndroidConfig androidConfig = AndroidConfig.of(project);
            androidConfigMap.put(project.getPath(), androidConfig);
            resolverTaskProvider.configure(resolverTask -> {
              resolverTask.setMainLibraries(project.provider(androidConfig::getMainLibraries));
              resolverTask.setTestLibraries(project.provider(androidConfig::getTestLibraries));
              resolverTask.setAndroidSources(project.provider(androidConfig::getAndroidSources));
              resolverTask.setAndroidTests(project.provider(androidConfig::getAndroidTests));
              resolverTask.mustRunAfter(androidConfig.getTasks());
            });
          })
        );
      } else {
        resolverTaskProvider.configure(resolverTask -> {
          resolverTask.setMainLibraries(project.provider(() -> LegacyAndroidConfig.findMainLibraries(project)));
          resolverTask.setTestLibraries(project.provider(() -> LegacyAndroidConfig.findTestLibraries(project)));
          resolverTask.mustRunAfter(getAndroidTasks(project));
        });
      }
    } catch (NoClassDefFoundError ignored) {
      // The Android plugin is not available in the project, so we do not configure Android.
    }
  }

  private static FileCollection querySourceSet(Project project, String sourceSetName) {
    var sourceSets = SonarUtils.getSourceSets(project);
    if (sourceSets == null) {
      return project.files();
    }
    var set = sourceSets.findByName(sourceSetName);
    return set == null ? project.files() : set.getCompileClasspath();
  }

  private static void configureTask(SonarTask sonarTask, Project project, Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap,
    Map<String, AndroidConfig> androidConfigMap) {
    Provider<ComputedProperties> computedPropertiesProvider =
      project.provider(() -> new SonarPropertyComputer(actionBroadcastMap, androidConfigMap, project).computeSonarProperties());
    Provider<Map<String, String>> conventionProvider = computedPropertiesProvider.map(computed ->
      computed.properties.entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue()))
    );
    Provider<Set<String>> userDefinedKeysProvider = computedPropertiesProvider.map(computed -> computed.userDefinedKeys);

    if (isGradleVersionGreaterOrEqualTo("6.1")) {
      MapProperty<String, String> mapProperty = project.getObjects().mapProperty(String.class, String.class);
      mapProperty.convention(conventionProvider);
      mapProperty.finalizeValueOnRead();
      sonarTask.setProperties(mapProperty, userDefinedKeysProvider);
    } else {
      sonarTask.setProperties(conventionProvider, userDefinedKeysProvider);
    }

    sonarTask.mustRunAfter(getJavaCompileTasks(project));
    sonarTask.mustRunAfter(getJavaTestTasks(project));
    sonarTask.mustRunAfter(getJacocoTasks(project));
    sonarTask.dependsOn(getClassPathResolverTasks(project));
  }

  private static boolean isGradleVersionGreaterOrEqualTo(String version) {
    return GradleVersion.current().compareTo(GradleVersion.version(version)) >= 0;
  }

  private static Callable<Iterable<? extends Task>> getJacocoTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> p.getPlugins().hasPlugin(JacocoPlugin.class) && SonarUtils.notSkipped(p))
      .map(p -> p.getTasks().withType(JacocoReport.class))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  private static Callable<Iterable<? extends Task>> getJavaTestTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> p.getPlugins().hasPlugin(JavaPlugin.class) && SonarUtils.notSkipped(p))
      .map(p -> p.getTasks().getByName(JavaPlugin.TEST_TASK_NAME))
      .collect(Collectors.toList());
  }

  private static Callable<Iterable<? extends Task>> getJavaCompileTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> p.getPlugins().hasPlugin(JavaPlugin.class) && SonarUtils.notSkipped(p))
      .flatMap(p -> Stream.of(p.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME), p.getTasks().getByName(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME)))
      .collect(Collectors.toList());
  }

  private static Callable<Iterable<? extends Task>> getClassPathResolverTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .map(p -> p.getTasks().getByName(SonarResolverTask.TASK_NAME))
      .collect(Collectors.toList());
  }

  private static Callable<Iterable<? extends Task>> getAndroidTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> isAndroidProject(p) && SonarUtils.notSkipped(p))
      .map(p -> {
        LegacyAndroidConfig.AndroidVariantAndExtension androidVariantAndExtension = LegacyAndroidConfig.findVariantAndExtension(p, SonarUtils.getConfiguredAndroidVariant(p));

        List<Task> allTasks = new ArrayList<>();
        if (androidVariantAndExtension != null && androidVariantAndExtension.getVariant() != null) {
          String variantName = SonarUtils.capitalize(androidVariantAndExtension.getVariant().getName());
          final String compileTaskPrefix = "compile" + variantName;
          boolean unitTestTaskDepAdded = SonarUtils.addTaskByName(p, compileTaskPrefix + "UnitTestJavaWithJavac", allTasks);
          boolean androidTestTaskDepAdded = SonarUtils.addTaskByName(p, compileTaskPrefix + "AndroidTestJavaWithJavac", allTasks);
          // Unit test compilation and android test compilation tasks already depend on main code compilation, so we don't add a useless dependency
          // that would lead to run the main compilation task several times.
          if (!unitTestTaskDepAdded && !androidTestTaskDepAdded) {
            SonarUtils.addTaskByName(p, compileTaskPrefix + "JavaWithJavac", allTasks);
          }

          final String testTaskPrefix = "test" + variantName;
          SonarUtils.addTaskByName(p, testTaskPrefix + "UnitTest", allTasks);
        }
        return allTasks;
      })
      .flatMap(List::stream)
      .collect(Collectors.toList());
  }

  @Override
  public void apply(Project project) {
    // Don't try to see if the task was added to any project in the hierarchy. If you do it, it will try to recursively resolve the configuration of all
    // the projects, failing if a project has a sonarqube configuration since the extension wasn't added to it yet.
    if (project.getExtensions().findByName(SonarExtension.SONAR_EXTENSION_NAME) == null) {
      Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap = new HashMap<>();
      Map<String, AndroidConfig> androidConfigMap = new HashMap<>();

      Set<File> resolverFiles = configureAllProjects(project, actionBroadcastMap, androidConfigMap);

      LOGGER.debug("Adding '{}' task to '{}'", SonarExtension.SONAR_DEPRECATED_TASK_NAME, project);
      TaskContainer tasks = project.getTasks();
      tasks.register(SonarExtension.SONAR_DEPRECATED_TASK_NAME, SonarTask.class, task -> {
        task.setDescription("Analyzes " + project + " and its subprojects with Sonar. This task is deprecated. Use 'sonar' instead.");
        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        task.setResolverFiles(resolverFiles);
        task.setBuildSonar(project.getLayout().getBuildDirectory().dir("sonar"));
        configureTask(task, project, actionBroadcastMap, androidConfigMap);
      });

      LOGGER.debug("Adding '{}' task to '{}'", SonarExtension.SONAR_TASK_NAME, project);
      tasks.register(SonarExtension.SONAR_TASK_NAME, SonarTask.class, task -> {
        task.setDescription("Analyzes " + project + " and its subprojects with Sonar.");
        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        task.setResolverFiles(resolverFiles);
        task.setBuildSonar(project.getLayout().getBuildDirectory().dir("sonar"));
        configureTask(task, project, actionBroadcastMap, androidConfigMap);
      });
    }
  }
}
