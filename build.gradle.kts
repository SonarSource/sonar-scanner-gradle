plugins {
    id("java-gradle-plugin")
    java
    jacoco
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.21.0"
    id("org.sonarqube") version "4.0.0.2929"
    id("com.jfrog.artifactory") version "4.24.23"
    id("com.github.hierynomus.license") version "0.16.1"
    id("pl.droidsonroids.jacoco.testkit") version "1.0.9"
    id("org.cyclonedx.bom") version "1.5.0"
}

val projectTitle: String by project

jacoco {
    toolVersion = "0.8.7"
}