rootProject.name = "sonarqube-gradle-plugin"

pluginManagement {
    // The environment variables ARTIFACTORY_PRIVATE_USERNAME and ARTIFACTORY_PRIVATE_PASSWORD are used on CI env
    // If you have access to "repox.jfrog.io" you can add artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
    val artifactoryUsername: String =
        System.getenv("ARTIFACTORY_PRIVATE_USERNAME") ?: providers.gradleProperty("artifactoryUsername").getOrElse("")
    val artifactoryPassword: String =
        System.getenv("ARTIFACTORY_PRIVATE_PASSWORD") ?: providers.gradleProperty("artifactoryPassword").getOrElse("")
    val repoxRepository: java.net.URI =
        uri("https://repox.jfrog.io/repox/" + (if (providers.gradleProperty("qa").isPresent()) "sonarsource-qa" else "sonarsource"))
    repositories {
        mavenLocal()
        if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
            maven {
                url = repoxRepository
                credentials {
                    username = artifactoryUsername
                    password = artifactoryPassword
                }
            }
        } else {
            mavenCentral()
        }
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories.addAll(pluginManagement.repositories)
}

plugins {
    id("com.gradle.develocity") version "3.18.2"
}

develocity {
    server = "https://develocity-public.sonar.build"
    buildScan {
        capture {
            buildLogging.set(!startParameter.taskNames.contains("properties"))
        }
    }
}
val isCI: Boolean = System.getenv("CI") != null
buildCache {
    local {
        isEnabled = !isCI
    }
    remote(develocity.buildCache) {
        isEnabled = true
        isPush = isCI
    }
}
