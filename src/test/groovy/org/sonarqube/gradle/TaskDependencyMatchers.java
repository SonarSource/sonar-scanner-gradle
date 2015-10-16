/**
 * SonarQube Gradle Plugin
 * Copyright (C) 2015 SonarSource
 * sonarqube@googlegroups.com
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

import org.gradle.api.Buildable;
import org.gradle.api.Task;
import org.hamcrest.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;

public class TaskDependencyMatchers {
    @Factory
    public static Matcher<Task> dependsOn(final String... tasks) {
        return dependsOn(equalTo(new HashSet<String>(Arrays.asList(tasks))));
    }

    @Factory
    public static Matcher<Task> dependsOn(Matcher<? extends Iterable<String>> matcher) {
        return dependsOn(matcher, false);
    }

    @Factory
    public static Matcher<Task> dependsOnPaths(Matcher<? extends Iterable<String>> matcher) {
        return dependsOn(matcher, true);
    }

    private static Matcher<Task> dependsOn(final Matcher<? extends Iterable<String>> matcher, final boolean matchOnPaths) {
        return new BaseMatcher<Task>() {
            public boolean matches(Object o) {
                Task task = (Task) o;
                Set<String> names = new HashSet<String>();
                Set<? extends Task> depTasks = task.getTaskDependencies().getDependencies(task);
                for (Task depTask : depTasks) {
                    names.add(matchOnPaths ? depTask.getPath() : depTask.getName());
                }
                boolean matches = matcher.matches(names);
                if (!matches) {
                    StringDescription description = new StringDescription();
                    matcher.describeTo(description);
                    System.out.println(String.format("expected %s, got %s.", description.toString(), names));
                }
                return matches;
            }

            public void describeTo(Description description) {
                description.appendText("a Task that depends on ").appendDescriptionOf(matcher);
            }
        };
    }

    @Factory
    public static <T extends Buildable> Matcher<T> builtBy(String... tasks) {
        return builtBy(equalTo(new HashSet<String>(Arrays.asList(tasks))));
    }

    @Factory
    public static <T extends Buildable> Matcher<T> builtBy(final Matcher<? extends Iterable<String>> matcher) {
        return new BaseMatcher<T>() {
            public boolean matches(Object o) {
                Buildable task = (Buildable) o;
                Set<String> names = new HashSet<String>();
                Set<? extends Task> depTasks = task.getBuildDependencies().getDependencies(null);
                for (Task depTask : depTasks) {
                    names.add(depTask.getName());
                }
                boolean matches = matcher.matches(names);
                if (!matches) {
                    StringDescription description = new StringDescription();
                    matcher.describeTo(description);
                    System.out.println(String.format("expected %s, got %s.", description.toString(), names));
                }
                return matches;
            }

            public void describeTo(Description description) {
                description.appendText("a Buildable that is built by ").appendDescriptionOf(matcher);
            }
        };
    }
}