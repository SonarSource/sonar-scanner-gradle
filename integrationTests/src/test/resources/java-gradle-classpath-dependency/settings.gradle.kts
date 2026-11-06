pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.sonarqube") {
                useModule("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        val repoxBaseUrl = System.getenv("ARTIFACTORY_RESOLVE_URL") ?: System.getenv("ARTIFACTORY_URL") ?: "https://repox.jfrog.io/artifactory"
        val repoxRepositoryUrl = System.getenv("SONARSOURCE_REPOSITORY_URL") ?: repoxBaseUrl.trimEnd('/') + "/" + (System.getenv("SONARSOURCE_REPOSITORY") ?: "sonarsource-qa")
        mavenLocal()
        val repox = (findByName("Repox") as? org.gradle.api.artifacts.repositories.MavenArtifactRepository)
            ?: maven {
                name = "Repox"
                val artifactoryUsername = System.getenv("ARTIFACTORY_USERNAME") ?: settings.extensions.extraProperties.properties["artifactoryUsername"]?.toString()
                val artifactoryPassword = System.getenv("ARTIFACTORY_ACCESS_TOKEN") ?: System.getenv("ARTIFACTORY_PASSWORD") ?: settings.extensions.extraProperties.properties["artifactoryPassword"]?.toString()
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

rootProject.name = "TrivialGradleWithDependencyOnClasspath"