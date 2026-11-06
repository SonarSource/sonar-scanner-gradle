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
package org.sonarqube.gradle

final class RepoxRepositories {
    private RepoxRepositories() {
    }

    static String projectRepositories(String publicRepositories) {
        return '''
            def repoxBaseUrl = System.getenv('ARTIFACTORY_RESOLVE_URL') ?: System.getenv('ARTIFACTORY_URL')
            def repoxRepositoryUrl = (repoxBaseUrl ? repoxBaseUrl.replaceAll('/+$', '') + '/' + (System.getenv('SONARSOURCE_REPOSITORY') ?: 'sonarsource') : null)
            if (repoxRepositoryUrl) {
                LOCAL_REPOSITORIES
                def repox = findByName('Repox') ?: maven {
                    name = 'Repox'
                    def artifactoryUsername = System.getenv('ARTIFACTORY_USERNAME') ?: project.findProperty('artifactoryUsername')
                    def artifactoryPassword = System.getenv('ARTIFACTORY_ACCESS_TOKEN') ?: System.getenv('ARTIFACTORY_PASSWORD') ?: project.findProperty('artifactoryPassword')
                    if (artifactoryUsername && artifactoryPassword) {
                        credentials {
                            username = artifactoryUsername
                            password = artifactoryPassword
                        }
                    }
                }
                repox.url = uri(repoxRepositoryUrl)
            } else {
                PUBLIC_REPOSITORIES
            }
        '''.stripIndent().replace('PUBLIC_REPOSITORIES', publicRepositories)
            .replace('LOCAL_REPOSITORIES', publicRepositories.contains('mavenLocal()') ? 'mavenLocal()' : '')
    }

    static String pluginRepositories(String publicRepositories) {
        return '''
            def repoxBaseUrl = System.getenv('ARTIFACTORY_RESOLVE_URL') ?: System.getenv('ARTIFACTORY_URL')
            def repoxRepositoryUrl = (repoxBaseUrl ? repoxBaseUrl.replaceAll('/+$', '') + '/' + (System.getenv('SONARSOURCE_REPOSITORY') ?: 'sonarsource') : null)
            if (repoxRepositoryUrl) {
                LOCAL_REPOSITORIES
                def repox = findByName('Repox') ?: maven {
                    name = 'Repox'
                    def artifactoryUsername = System.getenv('ARTIFACTORY_USERNAME') ?: settings.extensions.extraProperties.properties['artifactoryUsername']
                    def artifactoryPassword = System.getenv('ARTIFACTORY_ACCESS_TOKEN') ?: System.getenv('ARTIFACTORY_PASSWORD') ?: settings.extensions.extraProperties.properties['artifactoryPassword']
                    if (artifactoryUsername && artifactoryPassword) {
                        credentials {
                            username = artifactoryUsername
                            password = artifactoryPassword
                        }
                    }
                }
                repox.url = uri(repoxRepositoryUrl)
            } else {
                PUBLIC_REPOSITORIES
            }
        '''.stripIndent().replace('PUBLIC_REPOSITORIES', publicRepositories)
            .replace('LOCAL_REPOSITORIES', publicRepositories.contains('mavenLocal()') ? 'mavenLocal()' : '')
    }
}
