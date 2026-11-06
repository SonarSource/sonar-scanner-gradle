buildscript {
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
