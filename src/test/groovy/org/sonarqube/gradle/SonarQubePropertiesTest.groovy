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

class SonarQubePropertiesTest extends Specification {
    def properties = new SonarQubeProperties([:])

    def "set a single property"() {
        when:
        properties.property "foo", "one"

        then:
        properties.properties == [foo: "one"]
    }

    def "set multiple properties at once"() {
        when:
        properties.properties foo: "one", bar: "two"

        then:
        properties.properties == [foo: "one", bar: "two"]
    }

    def "read and write the properties map directly"() {
        when:
        properties.properties.putAll(foo: "one", bar: "two")
        properties.properties.bar *= 2
        properties.properties.remove("foo")

        then:
        properties.properties == [bar: "twotwo"]
    }
}
