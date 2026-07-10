buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
        classpath("com.android.tools.build:gradle:9.2.1")

        val sonarPluginClasspath = System.getProperty("sonarPluginClasspath")
            ?: error("The KMP Android functional test must provide the plugin-under-test classpath.")
        sonarPluginClasspath.split(File.pathSeparator).forEach {
            classpath(files(it))
        }
    }
}

// Gradle TestKit isolates the plugin under test from the test project's other plugins when using withPluginClasspath():
// https://github.com/gradle/gradle/issues/22466
// Keeping the Sonar and Android plugins on the buildscript classpath exercises the public sonar task without that split.
apply(plugin = "org.sonarqube")

extensions.configure(org.sonarqube.gradle.SonarExtension::class.java) {
    properties {
        property("sonar.projectKey", "kmp-android-double-indexing")
    }
}
