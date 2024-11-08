# SonarScanner for Gradle

The SonarScanner for Gradle provides an easy way to start the scan of a Gradle project.

The ability to execute the SonarScanner analysis via a regular Gradle task makes it available anywhere Gradle is available (developer build,
CI server, etc.), without the need to manually download, setup, and maintain a SonarScanner CLI installation. The Gradle build already has
much of the information needed for the SonarScanner to successfully analyze a project. By preconfiguring the analysis based on that
information, the need for manual configuration is reduced significantly.

## [Prerequisites](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#prerequisites "Prerequisites")

* Gradle 7.3+
* Java 17

Bytecode created by `javac` compilation is required for Java analysis, including Android projects.

See also
the [general requirements on the scanner environment](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/scanner-environment/general-requirements/ "general requirements on the scanner environment").

## [Configure the scanner](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#configure-the-scanner "Configure the scanner")

Installation is automatic, but certain global properties should still be configured. A good place to configure global properties
is `~/.gradle/gradle.properties`. Be aware that the scanner uses system properties so all properties should be prefixed by `systemProp`.

```
# gradle.properties
systemProp.sonar.host.url=http://localhost:9000
```

## [Analyzing](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#analyzing "Analyzing")

First, you need to activate the scanner in your build. Kotlin DSL is the default choice for new Gradle builds.

Apply the SonarQube plugin dependency to your `build.gradle.kts` file:

```
plugins {
    id("org.sonarqube") version "versionNumber" // Replace with latest scanner version number
}

sonar {
  properties {
    property("sonar.projectKey", "myProjectKey")
    property("sonar.host.url", "myHostUrl")
  }
}
```

If you use Groovy DSL, it is still supported for Gradle 2.1+. In that case, apply the SonarQube plugin dependency to your `build.gradle`
file:

```
plugins {
  id "org.sonarqube" version "versionNumber" // Replace with latest scanner version number
}

sonar {
  properties {
    property "sonar.projectKey", "myProjectKey"
    property "sonar.host.url", "myHostUrl"
  }
}
```

