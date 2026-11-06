buildscript {
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
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
        classpath("com.android.tools.build:gradle:9.2.1")

        val sonarPluginClasspath = System.getProperty("sonarPluginClasspath")
            ?: error("The KMP Android functional test must provide the plugin-under-test classpath.")
        sonarPluginClasspath.split(File.pathSeparator).forEach {
            classpath(files(it))
        }
    }
}

// Gradle TestKit isolates the plugin under test from the test project's other plugins when using withPluginClasspath():
// https://github.com/gradle/gradle/issues/22466
// Keeping the Sonar and Android plugins on the buildscript classpath exercises the public sonar task without that split.
apply(plugin = "org.sonarqube")
extensions.configure(org.sonarqube.gradle.SonarExtension::class.java) {
    properties {
        property("sonar.projectKey", "kmp-android-double-indexing")
    }
}

apply(plugin = "org.jetbrains.kotlin.multiplatform")
apply(plugin = "com.android.library")

extensions.configure(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java) {
    androidTarget()
}

extensions.configure(com.android.build.api.dsl.LibraryExtension::class.java) {
    namespace = "com.example.minimal"
    compileSdk = 36
}
