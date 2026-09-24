repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

dependencies {
    implementation("com.gradle.publish:plugin-publish-plugin:2.2.1")
    implementation("org.apache.maven:maven-model:3.9.9")
    implementation("org.codehaus.plexus:plexus-utils:4.1.0")
}
