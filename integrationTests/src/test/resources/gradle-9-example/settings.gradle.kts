rootProject.name = "gradle-9-example"

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.sonarqube") {
                useModule("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}
