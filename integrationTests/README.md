## Add a Project to the End-to-End Tests

First, add your Gradle project to `/src/test/resources`.

During end-to-end test executions, Maven will pull the SonarQube plugin from Repox, install it in the classpath, and replace the following property placeholders in the `/test/resources` files:
- `${gradle.version}`
- `${androidGradle.version}`
- `${version}`

You need to ensure that the following values are parameterized:
- the Gradle version; the simplest way is to parameterize the `distributionUrl` inside `gradle-wrapper.properties`
- if your project depends on Android, the Android Gradle version
- the version of the plugin

The last step is to configure Gradle to find the SonarQube plugin. Copy `src/test/resources/gradle-9-example/settings.gradle.kts` and add it to your project.