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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.gradle.api.Action;
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
          // Must use create() (eager) instead of register() (lazy) because onVariants
          // callbacks must be registered during the configuration phase.
          AndroidConfigCollectorTask task = target.getTasks().create(
            AndroidConfigCollectorTask.TASK_NAME, AndroidConfigCollectorTask.class);
          task.setDescription(AndroidConfigCollectorTask.TASK_DESCRIPTION);
          task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);

          // Store reference so AndroidUtils can access in-memory data during config phase
          target.getExtensions().getExtraProperties().set(AndroidUtils.EXTRA_PROP_COLLECTOR_TASK, task);

          File outputDir = new File(target.getLayout().getBuildDirectory().getAsFile().get(), "sonar-android-config");
          outputDir.mkdirs();
          task.setOutputDirectory(outputDir);

          // Wire testBuildType from the "android" extension via reflection
          wireTestBuildType(target, task);

          // Wire defaultConfig + productFlavors minSdk via reflection
          wireMinSdksFromExtension(target, task);

          // Wire boot classpath as a lazy Provider (resolved at execution time)
          wireBootClasspath(target, task);

          // Wire onVariants callback — must happen eagerly during configuration phase
          wireOnVariantsCallback(target, task);
        });
      }
    });
  }

  /**
   * Wraps {@code android.getBootClasspath()} in a lazy Provider so it is only resolved at execution time
   * (when the SDK is guaranteed to be available). The reflection is needed because {@code getBootClasspath()}
   * lives on {@code BaseExtension} (removed in AGP 9) with no replacement in the public API.
   */
  @SuppressWarnings("unchecked")
  private static void wireBootClasspath(Project target, AndroidConfigCollectorTask task) {
    Object androidExt = target.getExtensions().findByName("android");
    if (androidExt == null) {
      return;
    }
    Provider<List<File>> provider = target.provider(() -> {
      try {
        Method m = androidExt.getClass().getMethod("getBootClasspath");
        List<File> result = (List<File>) m.invoke(androidExt);
        return result != null ? result : Collections.emptyList();
      } catch (Exception e) {
        LOGGER.debug("Unable to resolve boot classpath: {}", e.getMessage());
        return Collections.emptyList();
      }
    });
    task.setBootClasspathProvider(provider);
  }

  @SuppressWarnings("unchecked")
  private static void wireTestBuildType(Project target, AndroidConfigCollectorTask task) {
    Object androidExt = target.getExtensions().findByName("android");
    if (androidExt == null) {
      return;
    }
    try {
      Method getTestBuildType = androidExt.getClass().getMethod("getTestBuildType");
      task.setTestBuildType((String) getTestBuildType.invoke(androidExt));
    } catch (Exception e) {
      LOGGER.debug("Unable to get testBuildType: {}", e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private static void wireMinSdksFromExtension(Project target, AndroidConfigCollectorTask task) {
    Object androidExt = target.getExtensions().findByName("android");
    if (androidExt == null) {
      return;
    }
    // defaultConfig.minSdk
    try {
      Method getDefaultConfig = androidExt.getClass().getMethod("getDefaultConfig");
      Object defaultConfig = getDefaultConfig.invoke(androidExt);
      Method getMinSdk = defaultConfig.getClass().getMethod("getMinSdk");
      Object minSdk = getMinSdk.invoke(defaultConfig);
      if (minSdk instanceof Integer) {
        task.addMinSdk((Integer) minSdk);
      }
    } catch (Exception e) {
      LOGGER.debug("Unable to get defaultConfig minSdk: {}", e.getMessage());
    }
    // productFlavors[*].minSdk
    try {
      Method getProductFlavors = androidExt.getClass().getMethod("getProductFlavors");
      Iterable<?> flavors = (Iterable<?>) getProductFlavors.invoke(androidExt);
      for (Object flavor : flavors) {
        try {
          Method getMinSdk = flavor.getClass().getMethod("getMinSdk");
          Object minSdk = getMinSdk.invoke(flavor);
          if (minSdk instanceof Integer) {
            task.addMinSdk((Integer) minSdk);
          }
        } catch (Exception ignored) {
          // individual flavor may not expose minSdk
        }
      }
    } catch (Exception e) {
      LOGGER.debug("Unable to get productFlavors minSdk: {}", e.getMessage());
    }
  }

  /**
   * Calls {@code androidComponents.onVariants(selector.all(), callback)} via reflection
   * so that this class has zero compile-time AGP dependencies.
   */
  @SuppressWarnings("unchecked")
  private static void wireOnVariantsCallback(Project target, AndroidConfigCollectorTask task) {
    Object androidComponents = target.getExtensions().findByName("androidComponents");
    if (androidComponents == null) {
      return;
    }
    try {
      Method selectorMethod = androidComponents.getClass().getMethod("selector");
      Object selector = selectorMethod.invoke(androidComponents);
      Method allMethod = selector.getClass().getMethod("all");
      Object allSelector = allMethod.invoke(selector);

      Method onVariantsMethod = null;
      for (Method m : androidComponents.getClass().getMethods()) {
        if ("onVariants".equals(m.getName()) && m.getParameterCount() == 2
          && m.getParameterTypes()[1].equals(Action.class)) {
          onVariantsMethod = m;
          break;
        }
      }

      if (onVariantsMethod != null) {
        Action<Object> callback = variant -> {
          try {
            String name = (String) variant.getClass().getMethod("getName").invoke(variant);
            String buildType = null;
            try {
              buildType = (String) variant.getClass().getMethod("getBuildType").invoke(variant);
            } catch (Exception ignored) {
            }
            Integer minSdk = null;
            try {
              Object minSdkObj = variant.getClass().getMethod("getMinSdk").invoke(variant);
              if (minSdkObj != null) {
                minSdk = (int) minSdkObj.getClass().getMethod("getApiLevel").invoke(minSdkObj);
              }
            } catch (Exception ignored) {
            }
            task.addVariant(name, buildType, minSdk);

            // Capture source directory providers (java, res, assets, etc.)
            wireVariantSourceProviders(variant, name, task);
          } catch (Exception e) {
            LOGGER.debug("Unable to extract variant data: {}", e.getMessage());
          }
        };
        onVariantsMethod.invoke(androidComponents, allSelector, callback);
      }
    } catch (Exception e) {
      LOGGER.debug("Unable to register onVariants callback: {}", e.getMessage());
    }
  }

  /**
   * Captures source directory providers from {@code variant.getSources()} via reflection.
   * <p>
   * Source types fall into two categories:
   * <ul>
   *   <li>{@code SourceDirectories} (java, aidl, …) — {@code getAll()} returns {@code Provider<List<Directory>>}</li>
   *   <li>{@code LayeredSourceDirectories} (res, assets, …) — {@code getAll()} returns {@code Provider<List<List<Directory>>>}</li>
   * </ul>
   * Both are stored as raw {@code Provider<?>} and flattened in the collector task at execution time.
   */
  private static void wireVariantSourceProviders(Object variant, String variantName, AndroidConfigCollectorTask task) {
    try {
      Object sources = variant.getClass().getMethod("getSources").invoke(variant);
      // Source types whose getAll() can be safely resolved at execution time.
      // getRes() and getManifests() are excluded because their providers depend on tasks
      // (e.g. generateDebugResValues) that may not have run yet. Res and manifest dirs
      // are added convention-based in AndroidUtils instead.
      String[] sourceGetters = {"getJava", "getKotlin", "getResources", "getAidl"};
      for (String getter : sourceGetters) {
        try {
          Object sourceDir = sources.getClass().getMethod(getter).invoke(sources);
          if (sourceDir != null) {
            Method getAllMethod = sourceDir.getClass().getMethod("getAll");
            Provider<?> provider = (Provider<?>) getAllMethod.invoke(sourceDir);
            if (provider != null) {
              task.addVariantSourceProvider(variantName, provider);
            }
          }
        } catch (NoSuchMethodException ignored) {
          // Source type may not exist in this AGP version
        } catch (Exception e) {
          LOGGER.debug("Unable to capture {} source provider for variant '{}': {}", getter, variantName, e.getMessage());
        }
      }
    } catch (Exception e) {
      LOGGER.debug("Unable to capture source providers for variant '{}': {}", variantName, e.getMessage());
    }
  }
}
