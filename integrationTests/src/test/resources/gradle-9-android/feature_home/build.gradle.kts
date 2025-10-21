plugins {
    // Note: 'kotlin-android' is automatically applied by AGP 9.0+
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.feature.home"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    // Configure Java 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
}