Ensure that you declare the plugins in the correct sequence required by Gradle, that is, after the buildscript block in your `build.gradle`
file. More details on [Gradle - Plugin: org.sonarqube](https://plugins.gradle.org/plugin/org.sonarqube "Gradle - Plugin: org.sonarqube").

Assuming a local SonarQube server with out-of-the-box settings is up and running, no further configuration is required.

You need to pass an [authentication token](https://docs.sonarsource.com/sonarqube/latest/user-guide/managing-tokens/ "authentication token")
using one of the following options:

* Use the `sonar.token` property in your command line: Execute `gradle sonar -Dsonar.token=yourAuthenticationToken` and wait until the build
  has completed.
* Create the `SONAR_TOKEN` environment variable and set the token as its value before you run the analysis.

Once passing your token and running an analysis, open the web page indicated at the bottom of the console output. Your analysis results
should be available shortly after the CI-side analysis is complete.

<div>
  <p>The SonarScanners run on code that is checked out. See 
    <a class="internal-link" title="Verifying the code checkout step of your build" 
      href="/sonarqube/latest/analyzing-source-code/scanners/scanner-environment/verifying-code-checkout-step/">
      Verifying the code checkout step of your build</a>.
  </p>
</div>

## [Analyzing multi-project builds](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#analyzing-multi-project-builds "Analyzing multi-project builds")

To analyze a project hierarchy, apply the SonarQube plugin to the root project of the hierarchy. Typically (but not necessarily) this will
be the root project of the Gradle build. Information pertaining to the analysis as a whole has to be configured in the `sonar` block of this
project. Any properties set on the command line also apply to this project.

A configuration shared between subprojects can be configured in a subprojects block.

```
// build.gradle
subprojects {
    sonar {
        properties {
            property "sonar.sources", "src"
        }
    }
}
```

Project-specific information is configured in the `sonar` block of the corresponding project.

```
// build.gradle
project(":project1") {
    sonar {
        properties {
            property "sonar.branch.name", "Foo"
        }
    }}
```

To skip SonarScanner analysis for a particular subproject, set `sonarqube.skipProject` to true.

```
// build.gradle
project(":project2") {
    sonar {
        isSkipProject = true
    }
}
```

## [Task dependencies](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#task-dependencies "Task dependencies")

All tasks that produce output that should be included in the SonarScanner analysis need to be executed before the `sonar` task runs.
Typically, these are compile tasks, test tasks,
and [code coverage](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/test-coverage/overview/ "code coverage") tasks.

Starting with v3.0 of the SonarScanner for Gradle, task dependencies are no longer added automatically. Instead, the SonarScanner plugin
enforces the correct order of tasks with `mustRunAfter`. You need to be either manually run the tasks that produce output
before `sonarqube`, or you can add a dependency to the build script:

```
// build.gradle
project.tasks["sonar"].dependsOn "anotherTask"
```

## [Sample project](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#sample-project "Sample project")

A simple working example is available at this URL so you can check everything is correctly configured in your env:  
[https://github.com/SonarSource/sonar-scanning-examples/tree/master/sonar-scanner-gradle/gradle-basic](https://github.com/SonarSource/sonar-scanning-examples/tree/master/sonar-scanner-gradle/gradle-basic "https://github.com/SonarSource/sonar-scanning-examples/tree/master/sonar-scanner-gradle/gradle-basic")

## [Adjusting the analysis scope](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#adjusting-the-analysis-scope "Adjusting the analysis scope ")

The analysis scope of a project determines the source and test files to be analyzed.

An initial analysis scope is set by default. With the SonarScanner for Gradle, the initial analysis scope is:

* For source files: all the files stored under `src/main/java` (in the root or module directories).
* For test files: all the files stored under `src/test/java` (in the root or module directories).

Since SonarScanner for Gradle also supports Groovy and Kotlin, the initial scope will also include `src/main/kotlin` or `src/main/groovy`
for source and test files, depending on the type of project.

To adjust the analysis scope, you can:

* Adjust the initial scope: see below.
* And/or exclude specific files from the initial scope:
  see [Analysis scope](https://docs.sonarsource.com/sonarqube/latest/project-administration/analysis-scope/ "Analysis scope").

### [Adjusting the initial scope](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#adjusting-the-initial-scope "Adjusting the initial scope")

The initial scope is set through the `sonar.sources` property (for source files) and the `sonar.tests` property (for test files).
See [Analysis parameters](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/analysis-parameters/ "Analysis parameters")
for more information.

To adjust the initial scope, you can:

* Either override these properties by setting them explicitly in your build like any other relevant gradle property:
  see [Analysis scope](https://docs.sonarsource.com/sonarqube/latest/project-administration/analysis-scope/ "Analysis scope").
* Or use the scanAll option to extend the initial scope to non-JVM-related files. See below.

### [Using the scanAll option to include non-JVM-related files](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/using-the-scanall-option-to-include-nonjvmrelated-files "Using the scanAll option to include non-JVM-related files")

You may want to analyze not only the JVM main files but also files related to configuration, infrastructure, etc. An easy way to do that is
to enable the scanAll option (By default, this option is disabled.)

If the scanAll option is enabled then the initial analysis scope of _source files_ will be:

* The files stored in _src/main/java_ (and _src/main/kotlin_ or _src/main/groovy_, depending on the type of project).
* The non-JVM-related files stored in the root directory of your project.

<div role="alert">
  <div>
    <p>The scanAll option is disabled if the <code>sonar.sources</code> or <code>sonar.tests</code> property is overridden.</p>
  </div>
</div>

To enable the scanAll option, Set the `sonar.gradle.scanAll` property to `True`.

## [Analysis property defaults](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#analysis-property-defaults "Analysis property defaults")

The SonarScanner for Gradle uses information contained in Gradle's object model to provide smart defaults for most of the
standard [analysis parameters](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/analysis-parameters/ "analysis parameters"),
as listed below. Note that additional defaults are provided depending on the projects.

### [Gradle defaults for standard Sonar properties](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#gradle-defaults-for-standard-sonar-properties "Gradle defaults for standard Sonar properties")

| Property  | Gradle default                                                                                         |
|:----------|:-------------------------------------------------------------------------------------------------------|
| `sonar.projectKey` | `$[{project.group}:]${project.name}` for root module; `<root module key>:<module path>` for submodules |
| `sonar.projectName` | `${project.name}`                                                                                     |
| `sonar.projectDescription` | `${project.description}`                                                                       |
| `sonar.projectVersion` | `${project.version}`                                                                               |
| `sonar.projectBaseDir` | `${project.projectDir}`                                                                            |
| `sonar.working.directory` | `${project.buildDir}/sonar`                                                                     |

### [Additional defaults for projects with Java-base or Java plugin applied](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#additional-defaults-for-projects-with-javabase-or-java-plugin-applied "Additional defaults for projects with Java-base or Java plugin applied")

| Property                   | Gradle default                                                                                         |
|:---------------------------|:-------------------------------------------------------------------------------------------------------|
| `sonar.sourceEncoding`     | `${project.compileJava.options.encoding}`                                                              |
| `sonar.java.source`        | `${project.targetCompatibility}`                                                                       |
| `sonar.java.target`        | `${project.targetCompatibility}`                                                                       |
| `sonar.sources`            | `${sourceSets.main.allJava.srcDirs}` (filtered to only include existing directories)                    |
| `sonar.tests`              | `${sourceSets.test.allJava.srcDirs}` (filtered to only include existing directories)                    |
| `sonar.java.binaries`      | `${sourceSets.main.output.classesDir}`                                                                 |
| `sonar.java.libraries`     | `${sourceSets.main.compileClasspath}` (filtering to only include files; rt.jar and jfxrt.jar added if necessary) |
| `sonar.java.test.binaries` | `${sourceSets.test.output.classesDir}`                                                                 |
| `sonar.java.test.libraries`| `${sourceSets.test.compileClasspath}` (filtering to only include files; rt.jar and jfxrt.jar added if necessary) |
| `sonar.junit.reportPaths`  | `${test.testResultsDir}` (if the directory exists)                                                     |

### [Additional default for Groovy projects](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#additional-default-for-groovy-projects "Additional default for Groovy projects")

| Property                | Gradle default                         |
|:------------------------|:---------------------------------------|
| `sonar.groovy.binaries` | `${sourceSets.main.output.classesDir}` |

### [Additional defaults for Android projects](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#additional-defaults-for-android-projects "Additional defaults for Android projects ")

Additional defaults are provided for Android projects (`com.android.application`, `com.android.library`, or `com.android.test`). By default
the first variant of type `debug` will be used to configure the analysis. You can override the name of the variant to be used using the
parameter `androidVariant`:

| Property                                    | Gradle default                                                                                         |
|:--------------------------------------------|:-------------------------------------------------------------------------------------------------------|
| `sonar.sources` (**for non-test variants**) | `${variant.sourcesets.map}` (`ManifestFile/CDirectories/AidlDirectories/AssetsDirectories/CppDirectories/JavaDirectories/RenderscriptDirectories/ResDirectories/ResourcesDirectories`) |
| `sonar.tests` (**for test variants**)       | `${variant.sourcesets.map}` (`ManifestFile/CDirectories/AidlDirectories/AssetsDirectories/CppDirectories/JavaDirectories/RenderscriptDirectories/ResDirectories/ResourcesDirectories`) |
| `sonar.java[.test].binaries`                | `${variant.destinationDir}`                                                                 |
| `sonar.java[.test].libraries`               | `${variant.javaCompile.classpath} + ${bootclasspath}`                                       |
| `sonar.java.source`                         | `${variant.javaCompile.sourceCompatibility}`                                                |
| `sonar.java.target`                         | `${variant.javaCompile.targetCompatibility}`                                                |

```
build.gradle
sonar {
    androidVariant 'fullDebug'
}
```

## [Passing manual properties / overriding defaults](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#manual-properties "Passing manual properties / overriding defaults")

The SonarScanner for Gradle adds a `sonar` extension to the project and its subprojects, which allows you to configure/override the analysis
properties.

```
// in build.gradle
sonar {
    properties {
        property "sonar.exclusions", "**/*Generated.java"
    }
}
```

Sonar properties can also be set from the command line, or by setting a system property named exactly like the Sonar property in question.
This can be useful when dealing with sensitive information (e.g. credentials), environment information, or for ad-hoc configuration.

```
gradle sonar -Dsonar.host.url=http://sonar.mycompany.com -Dsonar.verbose=true
```

While certainly useful at times, we recommend keeping the bulk of the configuration in a (versioned) build script, readily available to
everyone. A Sonar property value set via a system property overrides any value set in a build script (for the same property). When analyzing
a project hierarchy, values set via system properties apply to the root project of the analyzed hierarchy. Each system property starting
with `sonar.` will be taken into account.

## [Analyzing custom source sets](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#custom-source-sets "Analyzing custom source sets")

By default, the SonarScanner for Gradle passes on the project's main source set as production sources, and the project's test source set as
test sources. This works regardless of the project's source directory layout. Additional source sets can be added as needed.

```
// build.gradle
sonar {
    properties {
        properties["sonar.sources"] += sourceSets.custom.allSource.srcDirs
        properties["sonar.tests"] += sourceSets.integTest.allSource.srcDirs
    }
}
```

## [Advanced topics](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#advanced-topics "Advanced topics")

### [If your SonarQube server is secured](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#if-your-sonarqube-server-is-secured "If your SonarQube server is secured")

If your SonarQube server
is [configured with HTTPS](https://docs.sonarsource.com/sonarqube/latest/setup-and-upgrade/operating-the-server/#securing-the-server-behind-a-proxy "configured with HTTPS")
and a self-signed certificate then you must add the self-signed certificate to the trusted CA certificates of the SonarScanner. In addition,
if mutual TLS is used then you must define the access to the client certificate at the SonarScanner level.

See [Managing the TLS certificates on the client side](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/scanner-environment/manage-tls-certificates/ "Managing the TLS certificates on the client side").

### [More on configuring SonarQube properties](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#more-on-configuring-sonarqube-properties "More on configuring SonarQube properties")

Let's take a closer look at the `sonar.properties` block. As we have already seen in the examples, the `property` method allows you to set
new properties or override existing ones. Furthermore, all properties that have been configured up to this point, including all properties
preconfigured by Gradle, are available via the properties accessor.

Entries in the properties map can be read and written with the usual Groovy syntax. To facilitate their manipulation, values still have
their “idiomatic” type (File, List, etc.). After the `sonar.properties` block has been evaluated, values are converted to Strings as
follows: Collection values are (recursively) converted to comma-separated Strings, and all other values are converted by calling
their `toString` methods.

Because the `sonar.properties` block is evaluated lazily, properties of Gradle's object model can be safely referenced from within the
block, without having to fear that they have not yet been set.

## [Troubleshooting](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#troubleshooting "Troubleshooting")

#### [If you get a java.lang.OutOfMemoryError: Metaspace](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#if-you-get-a-javalangoutofmemoryerror-metaspace "If you get a java.lang.OutOfMemoryError: Metaspace")

Increase the metaspace size in your `gradle.properties` file:

```
org.gradle.jvmargs=-XX:MetaspaceSize=512M -XX:MaxMetaspaceSize=512M
```

#### [Task not found in root project](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/#task-not-found-in-root-project "Task not found in root project")

Sometimes Gradle has a difficult time seeing arguments as arguments and instead sees them as tasks to perform. When passing commands on
Windows, this can be overcome by passing the parameters inside of quotation marks; use `-D “key=value”` instead.

For example, the argument `-D sonar.projectKey=<your-project>` should be passed as `-D "sonar.projectKey=<your-project>"`