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

import com.android.build.api.dsl.BaseFlavor;
import com.android.build.api.dsl.CommonExtension;
import com.android.build.api.dsl.TestedExtension;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.AndroidVersion;
// Note: SourceDirectories.Flat and Sources are NOT imported here because they don't exist
// in older AGP versions (e.g. 7.1). They are accessed via reflection-free code in a separate
// method that catches NoClassDefFoundError.
import com.android.build.api.variant.Variant;
import com.android.build.api.variant.VariantSelector;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
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

  private static final String[] ANDROID_PLUGIN_IDS = {
    "com.android.application",
    "com.android.library",
    "com.android.test",
    "com.android.dynamic-feature"
  };

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

      // Register AndroidConfigCollectorTask per Android subproject (AGP 9+ support)
      registerAndroidConfigCollectorTasks(project);

      List<File> resolverFiles = registerAndConfigureResolverTasks(project);

      TaskContainer tasks = project.getTasks();
      tasks.register(SonarExtension.SONAR_DEPRECATED_TASK_NAME, SonarTask.class, task -> {
        task.setDescription("Analyzes " + project + " and its subprojects with Sonar. This task is deprecated. Use 'sonar' instead.");
        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        task.setResolverFiles(resolverFiles);
        task.setBuildSonar(project.getLayout().getBuildDirectory().dir("sonar"));
        configureTask(task, project, actionBroadcastMap);
      });

      tasks.register(SonarExtension.SONAR_TASK_NAME, SonarTask.class, task -> {
        task.setDescription("Analyzes " + project + " and its subprojects with Sonar.");
        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        task.setResolverFiles(resolverFiles);
        task.setBuildSonar(project.getLayout().getBuildDirectory().dir("sonar"));
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
    var androidTasks = getAndroidTasks(topLevelProject);

    topLevelProject.getAllprojects().forEach(target ->
      target.getTasks().register(SonarResolverTask.TASK_NAME, SonarResolverTask.class, task -> {
        Provider<Boolean> skipProject = target.provider(() -> isSkipped(target));

        task.setDescription(SonarResolverTask.TASK_DESCRIPTION);
        task.setSkipProject(skipProject);
        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        if (target == topLevelProject) {
          task.setTopLevelProject(true);
        }
        task.setProjectName(SonarUtils.constructPrefixedProjectName(target.getPath()));

        Provider<FileCollection> compile = target.provider(() -> querySourceSet(target, SourceSet.MAIN_SOURCE_SET_NAME));
        Provider<FileCollection> test = target.provider(() -> querySourceSet(target, SourceSet.TEST_SOURCE_SET_NAME));
        task.setCompileClasspath(compile);
        task.setTestCompileClasspath(test);

        if (isAndroidProject(target)) {
          task.setMainLibraries(target.provider(() -> AndroidUtils.findMainLibraries(target)));
          task.setTestLibraries(target.provider(() -> AndroidUtils.findTestLibraries(target)));
        } else {
          task.setMainLibraries(target.provider(() -> target.files(SonarUtils.getRuntimeJars())));
          task.setTestLibraries(target.provider(() -> target.files(SonarUtils.getRuntimeJars())));
        }
        DirectoryProperty buildDirectory = target.getLayout().getBuildDirectory();
        File localSonarResolver = new File(buildDirectory.getAsFile().get(), "sonar-resolver");
        localSonarResolver.mkdirs();
        task.setOutputDirectory(localSonarResolver);
        resolverFiles.add(task.getOutputFile());
        // Android uses JetifyTransform to translate and ensure compatibility for specific deprecated libraries.
        // Therefore, we must wait for this transform to complete before collecting the classpath.
        task.mustRunAfter(androidTasks);

        // Resolver depends on collector so variant data is available when resolving libraries
        task.dependsOn(getAndroidConfigCollectorTasks(topLevelProject));
      })
    );
    return resolverFiles;
  }

  private static FileCollection querySourceSet(Project project, String sourceSetName) {
    var sourceSets = SonarUtils.getSourceSets(project);
    if (sourceSets == null) {
      return project.files();
    }
    var set = sourceSets.findByName(sourceSetName);
    return set == null ? project.files() : set.getCompileClasspath();
  }

  private static void addExtensions(Project project, String name, Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap) {
    project.getAllprojects().forEach(p -> {
      LOGGER.debug("Adding " + name + " extension to " + p);
      ActionBroadcast<SonarProperties> actionBroadcast = addBroadcaster(actionBroadcastMap, p);
      p.getExtensions().create(name, SonarExtension.class, actionBroadcast);
    });
  }

  private static void configureTask(SonarTask sonarTask, Project project, Map<String, ActionBroadcast<SonarProperties>> actionBroadcastMap) {
    Provider<ComputedProperties> computedPropertiesProvider = project.provider(() -> new SonarPropertyComputer(actionBroadcastMap, project).computeSonarProperties());
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
    sonarTask.mustRunAfter(getAndroidTasks(project));
    sonarTask.mustRunAfter(getJavaTestTasks(project));
    sonarTask.mustRunAfter(getJacocoTasks(project));
    sonarTask.dependsOn(getAndroidConfigCollectorTasks(project));
    sonarTask.dependsOn(getClassPathResolverTask(project));
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

  private static Callable<Iterable<? extends Task>> getAndroidConfigCollectorTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> p.getTasks().getNames().contains(AndroidConfigCollectorTask.TASK_NAME))
      .map(p -> p.getTasks().getByName(AndroidConfigCollectorTask.TASK_NAME))
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

  /**
   * must run after compile to have access to class files
   * must run after test to have access to test reports
   */
  private static Callable<Iterable<? extends Task>> getAndroidTasks(Project project) {
    return () -> project.getAllprojects().stream()
      .filter(p -> isAndroidProject(p) && notSkipped(p))
      .map(p -> {
        String variantName = AndroidUtils.getSelectedVariantName(p, getConfiguredAndroidVariant(p));

        List<Task> allTasks = new ArrayList<>();
        if (variantName != null) {
          final String compileTaskPrefix = "compile" + capitalize(variantName);
          boolean unitTestTaskDepAdded = addTaskByName(p, compileTaskPrefix + "UnitTestJavaWithJavac", allTasks);
          boolean androidTestTaskDepAdded = addTaskByName(p, compileTaskPrefix + "AndroidTestJavaWithJavac", allTasks);
          // unit test compile and android test compile tasks already depends on main code compile so don't add a useless dependency
          // that would lead to run main compile task several times
          if (!unitTestTaskDepAdded && !androidTestTaskDepAdded) {
            addTaskByName(p, compileTaskPrefix + "JavaWithJavac", allTasks);
          }

          final String testTaskPrefix = "test" + capitalize(variantName);
          addTaskByName(p, testTaskPrefix + "UnitTest", allTasks);
        }
        return allTasks;
      })
      .flatMap(List::stream)
      .collect(Collectors.toList());
  }

  // ------------------------------------------------------------------
  //  AndroidConfigCollectorTask registration + onVariants wiring
  // ------------------------------------------------------------------

  /**
   * For every (sub-)project, listen for Android plugin application via {@code plugins.withId()}.
   * When an Android plugin is applied, register an {@link AndroidConfigCollectorTask} and wire
   * {@code onVariants} callbacks so variant metadata flows into the task during configuration.
   */
  private static void registerAndroidConfigCollectorTasks(Project topLevelProject) {
    topLevelProject.getAllprojects().forEach(target -> {
      for (String pluginId : ANDROID_PLUGIN_IDS) {
        target.getPlugins().withId(pluginId, plugin -> {
          if (target.getTasks().getNames().contains(AndroidConfigCollectorTask.TASK_NAME)) {
            return; // already registered
          }
          // Create a shared collector that onVariants populates eagerly during config.
          // The actual task is registered lazily to avoid triggering early project evaluation.
          AndroidConfigCollectorTask.SharedCollector collector = new AndroidConfigCollectorTask.SharedCollector();

          // Store collector so AndroidUtils can access in-memory data during config phase
          target.getExtensions().getExtraProperties().set(AndroidUtils.EXTRA_PROP_COLLECTOR_TASK, collector);

          // onVariants must be registered eagerly during configuration phase
          wireOnVariantsCallback(target, collector);

          // Boot classpath, testBuildType, minSdks are wired into the task lazily
          target.getTasks().register(AndroidConfigCollectorTask.TASK_NAME, AndroidConfigCollectorTask.class, task -> {
            task.setDescription(AndroidConfigCollectorTask.TASK_DESCRIPTION);
            task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);

            File outputDir = new File(target.getLayout().getBuildDirectory().getAsFile().get(), "sonar-android-config");
            outputDir.mkdirs();
            task.setOutputDirectory(outputDir);

            // Transfer eagerly collected variant data into the task
            task.setSharedCollector(collector);

            wireBootClasspath(target, task);
            wireTestBuildType(target, task);
            wireMinSdksFromExtension(target, task);
          });
        });
      }
    });
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void wireBootClasspath(Project target, AndroidConfigCollectorTask task) {
    AndroidComponentsExtension androidComponents = target.getExtensions().findByType(AndroidComponentsExtension.class);
    if (androidComponents == null) {
      return;
    }
    Provider<List<RegularFile>> bootCp = androidComponents.getSdkComponents().getBootClasspath();
    Provider<List<File>> provider = bootCp.map(files ->
      files.stream().map(RegularFile::getAsFile).collect(Collectors.toList()));
    task.setBootClasspathProvider(provider);
  }

  private static void wireTestBuildType(Project target, AndroidConfigCollectorTask task) {
    Object androidExt = target.getExtensions().findByName("android");
    if (androidExt instanceof TestedExtension) {
      task.setTestBuildType(((TestedExtension) androidExt).getTestBuildType());
    }
  }

  @SuppressWarnings("rawtypes")
  private static void wireMinSdksFromExtension(Project target, AndroidConfigCollectorTask task) {
    Object androidExt = target.getExtensions().findByName("android");
    if (!(androidExt instanceof CommonExtension)) {
      return;
    }
    CommonExtension commonExt = (CommonExtension) androidExt;

    Integer minSdk = commonExt.getDefaultConfig().getMinSdk();
    if (minSdk != null) {
      task.addMinSdk(minSdk);
    }

    for (Object flavor : commonExt.getProductFlavors()) {
      Integer flavorMinSdk = ((BaseFlavor) flavor).getMinSdk();
      if (flavorMinSdk != null) {
        task.addMinSdk(flavorMinSdk);
      }
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void wireOnVariantsCallback(Project target, AndroidConfigCollectorTask.SharedCollector collector) {
    AndroidComponentsExtension androidComponents = target.getExtensions().findByType(AndroidComponentsExtension.class);
    if (androidComponents == null) {
      return;
    }
    VariantSelector selector = androidComponents.selector().all();
    androidComponents.onVariants(selector, variant -> {
      String name = ((Variant) variant).getName();
      String buildType = ((Variant) variant).getBuildType();
      Integer minSdk = null;
      try {
        AndroidVersion minSdkVersion = ((Variant) variant).getMinSdk();
        if (minSdkVersion != null) {
          minSdk = minSdkVersion.getApiLevel();
        }
      } catch (NoSuchMethodError e) {
        // Variant.getMinSdk() doesn't exist in older AGP (e.g. 7.1)
      }
      collector.addVariant(name, buildType, minSdk);
      wireVariantSourceProviders((Variant) variant, name, collector);
    });
  }

  /**
   * Captures source directory providers from {@code variant.getSources()}.
   * Only safe source types are collected (java, kotlin, resources, aidl).
   * Res and manifests are excluded because their providers depend on tasks that may not have run yet;
   * they are added convention-based in {@link AndroidUtils} instead.
   */
  /**
   * Captures source directory providers from {@code variant.getSources()}.
   * Delegates to {@link AndroidVariantSourcesCollector} which is in a separate class because
   * it references {@code Sources} / {@code SourceDirectories.Flat} — types that don't exist
   * in AGP &lt; 7.2. The separate class is only loaded when needed, so older AGP won't fail.
   */
  private static void wireVariantSourceProviders(Variant variant, String variantName, AndroidConfigCollectorTask.SharedCollector collector) {
    try {
      AndroidVariantSourcesCollector.collect(variant, variantName, collector);
    } catch (NoClassDefFoundError | NoSuchMethodError e) {
      // Sources / SourceDirectories.Flat / Variant.getSources() don't exist in older AGP (e.g. 7.1)
      LOGGER.debug("variant.getSources() not available: {}", e.getMessage());
    }
  }
}
