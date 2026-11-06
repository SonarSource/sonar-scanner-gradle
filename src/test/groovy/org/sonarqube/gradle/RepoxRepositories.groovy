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
                def repox = findByName('Repox') ?: maven { name = 'Repox' }
                repox.with {
                    url = uri(repoxRepositoryUrl)
                    credentials(org.gradle.api.credentials.HttpHeaderCredentials) {
                        name = 'Authorization'
                        def tokenProperties = ['RepoxAuthHeaderValue', 'RepoxAuthAccessToken', 'artifactoryPassword']
                        def tokenVariables = ['ARTIFACTORY_ACCESS_TOKEN', 'ARTIFACTORY_DEPLOY_ACCESS_TOKEN', 'ARTIFACTORY_PASSWORD', 'ARTIFACTORY_PRIVATE_READER_TOKEN', 'ARTIFACTORY_PRIVATE_PASSWORD', 'ARTIFACTORY_DEPLOY_PASSWORD']
                        def token = (tokenProperties.collect { project.findProperty(it) } +
                            tokenVariables.collect { System.getenv(it) }).find { it != null }
                        value = token == null ? null : 'Bearer ' + token.toString().split(' ', 2).last()
                    }
                    authentication {
                        if (findByName('header') == null) {
                            header(org.gradle.authentication.http.HttpHeaderAuthentication)
                        }
                    }
                }
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
                def repox = findByName('Repox') ?: maven { name = 'Repox' }
                repox.with {
                    url = uri(repoxRepositoryUrl)
                    credentials(org.gradle.api.credentials.HttpHeaderCredentials) {
                        name = 'Authorization'
                        def tokenProperties = ['RepoxAuthHeaderValue', 'RepoxAuthAccessToken', 'artifactoryPassword']
                        def tokenVariables = ['ARTIFACTORY_ACCESS_TOKEN', 'ARTIFACTORY_DEPLOY_ACCESS_TOKEN', 'ARTIFACTORY_PASSWORD', 'ARTIFACTORY_PRIVATE_READER_TOKEN', 'ARTIFACTORY_PRIVATE_PASSWORD', 'ARTIFACTORY_DEPLOY_PASSWORD']
                        def token = (tokenProperties.collect { settings.hasProperty(it) ? settings.getProperty(it) : null } +
                            tokenVariables.collect { System.getenv(it) }).find { it != null }
                        value = token == null ? null : 'Bearer ' + token.toString().split(' ', 2).last()
                    }
                    authentication {
                        if (findByName('header') == null) {
                            header(org.gradle.authentication.http.HttpHeaderAuthentication)
                        }
                    }
                }
            } else {
                PUBLIC_REPOSITORIES
            }
        '''.stripIndent().replace('PUBLIC_REPOSITORIES', publicRepositories)
            .replace('LOCAL_REPOSITORIES', publicRepositories.contains('mavenLocal()') ? 'mavenLocal()' : '')
    }
}
