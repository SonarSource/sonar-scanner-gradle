buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
        classpath("com.android.tools.build:gradle:9.2.1")
    }
}

// Legacy apply keeps this fixture aligned with the root buildscript setup used to avoid TestKit classloader isolation.
apply(plugin = "org.jetbrains.kotlin.multiplatform")
apply(plugin = "com.android.library")

extensions.configure(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java) {
    androidTarget()
}

extensions.configure(com.android.build.api.dsl.LibraryExtension::class.java) {
    namespace = "com.example.neem"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }
}
