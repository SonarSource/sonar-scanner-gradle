pluginManagement {
    repositories {
        val repoxBaseUrl = System.getenv("ARTIFACTORY_RESOLVE_URL") ?: System.getenv("ARTIFACTORY_URL")
        val repoxRepositoryUrl = repoxBaseUrl?.let { it.trimEnd('/') + "/" + (System.getenv("SONARSOURCE_REPOSITORY") ?: "sonarsource") }
        if (repoxRepositoryUrl != null) {
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
        } else {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    val repoxConfigured = System.getenv("ARTIFACTORY_RESOLVE_URL") != null || System.getenv("ARTIFACTORY_URL") != null
    repositoriesMode.set(if (repoxConfigured) RepositoriesMode.PREFER_SETTINGS else RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val repoxBaseUrl = System.getenv("ARTIFACTORY_RESOLVE_URL") ?: System.getenv("ARTIFACTORY_URL")
        val repoxRepositoryUrl = repoxBaseUrl?.let { it.trimEnd('/') + "/" + (System.getenv("SONARSOURCE_REPOSITORY") ?: "sonarsource") }
        if (repoxRepositoryUrl != null) {
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
        } else {
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "kmp-android-double-indexing-new-dsl"
