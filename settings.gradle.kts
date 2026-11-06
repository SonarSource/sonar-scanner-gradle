rootProject.name = "sonarqube-gradle-plugin"

pluginManagement {
    repositories {
        val repoxBaseUrl = System.getenv("ARTIFACTORY_RESOLVE_URL") ?: System.getenv("ARTIFACTORY_URL") ?: "https://repox.jfrog.io/artifactory"
        val repoxRepositoryUrl = repoxBaseUrl.trimEnd('/') + "/" + (System.getenv("SONARSOURCE_REPOSITORY") ?: if (providers.gradleProperty("qa").isPresent) "sonarsource-qa" else "sonarsource")
        val repox = (findByName("Repox") as? org.gradle.api.artifacts.repositories.MavenArtifactRepository)
            ?: maven {
                name = "Repox"
                val artifactoryUsername = System.getenv("ARTIFACTORY_USERNAME") ?: providers.gradleProperty("artifactoryUsername").orNull
                val artifactoryPassword = System.getenv("ARTIFACTORY_ACCESS_TOKEN") ?: System.getenv("ARTIFACTORY_PASSWORD") ?: providers.gradleProperty("artifactoryPassword").orNull
                if (!artifactoryUsername.isNullOrEmpty() && !artifactoryPassword.isNullOrEmpty()) {
                    credentials {
                        username = artifactoryUsername
                        password = artifactoryPassword
                    }
                }
            }
        repox.url = uri(repoxRepositoryUrl)
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
