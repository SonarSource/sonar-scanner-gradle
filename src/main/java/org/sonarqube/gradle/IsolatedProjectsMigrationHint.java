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

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.flow.BuildWorkResult;
import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

/**
 * Isolated Projects turns two historical Sonar configurations into generic Gradle errors:
 * <ul>
 *   <li>the plugin applied in the root build script only: a subproject build script containing {@code sonar { }}
 *   cannot find the extension, because the root is no longer allowed to register it there;</li>
 *   <li>a {@code sonar { }} block of a subproject written in the root build script, for example in
 *   {@code project(":app") { }}.</li>
 * </ul>
 * This action runs at the end of the build and, only in those cases, prints how to migrate.
 * <p>
 * Only access this class if runtime is Gradle 9.7+, see {@link IsolatedProjects}.
 */
public abstract class IsolatedProjectsMigrationHint implements FlowAction<IsolatedProjectsMigrationHint.Parameters> {
  private static final Logger LOGGER = Logging.getLogger(IsolatedProjectsMigrationHint.class);

  /** Groovy DSL and the two Kotlin compiler message formats for a missing {@code sonar} or {@code sonarqube} extension. */
  private static final List<Pattern> MISSING_EXTENSION_MESSAGES = List.of(
    Pattern.compile("Could not find method (sonar|sonarqube)\\(\\)"),
    Pattern.compile("Unresolved reference:? '?(sonar|sonarqube)\\b"));

  /** Reported by Gradle when a build script configures the {@code sonar} extension of another project. */
  private static final Pattern CROSS_PROJECT_CONFIGURATION_MESSAGE =
    Pattern.compile("cannot access '(?:sonar|sonarqube)' extension on (?:another project '([^']+)'|\\S+)");

  public interface Parameters extends FlowParameters {
    @Input
    Property<BuildWorkResult> getBuildResult();

    @Input
    Property<String> getPluginVersion();
  }

  @Override
  public void execute(Parameters parameters) {
    parameters.getBuildResult().get().getFailure()
      .flatMap(failure -> diagnose(failure, parameters.getPluginVersion().get()))
      .ifPresent(LOGGER::error);
  }

  /** Returns migration instructions when the build failed because of a Sonar configuration that Isolated Projects forbids. */
  static Optional<String> diagnose(Throwable failure, String pluginVersion) {
    for (Throwable current = failure; current != null; current = current.getCause() == current ? null : current.getCause()) {
      Optional<String> hint = diagnoseMessage(current.getMessage(), pluginVersion);
      if (hint.isPresent()) {
        return hint;
      }
      for (Throwable suppressed : current.getSuppressed()) {
        hint = diagnose(suppressed, pluginVersion);
        if (hint.isPresent()) {
          return hint;
        }
      }
    }
    return Optional.empty();
  }

  private static Optional<String> diagnoseMessage(@Nullable String message, String pluginVersion) {
    if (message == null) {
      return Optional.empty();
    }
    Matcher crossProject = CROSS_PROJECT_CONFIGURATION_MESSAGE.matcher(message);
    if (crossProject.find()) {
      return Optional.of(IsolatedProjectsMigrationGuide.crossProjectConfigurationMessage(crossProject.group(1)));
    }
    if (MISSING_EXTENSION_MESSAGES.stream().anyMatch(pattern -> pattern.matcher(message).find())) {
      return Optional.of(IsolatedProjectsMigrationGuide.missingExtensionMessage(pluginVersion));
    }
    return Optional.empty();
  }

  static void register(Project project) {
    project.getObjects().newInstance(Registrar.class).register();
  }

  /** Gradle injects the flow services into managed objects only, so they are obtained through this type. */
  public abstract static class Registrar {
    @Inject
    public Registrar() {
      // Instantiated by Gradle
    }

    @Inject
    protected abstract FlowScope getFlowScope();

    @Inject
    protected abstract FlowProviders getFlowProviders();

    void register() {
      getFlowScope().always(IsolatedProjectsMigrationHint.class, spec -> {
        spec.getParameters().getBuildResult().set(getFlowProviders().getBuildWorkResult());
        spec.getParameters().getPluginVersion().set(PluginVersion.get());
      });
    }
  }
}
