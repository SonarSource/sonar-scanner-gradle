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

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

dependencies {
    implementation("com.gradle.publish:plugin-publish-plugin:1.3.1")
    implementation("org.apache.maven:maven-model:3.9.9")
    implementation("org.codehaus.plexus:plexus-utils:3.6.1")
}
