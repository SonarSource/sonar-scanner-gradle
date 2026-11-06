plugins {
  java
  id("org.sonarqube") version "${version}"
}

allprojects {
  apply(plugin = "java")

  repositories {
      val repoxBaseUrl = System.getenv("ARTIFACTORY_RESOLVE_URL") ?: System.getenv("ARTIFACTORY_URL")
      val repoxRepositoryUrl = System.getenv("SONARSOURCE_REPOSITORY_URL") ?: repoxBaseUrl?.let { it.trimEnd('/') + "/" + (System.getenv("SONARSOURCE_REPOSITORY") ?: "sonarsource-qa") }
      if (repoxRepositoryUrl != null) {
          mavenLocal()
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
          mavenLocal()
          mavenCentral()
      }
  }

  extra["baseVersion"] = "0.1"
  extra["snapshotVersion"] = true

  group = "org.sonar.tests"
  version = "${extra["baseVersion"]}${if (extra["snapshotVersion"] as Boolean) "-SNAPSHOT" else ""}"

  dependencies {
    implementation("commons-io:commons-io:2.5")
    testImplementation("junit:junit:4.10")
  }
}

sonar {
  properties {
    property("sonar.projectName", "Multi-module Project with submodules in root")
    property("sonar.projectKey", "org.sonarsource.gradle:example-multi-module-with-submodules")
    property("sonar.gradle.scanAll", "true")
  }
}
