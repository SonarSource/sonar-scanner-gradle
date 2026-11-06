pluginManagement {
    repositories {
        val repoxBaseUrl = System.getenv("ARTIFACTORY_RESOLVE_URL") ?: System.getenv("ARTIFACTORY_URL")
        val repoxRepositoryUrl = repoxBaseUrl?.let { it.trimEnd('/') + "/" + (System.getenv("SONARSOURCE_REPOSITORY") ?: "sonarsource") }
        if (repoxRepositoryUrl != null) {
            val repox = (findByName("Repox") as? org.gradle.api.artifacts.repositories.MavenArtifactRepository)
                ?: maven { name = "Repox" }
            repox.apply {
                url = uri(repoxRepositoryUrl)
                credentials(org.gradle.api.credentials.HttpHeaderCredentials::class) {
                    name = "Authorization"
                    val tokenProperties = listOf("RepoxAuthHeaderValue", "RepoxAuthAccessToken", "artifactoryPassword")
                    val tokenVariables = listOf("ARTIFACTORY_ACCESS_TOKEN", "ARTIFACTORY_DEPLOY_ACCESS_TOKEN", "ARTIFACTORY_PASSWORD", "ARTIFACTORY_PRIVATE_READER_TOKEN", "ARTIFACTORY_PRIVATE_PASSWORD", "ARTIFACTORY_DEPLOY_PASSWORD")
                    val token = (tokenProperties.map { providers.gradleProperty(it).orNull } +
                        tokenVariables.map { System.getenv(it) }).firstOrNull { it != null }
                    value = token?.let { "Bearer ${it.substringAfter(' ')}" }
                }
                authentication {
                    if (findByName("header") == null) {
                        create<org.gradle.authentication.http.HttpHeaderAuthentication>("header")
                    }
                }
            }
        } else {
            gradlePluginPortal()
        }
    }
}
