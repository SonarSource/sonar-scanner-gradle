rootProject.name = "java-multi-module-with-skipped-modules"

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.sonarqube") {
                useModule("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        val repoxBaseUrl = System.getenv("ARTIFACTORY_RESOLVE_URL") ?: System.getenv("ARTIFACTORY_URL")
        val repoxRepositoryUrl = System.getenv("SONARSOURCE_REPOSITORY_URL") ?: repoxBaseUrl?.let { it.trimEnd('/') + "/" + (System.getenv("SONARSOURCE_REPOSITORY") ?: "sonarsource-qa") }
        if (repoxRepositoryUrl != null) {
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
        } else {
            mavenLocal()
            maven(url="maven-repo")
            gradlePluginPortal()
            ivy("ivy-repo")
        }
    }
}

include("module")
include("skippedModule")
include("module:submodule")
include("skippedModule:skippedSubmodule")
