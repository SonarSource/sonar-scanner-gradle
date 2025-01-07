rootProject.name = "sonarqube-gradle-plugin"

plugins {
    id("com.gradle.develocity") version("3.18.2")
}
develocity {
    server.set("https://develocity.sonar.build")
    buildScan {
        capture {
            buildLogging.set(!startParameter.taskNames.contains("properties"))
        }
    }
}
val isCI: Boolean = System.getenv("CI") != null
buildCache {
    local {
        isEnabled = !isCI
    }
    remote(develocity.buildCache) {
        isEnabled = true
        isPush = isCI
    }
}

