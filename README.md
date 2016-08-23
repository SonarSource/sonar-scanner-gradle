# SonarQube Scanner for Gradle
[![Build Status](https://travis-ci.org/SonarSource/sonar-scanner-gradle.svg?branch=master)](https://travis-ci.org/SonarSource/sonar-scanner-gradle)

## User documentation

http://docs.sonarqube.org/display/SONAR/Analyzing+with+SonarQube+Scanner+for+Gradle

## Developer documentation

### How to install a SNAPSHOT in local Maven repository:
`./gradlew install`

### How to use the plugin SNAPSHOT previously installed in local Maven repository:

```groovy
buildscript {
    repositories { 
      mavenCentral()
      mavenLocal()
    }
    dependencies { classpath 'org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:<THE VERSION>' }
}

apply plugin: 'org.sonarqube'
```

### How to release and deploy on Gradle plugin repository:
https://plugins.gradle.org/docs/publish-plugin

`./gradlew release`

