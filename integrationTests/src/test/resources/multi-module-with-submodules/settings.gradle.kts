rootProject.name = "java-multi-module-with-skipped-modules"

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
        maven(url="maven-repo")
        gradlePluginPortal()
        ivy("ivy-repo")
    }
}

include("module")
include("skippedModule")
include("module:submodule")
include("skippedModule:skippedSubmodule")
