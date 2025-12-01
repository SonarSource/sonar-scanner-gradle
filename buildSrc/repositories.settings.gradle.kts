dependencyResolutionManagement {
    // The environment variable ARTIFACTORY_ACCESS_TOKEN is used in QA
    // On local box, please add artifactoryPassword to ~/.gradle/gradle.properties and the init script from Xtranet
    val useRepox = System.getenv("ARTIFACTORY_ACCESS_TOKEN") != null || extra["artifactoryPassword"] != null

    repositories {
        if (useRepox) {
            maven {
                url = uri("https://repox.jfrog.io/repox/sonarsource/")
            }
        } else {
            mavenCentral()
        }
    }

    pluginManagement {
        repositories {
            if (useRepox) {
                maven {
                    url = uri("https://repox.jfrog.io/repox/plugins.gradle.org/")
                }
            } else {
                gradlePluginPortal()
            }
        }
    }

}
