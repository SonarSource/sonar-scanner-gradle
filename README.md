# SonarQube Scanner for Gradle

[![Build Status](https://travis-ci.org/SonarSource/sonar-scanner-gradle.svg?branch=master)](https://travis-ci.org/SonarSource/sonar-scanner-gradle) [![Quality Gate](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.scanner.gradle%3Asonarqube-gradle-plugin&metric=alert_status)](https://next.sonarqube.com/sonarqube/dashboard?id=org.sonarsource.scanner.gradle%3Asonarqube-gradle-plugin)

## User documentation

http://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle

## Developer documentation

### Install a SNAPSHOT in local Maven repository

    ./gradlew install

### Using the plugin SNAPSHOT previously installed in local Maven repository

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

### Release and deploy on Gradle plugin repository

https://plugins.gradle.org/docs/publish-plugin

    ./gradlew release


### License

Copyright 2015-2018 SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
