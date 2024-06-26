plugins {
  java
  id("org.sonarqube") version "5.1-SNAPSHOT"
}

allprojects {
  apply(plugin = "java")

  repositories {
    mavenLocal()
    mavenCentral()
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
