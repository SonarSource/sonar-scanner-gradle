plugins {
  java
  id("org.sonarqube") version "${version}"
}

allprojects {
  apply(plugin = "java")

  repositories {
      val repoxBaseUrl = System.getenv("ARTIFACTORY_RESOLVE_URL") ?: System.getenv("ARTIFACTORY_URL") ?: "https://repox.jfrog.io/artifactory"
      val repoxRepositoryUrl = System.getenv("SONARSOURCE_REPOSITORY_URL") ?: repoxBaseUrl.trimEnd('/') + "/" + (System.getenv("SONARSOURCE_REPOSITORY") ?: "sonarsource-qa")
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
