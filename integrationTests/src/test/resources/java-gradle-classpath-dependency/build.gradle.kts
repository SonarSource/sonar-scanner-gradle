plugins {
    id("java")
    id("org.sonarqube") version "${version}"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    val repoxBaseUrl = System.getenv("ARTIFACTORY_RESOLVE_URL") ?: System.getenv("ARTIFACTORY_URL")
    val repoxRepositoryUrl = System.getenv("SONARSOURCE_REPOSITORY_URL") ?: repoxBaseUrl?.let { it.trimEnd('/') + "/" + (System.getenv("SONARSOURCE_REPOSITORY") ?: "sonarsource-qa") }
    if (repoxRepositoryUrl != null) {
        val repox = (findByName("Repox") as? org.gradle.api.artifacts.repositories.MavenArtifactRepository)
            ?: maven { name = "Repox" }
        repox.apply {
            url = uri(repoxRepositoryUrl)
            credentials(org.gradle.api.credentials.HttpHeaderCredentials::class) {
                name = "Authorization"
                val tokenProperties = listOf("RepoxAuthHeaderValue", "RepoxAuthAccessToken", "artifactoryPassword")
                val tokenVariables = listOf("ARTIFACTORY_ACCESS_TOKEN", "ARTIFACTORY_DEPLOY_ACCESS_TOKEN", "ARTIFACTORY_PASSWORD", "ARTIFACTORY_PRIVATE_READER_TOKEN", "ARTIFACTORY_PRIVATE_PASSWORD", "ARTIFACTORY_DEPLOY_PASSWORD")
                val token = (tokenProperties.map { project.findProperty(it)?.toString() } +
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
        mavenCentral()
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// tasks has as output resources/main. Remark resources/main/ is part of the classpath
// gralde detect that writeToResources affect the classpath, any plugin depending on the classpath must have an explicit
// relation (depends on, must run after, must run before, ... ) to writeToResources
tasks.register("writeToResources") {
    outputs.file(layout.buildDirectory.file("resources/main/generated.txt"))
    doLast {
        val outputDir = layout.buildDirectory.dir("resources/main").get().asFile
        outputDir.mkdirs()
        val file = File(outputDir, "generated.txt")
        file.writeText("This file was generated after classes task.")
    }
}

tasks.named("classes").configure {
    finalizedBy("writeToResources")
}
