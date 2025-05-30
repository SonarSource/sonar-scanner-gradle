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

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Action;

public class ActionBroadcast<T> implements Action<T> {

  private final List<Action<? super T>> actions = new ArrayList<>();

  public void add(Action<? super T> action) {
    actions.add(action);
  }

  @Override
  public void execute(T t) {
    actions.forEach(a -> a.execute(t));
  }

}
