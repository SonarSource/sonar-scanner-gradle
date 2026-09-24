repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

dependencies {
    implementation("com.gradle.publish:plugin-publish-plugin:1.3.1")
    implementation("org.apache.maven:maven-model:3.9.16")
    implementation("org.codehaus.plexus:plexus-utils:3.6.2")
}
