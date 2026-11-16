val artifactoryUsername: String =
    System.getenv("ARTIFACTORY_PRIVATE_USERNAME") ?: providers.gradleProperty("artifactoryUsername").getOrElse("")
val artifactoryPassword: String =
    System.getenv("ARTIFACTORY_PRIVATE_PASSWORD") ?: providers.gradleProperty("artifactoryPassword").getOrElse("")

repositories {
    if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
        maven {
            url = uri("https://repox.jfrog.io/repox/sonarsource")
            credentials {
                username = artifactoryUsername
                password = artifactoryPassword
            }
        }
    } else {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

dependencies {
    implementation("com.gradle.publish:plugin-publish-plugin:2.2.1")
    implementation("org.apache.maven:maven-model:3.9.9")
    implementation("org.codehaus.plexus:plexus-utils:4.1.0")
}
