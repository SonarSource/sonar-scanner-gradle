import org.sonarqube.gradle.SonarQubePlugin

buildscript {
    data class Repox(val url: String, val username: String, val password: String)
    fun nonEmpty(value: String?): String? = value?.takeIf { it.isNotEmpty() }
    val privateUsername = nonEmpty(System.getenv("ARTIFACTORY_PRIVATE_USERNAME") ?: providers.gradleProperty("artifactoryUsername").orNull)
    val privatePassword = nonEmpty(System.getenv("ARTIFACTORY_PRIVATE_PASSWORD") ?: providers.gradleProperty("artifactoryPassword").orNull)
    val ciUrl = nonEmpty(System.getenv("ARTIFACTORY_RESOLVE_URL"))
    val ciUsername = nonEmpty(System.getenv("ARTIFACTORY_USERNAME"))
    val ciPassword = nonEmpty(System.getenv("ARTIFACTORY_ACCESS_TOKEN"))
    val repox = when {
        privateUsername != null && privatePassword != null -> Repox("https://repox.jfrog.io/artifactory", privateUsername, privatePassword)
        ciUrl != null && ciUsername != null && ciPassword != null -> Repox(ciUrl, ciUsername, ciPassword)
        else -> null
    }
    repositories {
        if (repox != null) {
            maven {
                url = uri("${repox.url.trimEnd('/')}/plugins.gradle.org")
                credentials {
                    username = repox.username
                    password = repox.password
                }
            }
        } else {
            gradlePluginPortal()
        }
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
