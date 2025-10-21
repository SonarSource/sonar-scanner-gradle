plugins {
    // Apply the Sonar plugin at the root level to scan all modules
    alias(libs.plugins.sonarqube)

    // Define other plugins as 'false' so subprojects can apply them
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// Default configuration for the Sonar scanner
sonarqube {
    properties {
        property("sonar.projectKey", "MyGradle9Project")
        property("sonar.host.url", "http://localhost:9000")
        // You would typically pass the token via environment variable
        // property("sonar.token", System.getenv("SONAR_TOKEN"))
    }
}