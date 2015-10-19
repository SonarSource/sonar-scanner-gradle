# SonarQube Gradle Plugin
[![Build Status](https://travis-ci.org/SonarCommunity/sonar-gradle.svg?branch=master)](https://travis-ci.org/SonarCommunity/sonar-gradle)

## How to install in local Maven repository:
`./gradlew install`

## How to use the version of the plugin previously installed in local Maven repository:

```groovy
buildscript {
    repositories { 
      mavenCentral()
      mavenLocal()
    }
    dependencies { classpath 'org.sonarqube.gradle:gradle-sonarqube-plugin:<THE VERSION>' }
}

apply plugin: 'org.sonarqube'
```

## How to deploy on Gradle plugin repository:
https://plugins.gradle.org/docs/publish-plugin
