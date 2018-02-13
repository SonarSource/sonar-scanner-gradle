/*
 * SonarQube Scanner for Gradle
 * Copyright (C) 2015-2018 SonarSource
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
package org.sonarqube.gradle

import spock.lang.Specification

class SonarQubeExtensionTest extends Specification {

    def "evaluate properties blocks"() {
        def actionBroadcast = new ActionBroadcast<SonarQubeProperties>()
        def extension = new SonarQubeExtension(actionBroadcast)
        def props = ["key.1": "value 1"]

        when:
        extension.properties {
            it.property "key.2", ["value 2"]
            it.properties(["key.3": "value 3", "key.4": "value 4"])
        }

        extension.properties {
            it.property "key.5", "value 5"
            it.properties["key.2"] << "value 6"
            it.properties.remove("key.3")
        }

        def sonarProperties = new SonarQubeProperties(props)
        actionBroadcast.execute(sonarProperties)

        then:
        props == ["key.1": "value 1", "key.2": ["value 2", "value 6"], "key.4": "value 4", "key.5": "value 5"]
    }
}
