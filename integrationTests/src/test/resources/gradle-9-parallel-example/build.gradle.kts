plugins {
    id("java")
    `java-library`
    id("org.sonarqube") version "${version}"
}

group = "org.example.gradle"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
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
        property("sonar.projectName", "Gradle 9 parallel example")
        property("sonar.projectKey", "gradle-9-parallel-example")
    }
}
