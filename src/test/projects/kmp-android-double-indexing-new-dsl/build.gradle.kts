buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        val sonarPluginClasspath = System.getProperty("sonarPluginClasspath")
            ?: error("The KMP Android functional test must provide the plugin-under-test classpath.")
        sonarPluginClasspath.split(File.pathSeparator).forEach {
            classpath(files(it))
        }
    }
}

// Gradle TestKit isolates the plugin under test from the test project's other plugins when using withPluginClasspath():
// https://github.com/gradle/gradle/issues/22466
apply(plugin = "org.sonarqube")
extensions.configure(org.sonarqube.gradle.SonarExtension::class.java) {
    properties {
        property("sonar.projectKey", "kmp-android-double-indexing-new-dsl")
    }
}

plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.3.0"
    id("com.android.kotlin.multiplatform.library") version "9.2.1"
}

kotlin {
    android {
        namespace = "com.example.newdsl"
        compileSdk = 36
        minSdk = 23
        withHostTest {}
    }
}
