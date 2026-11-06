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
        val sonarPluginClasspath = System.getProperty("sonarPluginClasspath")
            ?: error("The KMP Android functional test must provide the plugin-under-test classpath.")
        sonarPluginClasspath.split(File.pathSeparator).forEach {
            classpath(files(it))
        }
    }
}

// Gradle TestKit isolates the plugin under test from the test project's other plugins when using withPluginClasspath():
// https://github.com/gradle/gradle/issues/22466
apply(plugin = "org.sonarqube")
extensions.configure(org.sonarqube.gradle.SonarExtension::class.java) {
    properties {
        property("sonar.projectKey", "kmp-android-double-indexing-new-dsl")
    }
}

plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.4.20"
    id("com.android.kotlin.multiplatform.library") version "9.2.1"
}

kotlin {
    android {
        namespace = "com.example.newdsl"
        compileSdk = 36
        minSdk = 23
        withHostTest {}
    }
}
