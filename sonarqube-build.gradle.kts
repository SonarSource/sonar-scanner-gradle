import org.sonarqube.gradle.SonarQubePlugin

buildscript {
    repositories {
        val repoxBaseUrl = System.getenv("ARTIFACTORY_RESOLVE_URL") ?: System.getenv("ARTIFACTORY_URL") ?: "https://repox.jfrog.io/artifactory"
        val repoxRepositoryUrl = repoxBaseUrl.trimEnd('/') + "/" + (System.getenv("SONARSOURCE_REPOSITORY") ?: "sonarsource")
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

    dependencies {
        classpath("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:latest.release")
    }

    val projectTitle: String by project
    extra.apply {
        set("sonar.projectName", projectTitle)
    }
}

// To apply a third-party plugin from an external build script,
// you have to use the plugin's fully qualified class name, rather than its ID
apply<org.sonarqube.gradle.SonarQubePlugin>()
