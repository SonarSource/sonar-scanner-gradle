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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
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
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.gradle.util.GradleVersion;

import static org.sonarqube.gradle.SonarUtils.isAndroidProject;

/**
 * A plugin for analyzing projects with the <a href="http://redirect.sonarsource.com/doc/analyzing-with-sq-gradle.html">SonarScanner for Gradle</a>.
 * When applied to a project, both the project itself and its subprojects will be analyzed (in a single run).
 * <p>
 * Under Gradle Isolated Projects, a project can no longer configure its subprojects: the plugin must be applied to
 * every project, which {@link SonarSettingsPlugin} does from the settings script. Each project then publishes its own
 * data and only the root project runs the analysis.
 */
public class SonarQubePlugin implements Plugin<Project> {
  private static final Logger LOGGER = Logging.getLogger(SonarQubePlugin.class);
  private static final String METADATA_ELEMENTS = "sonarMetadataElements";
  private static final String METADATA_INPUTS = "sonarMetadataInputs";
  private static final String RESOLVER_ELEMENTS = "sonarResolverElements";
  private static final String RESOLVER_INPUTS = "sonarResolverInputs";
  private static final String ANALYSIS_DESCRIPTION_PREFIX = "Analyzes ";
  private static final String SONAR_BUILD_DIRECTORY = "sonar";

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
      resolverTask.getSkipProject().convention(project.provider(() -> SonarUtils.isSkipped(project)));
      resolverTask.getProjectName().convention(SonarUtils.constructPrefixedProjectName(project.getPath()));
      resolverTask.getTopLevelProject().convention(false);
      if (project == topLevelProject && (!IsolatedProjects.isEnabled(project.getGradle()) || project.getPath().equals(":"))) {
        resolverTask.getTopLevelProject().set(true);
      }
      resolverTask.setCompileClasspath(project.provider(() -> querySourceSet(project, SourceSet.MAIN_SOURCE_SET_NAME)));
      resolverTask.setTestCompileClasspath(project.provider(() -> querySourceSet(project, SourceSet.TEST_SOURCE_SET_NAME)));
      resolverTask.mustRunAfter(getJavaCompileTasksForProject(project));
      if (!isAndroidProject(project)) {
        resolverTask.getMainLibraries().from(project.provider(() -> project.files(SonarUtils.getRuntimeJars())));
        resolverTask.getTestLibraries().from(project.provider(() -> project.files(SonarUtils.getRuntimeJars())));
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
              resolverTask.getMainLibraries().setFrom(project.provider(androidConfig::getMainLibraries));
              resolverTask.getTestLibraries().setFrom(project.provider(androidConfig::getTestLibraries));
              resolverTask.getAndroidSources().from(project.provider(androidConfig::getAndroidSources));
              resolverTask.getAndroidTests().from(project.provider(androidConfig::getAndroidTests));
              resolverTask.mustRunAfter(androidConfig.getTasks());
            });
          })
        );
      } else {
        resolverTaskProvider.configure(resolverTask -> {
          resolverTask.setLegacyMainLibraries(project.provider(() -> LegacyAndroidConfig.findMainLibraries(project)));
          resolverTask.setLegacyTestLibraries(project.provider(() -> LegacyAndroidConfig.findTestLibraries(project)));
          resolverTask.mustRunAfter(IsolatedProjects.isEnabled(project.getGradle()) ? getAndroidTasksForProject(project) : getAndroidTasks(project));
        });
      }
    } catch (NoClassDefFoundError ignored) {
      // The Android Gradle plugin is not visible from the class loader of this plugin.
      reportAndroidPluginNotVisible(project);
    }
  }

  /**
   * The Android Gradle plugin classes are invisible either because the project does not use Android, or because the
   * Android Gradle plugin is declared in a build script while this plugin is declared in the settings script, which
   * loads it in a parent class loader. In the second case, analyzing the project without its Android configuration
   * would silently produce a wrong analysis.
   */
  private static void reportAndroidPluginNotVisible(Project project) {
    SonarUtils.ANDROID_PLUGIN_IDS.forEach(pluginId -> project.getPluginManager().withPlugin(pluginId, applied -> {
      String message = IsolatedProjectsMigrationGuide.androidPluginNotVisibleMessage(project.getPath(), pluginId, PluginVersion.get());
      if (IsolatedProjects.isEnabled(project.getGradle())) {
        throw new InvalidUserDataException(message);
      }
      LOGGER.warn(message);
    }));
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
    // The Sonar task depends on the Sonar resolver tasks for every project, which must themselves run after all Android tasks, so the Sonar task doesn't need to explicitly depend
    // on Android tasks.
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

  private static Callable<Iterable<? extends Task>> getJavaCompileTasksForProject(Project project) {
    return () -> {
      if (!project.getPlugins().hasPlugin(JavaPlugin.class)) {
        return new ArrayList<>();
      }
      return Stream.of(project.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME), project.getTasks().getByName(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME))
        .collect(Collectors.toList());
    };
  }

  private static Callable<Iterable<? extends Task>> getClassPathResolverTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .map(p -> p.getTasks().getByName(SonarResolverTask.TASK_NAME))
      .collect(Collectors.toList());
  }

  private static Callable<Iterable<? extends Task>> getJavaTestTasksForProject(Project project) {
    return () -> project.getPlugins().hasPlugin(JavaPlugin.class) && SonarUtils.notSkipped(project)
      ? List.of(project.getTasks().getByName(JavaPlugin.TEST_TASK_NAME)) : Collections.emptyList();
  }

  private static Callable<Iterable<? extends Task>> getJavaResourceTasksForProject(Project project) {
    return () -> project.getPlugins().hasPlugin(JavaPlugin.class) && SonarUtils.notSkipped(project)
      ? List.of(project.getTasks().getByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME),
        project.getTasks().getByName(JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME)) : Collections.emptyList();
  }

  private static Callable<Iterable<? extends Task>> getJacocoTasksForProject(Project project) {
    return () -> project.getPlugins().hasPlugin(JacocoPlugin.class) && SonarUtils.notSkipped(project)
      ? new ArrayList<>(project.getTasks().withType(JacocoReport.class)) : Collections.emptyList();
  }

  private static Callable<Iterable<? extends Task>> getAndroidTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> isAndroidProject(p) && SonarUtils.notSkipped(p))
      .map(SonarQubePlugin::queryAndroidTasks)
      .flatMap(List::stream)
      .collect(Collectors.toList());
  }

  private static Callable<Iterable<? extends Task>> getAndroidTasksForProject(Project project) {
    return () -> isAndroidProject(project) && SonarUtils.notSkipped(project)
      ? queryAndroidTasks(project) : Collections.emptyList();
  }

  private static List<Task> queryAndroidTasks(Project p) {
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
  }

  private static Configuration createOutgoing(Project project, String name) {
    Configuration configuration = project.getConfigurations().create(name);
    configuration.setCanBeConsumed(true);
    configuration.setCanBeResolved(false);
    return configuration;
  }

  /**
   * Lenient, so that a subproject without the plugin does not fail dependency resolution with a generic Gradle error.
   * {@link SonarTask} reports the missing projects itself, with migration instructions.
   */
  private static FileCollection createLenientIncoming(Project project, String name, String elements, Collection<String> childPaths) {
    Configuration configuration = project.getConfigurations().create(name);
    configuration.setCanBeConsumed(false);
    configuration.setCanBeResolved(true);
    for (String path : childPaths) {
      project.getDependencies().add(name, project.getDependencies().project(Map.of("path", path, "configuration", elements)));
    }
    return configuration.getIncoming().artifactView(view -> view.setLenient(true)).getFiles();
  }

  /**
   * Under Isolated Projects every project applies the plugin to itself (see {@link SonarSettingsPlugin}) and publishes its
   * own Sonar data. Only the root project aggregates that data and owns the analysis task, so that running
   * {@code ./gradlew sonar} starts a single analysis.
   */
  private static void configureIsolatedProjects(Project project) {
    IsolatedProducer producer = registerIsolatedProducer(project);
    if (project.getPath().equals(":")) {
      registerIsolatedAnalysisTasks(project, producer);
      IsolatedProjectsMigrationHint.register(project);
    }
  }

  private static IsolatedProducer registerIsolatedProducer(Project project) {
    Map<String, ActionBroadcast<SonarProperties>> broadcasters = new HashMap<>();
    Map<String, AndroidConfig> androidConfigs = new HashMap<>();
    registerSonarExtensions(project, broadcasters);

    TaskProvider<SonarResolverTask> resolver = registerResolverTask(project, project, new HashSet<>());
    configureAndroid(project, androidConfigs, resolver);
    resolver.configure(task -> {
      task.mustRunAfter(getJavaTestTasksForProject(project));
      task.mustRunAfter(getJavaResourceTasksForProject(project));
      task.mustRunAfter(getJacocoTasksForProject(project));
      // Artifact tasks such as Kotlin Multiplatform's jvmJar may be selected alongside sonar.
      // Keep the ordering local to this project; the root sonar task consumes this resolver's artifact.
      task.mustRunAfter(project.getTasks().withType(Jar.class));
    });

    TaskProvider<SonarMetadataTask> metadata = registerMetadataTask(project, broadcasters, androidConfigs);
    createOutgoing(project, METADATA_ELEMENTS).getOutgoing()
      .artifact(metadata.flatMap(SonarMetadataTask::getMetadataFile), artifact -> artifact.builtBy(metadata));
    createOutgoing(project, RESOLVER_ELEMENTS).getOutgoing()
      .artifact(project.provider(() -> resolver.get().getOutputFile()), artifact -> artifact.builtBy(resolver));

    Provider<Map<String, String>> sensitiveProperties = project.provider(() ->
      new SonarPropertyComputer(broadcasters, androidConfigs, project).computeLocalSensitiveProperties());
    return new IsolatedProducer(resolver, metadata, sensitiveProperties);
  }

  private static TaskProvider<SonarMetadataTask> registerMetadataTask(Project project, Map<String, ActionBroadcast<SonarProperties>> broadcasters,
    Map<String, AndroidConfig> androidConfigs) {
    Provider<ComputedProperties> moduleProperties = project.provider(() ->
      new SonarPropertyComputer(broadcasters, androidConfigs, project).computeLocalSonarProperties(false));
    Provider<ComputedProperties> rootProperties = project.provider(() ->
      new SonarPropertyComputer(broadcasters, androidConfigs, project).computeLocalSonarProperties(true));
    return project.getTasks().register(SonarMetadataTask.TASK_NAME, SonarMetadataTask.class, task -> {
      task.getProjectPath().set(project.getPath());
      task.getProjectDirectory().set(project.getProjectDir().getAbsolutePath());
      task.getBuildFile().set(project.getBuildFile().getAbsolutePath());
      task.getSkipped().set(project.provider(() -> SonarUtils.isSkipped(project)));
      task.getProperties().set(moduleProperties.map(SonarQubePlugin::stringProperties));
      task.getUserDefinedKeys().set(moduleProperties.map(computed -> new ArrayList<>(computed.userDefinedKeys)));
      task.getRootProperties().set(rootProperties.map(SonarQubePlugin::stringProperties));
      task.getRootUserDefinedKeys().set(rootProperties.map(computed -> new ArrayList<>(computed.userDefinedKeys)));
      task.getScanAllSourcesOverridden().set(project.provider(() ->
        new SonarPropertyComputer(broadcasters, androidConfigs, project).hasExplicitSourceOverride()));
      task.getDefaultProjectKey().set(project.provider(() -> defaultProjectKey(project)));
      task.getMetadataFile().set(project.getLayout().getBuildDirectory().file("sonar-metadata/properties.json"));
    });
  }

  private static Map<String, String> stringProperties(ComputedProperties computed) {
    return computed.properties.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> (String) entry.getValue()));
  }

  private static String defaultProjectKey(Project project) {
    if (!project.getPath().equals(":")) {
      return "";
    }
    String group = project.getGroup().toString();
    return group.isEmpty() ? project.getName() : (group + ":" + project.getName());
  }

  private static void registerIsolatedAnalysisTasks(Project root, IsolatedProducer producer) {
    // Reading only project paths is safe in Gradle's isolated mode.
    List<String> childPaths = root.getAllprojects().stream().map(Project::getPath)
      .filter(path -> !path.equals(root.getPath())).sorted().collect(Collectors.toList());
    FileCollection metadataInputs = createLenientIncoming(root, METADATA_INPUTS, METADATA_ELEMENTS, childPaths);
    FileCollection resolverInputs = createLenientIncoming(root, RESOLVER_INPUTS, RESOLVER_ELEMENTS, childPaths);

    TaskContainer tasks = root.getTasks();
    for (String taskName : List.of(SonarExtension.SONAR_TASK_NAME, SonarExtension.SONAR_DEPRECATED_TASK_NAME)) {
      tasks.register(taskName, SonarTask.class, task -> {
        task.setDescription(ANALYSIS_DESCRIPTION_PREFIX + root + " and its subprojects with Sonar."
          + (taskName.equals(SonarExtension.SONAR_DEPRECATED_TASK_NAME) ? " This task is deprecated. Use 'sonar' instead." : ""));
        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        task.setBuildSonar(root.getLayout().getBuildDirectory().dir(SONAR_BUILD_DIRECTORY));
        task.setProperties(root.provider(Collections::emptyMap), root.provider(Collections::emptySet));
        task.setResolverFiles(Collections.emptySet());
        task.setIsolatedAnalysisRootPath(root.getPath());
        task.setIsolatedSensitiveProperties(producer.sensitiveProperties);
        task.getIsolatedExpectedProjectPaths().set(childPaths);
        task.getIsolatedMetadataFiles().from(producer.metadata, metadataInputs);
        task.getIsolatedResolverFiles().from(producer.resolver, resolverInputs);
      });
    }
  }

  /** The tasks and values a project publishes for the isolated analysis. */
  private static final class IsolatedProducer {
    private final TaskProvider<SonarResolverTask> resolver;
    private final TaskProvider<SonarMetadataTask> metadata;
    private final Provider<Map<String, String>> sensitiveProperties;

    private IsolatedProducer(TaskProvider<SonarResolverTask> resolver, TaskProvider<SonarMetadataTask> metadata,
      Provider<Map<String, String>> sensitiveProperties) {
      this.resolver = resolver;
      this.metadata = metadata;
      this.sensitiveProperties = sensitiveProperties;
    }
  }

  @Override
  public void apply(Project project) {
    if (IsolatedProjects.isEnabled(project.getGradle())) {
      configureIsolatedProjects(project);
      return;
    }
    // Don't try to see if the task was added to any project in the hierarchy. If you do it, it will try to recursively resolve the configuration of all
    // the projects, failing if a project has a sonarqube configuration since the extension wasn't added to it yet.
    if (project.getExtensions().findByName(SonarExtension.SONAR_EXTENSION_NAME) == null) {
      Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap = new HashMap<>();
      Map<String, AndroidConfig> androidConfigMap = new HashMap<>();

      Set<File> resolverFiles = configureAllProjects(project, actionBroadcastMap, androidConfigMap);

      LOGGER.debug("Adding '{}' task to '{}'", SonarExtension.SONAR_DEPRECATED_TASK_NAME, project);
      TaskContainer tasks = project.getTasks();
      tasks.register(SonarExtension.SONAR_DEPRECATED_TASK_NAME, SonarTask.class, task -> {
        task.setDescription(ANALYSIS_DESCRIPTION_PREFIX + project + " and its subprojects with Sonar. This task is deprecated. Use 'sonar' instead.");
        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        task.setResolverFiles(resolverFiles);
        task.setBuildSonar(project.getLayout().getBuildDirectory().dir(SONAR_BUILD_DIRECTORY));
        configureTask(task, project, actionBroadcastMap, androidConfigMap);
      });

      LOGGER.debug("Adding '{}' task to '{}'", SonarExtension.SONAR_TASK_NAME, project);
      tasks.register(SonarExtension.SONAR_TASK_NAME, SonarTask.class, task -> {
        task.setDescription(ANALYSIS_DESCRIPTION_PREFIX + project + " and its subprojects with Sonar.");
        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        task.setResolverFiles(resolverFiles);
        task.setBuildSonar(project.getLayout().getBuildDirectory().dir(SONAR_BUILD_DIRECTORY));
        configureTask(task, project, actionBroadcastMap, androidConfigMap);
      });
    }
  }
}
