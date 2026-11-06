plugins {
    id("java")
    id("org.sonarqube") version "${version}"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
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
