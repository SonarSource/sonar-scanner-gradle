plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Configure Java 17 toolchain for this pure JVM module
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}