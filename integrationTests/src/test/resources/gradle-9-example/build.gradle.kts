plugins {
    id("java")
    `java-library`
    id("org.sonarqube") version "${version}"
}

group = "org.example.gradle"
version = "1.0-SNAPSHOT"

repositories {
    val repoxBaseUrl = System.getenv("ARTIFACTORY_RESOLVE_URL") ?: System.getenv("ARTIFACTORY_URL")
    val repoxRepositoryUrl = System.getenv("SONARSOURCE_REPOSITORY_URL") ?: repoxBaseUrl?.let { it.trimEnd('/') + "/" + (System.getenv("SONARSOURCE_REPOSITORY") ?: "sonarsource-qa") }
    if (repoxRepositoryUrl != null) {
        mavenLocal()
        val repox = (findByName("Repox") as? org.gradle.api.artifacts.repositories.MavenArtifactRepository)
            ?: maven {
                name = "Repox"
                val artifactoryUsername = System.getenv("ARTIFACTORY_USERNAME") ?: project.findProperty("artifactoryUsername")?.toString()
                val artifactoryPassword = System.getenv("ARTIFACTORY_ACCESS_TOKEN") ?: System.getenv("ARTIFACTORY_PASSWORD") ?: project.findProperty("artifactoryPassword")?.toString()
                if (!artifactoryUsername.isNullOrEmpty() && !artifactoryPassword.isNullOrEmpty()) {
                    credentials {
                        username = artifactoryUsername
                        password = artifactoryPassword
                    }
                }
            }
        repox.url = uri(repoxRepositoryUrl)
    } else {
        mavenLocal()
        mavenCentral()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    api("org.jspecify:jspecify:1.0.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

sonar {
    properties {
        property("sonar.projectName", "Gradle 9 example")
        property("sonar.projectKey", "gradle-9-example")
    }
}
