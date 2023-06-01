import org.sonarqube.gradle.SonarQubePlugin

buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }

    dependencies {
        classpath("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:4.2.0.3129")
    }

    val projectTitle: String by project
    extra.apply{
        set("sonar.projectName", projectTitle)
    }
}

// To apply a third-party plugin from an external build script,
// you have to use the plugin's fully qualified class name, rather than its ID
apply<org.sonarqube.gradle.SonarQubePlugin>()
