import org.sonarqube.gradle.SonarQubePlugin

buildscript {
    repositories {
        maven {
            url = uri("https://maven.google.com")
        }

        val repository = if (project.hasProperty("qa")) "sonarsource-qa" else "sonarsource"
        maven {
            url = uri("https://repox.jfrog.io/repox/${repository}")

            // The environment variables ARTIFACTORY_PRIVATE_USERNAME and ARTIFACTORY_PRIVATE_PASSWORD are used on QA env (Jenkins)
            // On local box, please add artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
            val artifactoryUsername = System.getenv("ARTIFACTORY_PRIVATE_USERNAME") ?: project.findProperty("artifactoryUsername") ?: ""
            val artifactoryPassword = System.getenv("ARTIFACTORY_PRIVATE_PASSWORD") ?: project.findProperty("artifactoryPassword") ?: ""

            if (artifactoryUsername is String && artifactoryUsername.isNotEmpty() && artifactoryPassword is String && artifactoryPassword.isNotEmpty()) {
                credentials {
                    username = artifactoryUsername
                    password = artifactoryPassword
                }
            }
        }
    }
    dependencies {
        classpath("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:3.3")
    }

    val projectTitle: String by project
    extra.apply{
        set("sonar.projectName", projectTitle)
    }
}

// To apply a third-party plugin from an external build script,
// you have to use the plugin's fully qualified class name, rather than its ID
apply<org.sonarqube.gradle.SonarQubePlugin>()

