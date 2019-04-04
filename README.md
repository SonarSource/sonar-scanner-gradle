SonarQube Scanner for Gradle
============================

[![Build Status](https://travis-ci.org/SonarSource/sonar-scanner-gradle.svg?branch=master)](https://travis-ci.org/SonarSource/sonar-scanner-gradle) [![Quality Gate](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.scanner.gradle%3Asonarqube-gradle-plugin&metric=alert_status)](https://next.sonarqube.com/sonarqube/dashboard?id=org.sonarsource.scanner.gradle%3Asonarqube-gradle-plugin)

User documentation
------------------

https://redirect.sonarsource.com/doc/gradle.html

Have Question or Feedback?
--------------------------

For support questions ("How do I?", "I got this error, why?", ...), please head to the [SonarSource forum](https://community.sonarsource.com/c/help). There are chances that a question similar to yours has already been answered. 

Be aware that this forum is a community, so the standard pleasantries ("Hi", "Thanks", ...) are expected. And if you don't get an answer to your thread, you should sit on your hands for at least three days before bumping it. Operators are not standing by. :-)


Contributing
------------

If you would like to see a new feature, please create a new thread in the forum ["Suggest new features"](https://community.sonarsource.com/c/suggestions/features).

Please be aware that we are not actively looking for feature contributions. The truth is that it's extremely difficult for someone outside SonarSource to comply with our roadmap and expectations. Therefore, we typically only accept minor cosmetic changes and typo fixes.

With that in mind, if you would like to submit a code contribution, please create a pull request for this repository. Please explain your motives to contribute this change: what problem you are trying to fix, what improvement you are trying to make.

Make sure that you follow our [code style](https://github.com/SonarSource/sonar-developer-toolset#code-style) and all tests are passing (Travis build is executed for each pull request).


Developer documentation
-----------------------

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


License
-------

Copyright 2011-2019 SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt))
