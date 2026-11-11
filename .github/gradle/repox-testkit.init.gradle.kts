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

// The Repox init script of config-gradle also adds repositories to projects, which FAIL_ON_PROJECT_REPOS rejects
// in TestKit fixtures. PREFER_SETTINGS keeps the settings repositories and ignores the project ones.
settingsEvaluated {
    dependencyResolutionManagement {
        if (repositoriesMode.get() == RepositoriesMode.FAIL_ON_PROJECT_REPOS) {
            repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
        }
    }
}
