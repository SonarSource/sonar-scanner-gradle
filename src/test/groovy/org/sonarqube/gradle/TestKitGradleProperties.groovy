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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.stream.Stream

class TestKitGradleProperties {

    private TestKitGradleProperties() {
    }

    /**
     * Prepares a temporary Gradle TestKit project for coverage and Java toolchain tests.
     */
    static void configure(Path projectDir) {
        Path gradleProperties = projectDir.resolve('gradle.properties')
        copyJacocoTestKitProperties(gradleProperties)
        appendToolchainPaths(gradleProperties)
    }

    /**
     * Enables JaCoCo coverage for Gradle TestKit builds.
     * The pl.droidsonroids.jacoco.testkit plugin generates testkit-gradle.properties
     * through the generateJacocoTestKitProperties task.
     * See https://github.com/koral--/jacoco-gradle-testkit-plugin
     */
    private static void copyJacocoTestKitProperties(Path gradleProperties) {
        try (InputStream input = TestKitGradleProperties.class.getClassLoader().getResourceAsStream('testkit-gradle.properties')) {
            Files.copy(input, gradleProperties, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private static void appendToolchainPaths(Path gradleProperties) {
        List<String> toolchainPaths = discoverMiseJdkHomes().collect { escapePropertyPath(it) }
        if (toolchainPaths.isEmpty()) {
            return
        }
        Files.write(
            gradleProperties,
            ["", "org.gradle.java.installations.paths=${toolchainPaths.join(',')}", ""],
            StandardCharsets.UTF_8,
            StandardOpenOption.APPEND
        )
    }

    private static List<Path> discoverMiseJdkHomes() {
        // Gradle TestKit runs outside mise, so expose mise-provisioned JDKs through org.gradle.java.installations.paths.
        Set<Path> homes = new LinkedHashSet<>()
        for (Path miseJavaInstalls : miseJavaInstallRoots()) {
            if (!Files.isDirectory(miseJavaInstalls)) {
                continue
            }
            try (Stream<Path> children = Files.list(miseJavaInstalls)) {
                children.each { child ->
                    if (hasJavac(child)) {
                        homes.add(child.toRealPath())
                    }
                }
            }
        }
        return homes as List<Path>
    }

    /**
     * Directories where mise may store provisioned JDKs. mise uses ~/.local/share/mise on Linux/macOS and
     * %LOCALAPPDATA%\mise on Windows, so both roots are checked to keep this platform independent.
     */
    private static List<Path> miseJavaInstallRoots() {
        Path userHome = Path.of(System.getProperty("user.home"))
        String localAppData = System.getenv("LOCALAPPDATA")
        Path windowsMiseBase = localAppData?.trim() ? Path.of(localAppData) : userHome.resolve("AppData").resolve("Local")
        return [
            userHome.resolve(".local").resolve("share").resolve("mise").resolve("installs").resolve("java"),
            windowsMiseBase.resolve("mise").resolve("installs").resolve("java")
        ]
    }

    private static boolean hasJavac(Path javaHome) {
        Files.isRegularFile(javaHome.resolve("bin").resolve(javacExecutableName()))
    }

    private static String javacExecutableName() {
        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows") ? "javac.exe" : "javac"
    }

    private static String escapePropertyPath(Path path) {
        path.toString().replace("\\", "\\\\")
    }
}
