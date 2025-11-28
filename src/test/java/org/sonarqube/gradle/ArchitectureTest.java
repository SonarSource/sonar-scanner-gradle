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

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

  private static final JavaClasses PLUGIN_CLASSES = new ClassFileImporter()
    .importPackages("org.sonarqube.gradle");

  @Test
  void plugin_code_should_not_use_system_getenv() {
    ArchRule rule = noClasses()
      .that().resideInAPackage("org.sonarqube.gradle..")
      .should().callMethod(System.class, "getenv")
      .because("System.getenv() captures all environment variables which doesn't play nicely with Gradle's configuration cache. "
        + "Use explicit environment variable names with project.getProviders().environmentVariable() "
        + "or project.getProviders().environmentVariablesPrefixedBy() instead.");

    rule.check(PLUGIN_CLASSES);
  }

  @Test
  void plugin_code_should_not_use_system_getproperties() {
    ArchRule rule = noClasses()
      .that().resideInAPackage("org.sonarqube.gradle..")
      .should(notCallSystemGetPropertiesExceptInFallbackMethods())
      .because("System.getProperties() captures all system properties which doesn't play nicely with Gradle's configuration cache. "
        + "Use explicit system property names with project.getProviders().systemProperty() or project.getProviders().systemPropertiesPrefixedBy() instead. "
        + "System.getProperties() is only allowed in methods with 'fallback' in their name for older Gradle versions.");

    rule.check(PLUGIN_CLASSES);
  }

  private static ArchCondition<JavaClass> notCallSystemGetPropertiesExceptInFallbackMethods() {
    return new ArchCondition<>("not call System.getProperties() except in methods with 'fallback' in their name") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getMethodCallsFromSelf().stream()
          .filter(call -> call.getTargetOwner().isEquivalentTo(System.class) && call.getName().equals("getProperties"))
          .filter(call -> !call.getOriginOwner().getName().toLowerCase().contains("fallback"))
          .forEach(call -> {
            String message = String.format(
              "Method %s.%s() calls System.getProperties() but is not in a method with 'fallback' in its name",
              javaClass.getSimpleName(),
              call.getOriginOwner().getName());
            events.add(SimpleConditionEvent.violated(call, message));
          });
      }
    };
  }
}