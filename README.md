SonarScanner for Gradle
============================

[![Build Status](https://api.cirrus-ci.com/github/SonarSource/sonar-scanner-gradle.svg)](https://cirrus-ci.com/github/SonarSource/sonar-scanner-gradle) [![Quality Gate](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.scanner.gradle%3Asonarqube-gradle-plugin&metric=alert_status)](https://next.sonarqube.com/sonarqube/dashboard?id=org.sonarsource.scanner.gradle%3Asonarqube-gradle-plugin)

About Sonar
-----------

Sonar's [Clean Code solutions](https://www.sonarsource.com/solutions/clean-code/?utm_medium=referral&utm_source=github&utm_campaign=clean-code&utm_content=sonar-scanner-cli-docker) help developers deliver high-quality, efficient code standards that benefit the entire team or organization.

User documentation
------------------

https://redirect.sonarsource.com/doc/gradle.html

Have Questions or Feedback?
---------------------------

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

### Building the project
To build the plugin and run the tests, you will need Java 11.
```bash
./gradlew clean build
```

#### Fix the error `* What went wrong: Dependency verification failed ... update the gradle/verification-metadata.xml file ...`
You need to update `gradle/verification-metadata.xml` and review it.
```bash
./gradlew --refresh-dependencies --info --stacktrace --write-verification-metadata sha256
git diff -- gradle/verification-metadata.xml
```
Once you have reviewed the changes, replace `origin="Generated by Gradle"` with `origin="Verified"` for the changes you accepted.
And delete in the `verification-metadata.xml` the versions that we don't use anymore, because `--write-verification-metadata` don't remove unused dependencies.
But do not delete all unused versions for dependencies with a dynamic version set to `latest.release` (like `org.sonarsource.scanner.gradle:sonarqube-gradle-plugin`), 
because Gradle cache the version resolution for 24 hours, so for those dependencies, we also need to keep the before last version.

When you update a dependency’s checksum in the gradle/verification-metadata.xml file, you validate the change by comparing the sha256 value from Artifactory with the one listed on another package repositories like maven central. 
First, identify the dependency updated in the file and copy its sha256 checksum. 
Next, search for the dependency on another package repositories. For instance on [Maven Central](https://central.sonatype.com), you can use the query `checksum:new-dependency-sha256` to find a specific dependency.

### How the plugin works
When the plugin is applied to a project, it will add to that project the Sonar task. It will also add to the project and all its subprojects the Sonar extension.
For multi-module projects, the plugin will only apply to the first project where it gets called. The goal is to allow the usage of `allprojects {}`, for example.

**Sonar extension**
The `sonar` extension enables an easy configuration of a project with the Domain Specific Language.

**Sonar task**
The Sonar task has the name `sonar`, so it can be executed by calling `./gradlew sonar`. It collects information from the project and all its subprojects, generating the properties for the analysis. Then, it runs the SonarScanner analysis using all those properties.
The task depends on all compile and test tasks of all projects (except for skipped projects).
If all projects are skipped (by adding `skipProject=true` to the sonar DSL), the analysis won't execute.


### Using the plugin directly in a project (no need to build/install it in advance)
A composite build can be used to substitute plugins with an included build.

In the target project, apply the `sonarqube` plugin:
```
plugins {
  id 'org.sonarqube'
}
```

Run with:
```
./gradlew sonar --include-build /path/to/sonar-scanner-gradle
```

### Debugging the plugin
See the previous point about including the plugin's build when building a target project.
To debug, simply add the parameter:
```
./gradlew sonar --include-build /path/to/sonar-scanner-gradle -Dorg.gradle.debug=true
```

Now debug remotely by connecting to the port 5005.

### Integration Tests
By default, Integration Tests are skipped during the build. To run them, you need to follow these steps:

* Install the SNAPSHOT version of the root project in the local Maven repository.  
* Import the `integationTests` project as a Maven project and ensure that Android SDK is set.  
* Set `ANDROID_HOME` environment variable
* Run the following command from the `integrationTests` project:
    ```
    mvn --errors --batch-mode clean verify
    ```

### Install a SNAPSHOT in the local Maven repository

    ./gradlew publishToMavenLocal

### Using the plugin SNAPSHOT previously installed in the local Maven repository

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

Follow the [Scanner for Gradle Release Process](https://xtranet-sonarsource.atlassian.net/wiki/spaces/SSG/pages/1181729/Scanner+for+Gradle+Release+Process)

https://plugins.gradle.org/docs/publish-plugin

    ./gradlew release


License
-------

Copyright 2011-2025 SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt))
