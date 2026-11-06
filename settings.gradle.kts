rootProject.name = "sonarqube-gradle-plugin"

pluginManagement {
    // Preserve existing local credentials when the shared actions have not configured Repox.
    val artifactoryUsername: String =
        System.getenv("ARTIFACTORY_USERNAME") ?: providers.gradleProperty("artifactoryUsername").getOrElse("")
    val artifactoryPassword: String =
        System.getenv("ARTIFACTORY_ACCESS_TOKEN") ?: System.getenv("ARTIFACTORY_PASSWORD") ?: providers.gradleProperty("artifactoryPassword").getOrElse("")
    val repoxRepository: java.net.URI =
        uri("https://repox.jfrog.io/repox/" + (if (providers.gradleProperty("qa").isPresent()) "sonarsource-qa" else "sonarsource"))
    repositories {
        val repoxBaseUrl = System.getenv("ARTIFACTORY_RESOLVE_URL") ?: System.getenv("ARTIFACTORY_URL")
        val repoxRepositoryUrl = repoxBaseUrl?.let { it.trimEnd('/') + "/" + (System.getenv("SONARSOURCE_REPOSITORY") ?: "sonarsource") }
        if (repoxRepositoryUrl != null) {
            val repox = (findByName("Repox") as? org.gradle.api.artifacts.repositories.MavenArtifactRepository)
                ?: maven {
                    name = "Repox"
                    if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
                        credentials {
                            username = artifactoryUsername
                            password = artifactoryPassword
                        }
                    }
                }
            repox.url = uri(repoxRepositoryUrl)
        } else {
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
