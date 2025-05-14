repositories {
    mavenLocal()
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.gradle.publish:plugin-publish-plugin:1.3.1")
    implementation("org.apache.maven:maven-model:3.9.9")
    implementation("org.codehaus.plexus:plexus-utils:3.6.0")
}
