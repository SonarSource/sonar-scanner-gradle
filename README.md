# SonarQube Scanner for Gradle

[![Build Status](https://travis-ci.org/SonarSource/sonar-scanner-gradle.svg?branch=master)](https://travis-ci.org/SonarSource/sonar-scanner-gradle) [![Quality Gate](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.scanner.gradle%3Asonarqube-gradle-plugin&metric=alert_status)](https://next.sonarqube.com/sonarqube/dashboard?id=org.sonarsource.scanner.gradle%3Asonarqube-gradle-plugin)

## User documentation

http://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle

Need help or want to report a bug/suggest a new feature? Check out the [SonarSource Community Forum](https://community.sonarsource.com/).

## Developer documentation

### How the plugin works
When the plugin is applied to a project, it will add to that project the SonarQube task. It will also add to the project and all it's subprojects the SonarQube extension.
For multi-module projects, the plugin will only apply to the first project where it gets called. The goal is to allow the usage of `allprojects {}`, for example.

**SonarQube extension**
The `sonarqube` extension enables a easy configuration of a project with the DSL.

**SonarQube task**
The SonarQube task has the name `sonarqube`, so it can be executing by calling `./gradlew sonarqube`. It collects information from the project and all its subprojects, generating the properties for the analysis. Then, it runs the SonarQube analysis using all those properties.
The task depends on all compile and test tasks of all projects (except for skipped projects).
If all projects are skipped (by adding `skipProject=true` to the sonarqube DSL), the analysis won't execute.

  


### Using the plugin directly in a project (no need to build/install it in advance)

In the target project, apply as usual:
```
apply plugin: 'org.sonarqube'
```

Run with:
```
./gradlew sonarqube --include-build /path/to/sonar-scanner-gradle
```

### Debugging the plugin
See the previous point about including the plugin's build when building a target project.
To debug, simple add the parameter:
```
./gradlew sonarqube --include-build /path/to/sonar-scanner-gradle -Dorg.gradle.debug=true
```

Now debug remotely by connecting to port 5005.


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
