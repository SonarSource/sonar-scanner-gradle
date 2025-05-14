plugins {
    id("java-gradle-plugin")
    java
    groovy
    jacoco
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.3.1"
    id("com.jfrog.artifactory") version "4.24.23"
    id("com.github.hierynomus.license") version "0.16.1"
    id("pl.droidsonroids.jacoco.testkit") version "1.0.9"
    id("org.cyclonedx.bom") version "1.5.0"
    signing
}

val projectTitle: String by project

val docUrl = "http://redirect.sonarsource.com/doc/gradle.html"
val githubUrl = "https://github.com/SonarSource/sonar-scanner-gradle"

// Only configure "signing" plugin if build's artifacts need to be published to artifactory or Gradle Plugin Portal
// and the branch is "master" or "branch-*" (we don't want to sign PRs)
val doArtifactsRequireSignature: () -> Boolean = {
    val branch = System.getenv()["CIRRUS_BRANCH"] ?: ""

    val isSafeBranch = (branch == "master")
        || branch.matches("branch-[\\d.]+".toRegex())

    val isPublishTask = (gradle.taskGraph.hasTask(":artifactoryPublish") && !tasks.artifactoryPublish.get().skip)

    isSafeBranch && isPublishTask
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(11)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

val buildNumber: String? = System.getProperty("buildNumber")
ext {
    set("buildNumber", buildNumber)
}
// Replaces the version defined in sources, usually x.y-SNAPSHOT, by a version identifying the build.
if (project.version.toString().endsWith("-SNAPSHOT") && buildNumber != null) {
    val versionSuffix =
        if (project.version.toString().count { it == '.' } == 1) ".0.$buildNumber" else ".$buildNumber"
    project.version = project.version.toString().replace("-SNAPSHOT", versionSuffix)
}

dependencies {
    implementation("org.sonarsource.scanner.lib:sonar-scanner-java-library:3.3.1.450")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("com.android.tools.build:gradle:8.1.1")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.21")
    testImplementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.21")
    testImplementation("com.android.tools.build:gradle:8.1.1")
    testImplementation(localGroovy())
    testImplementation("org.assertj:assertj-core:3.26.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.hamcrest:hamcrest-all:1.3")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.spockframework:spock-core:2.3-groovy-3.0") {
        exclude(module = "groovy-all")
    }
}

gradlePlugin {
    plugins {
        create("sonarqubePlugin") {
            id = "org.sonarqube"
            group = project.group as String
            displayName = projectTitle
            description = project.description
            implementationClass = "org.sonarqube.gradle.SonarQubePlugin"
            tags.set(listOf("sonarqube", "sonar", "quality", "qa"))
            website.set(docUrl)
            vcsUrl.set(githubUrl)
        }
    }
}

license {
    header = rootProject.file("HEADER")
    mapping("java", "SLASHSTAR_STYLE")
    strictCheck = true
    exclude("**/*-version.txt")
}

jacoco {
    toolVersion = "0.8.7"
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
    }
}

apply(from = "sonarqube-build.gradle.kts")

tasks.getByName("sonar").dependsOn(tasks.jacocoTestReport)

val bomFile = layout.buildDirectory.file("reports/bom.json")
tasks.cyclonedxBom {
    setIncludeConfigs(includeConfigs + listOf("runtimeClasspath"))
    outputs.file(bomFile)
    outputs.upToDateWhen { false }
}

val bomArtifact = artifacts.add("archives", bomFile.get().asFile) {
    type = "json"
    classifier = "cyclonedx"
    builtBy("cyclonedxBom")
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            pom {
                name.set(projectTitle)
                description.set(project.description)
                url.set(docUrl)
                organization {
                    name.set("SonarSource")
                    url.set("http://www.sonarqube.org/")
                }
                licenses {
                    license {
                        name.set("GNU LPGL 3")
                        url.set("http://www.gnu.org/licenses/lgpl.txt")
                        distribution.set("repo")
                    }
                }
                scm {
                    url.set(githubUrl)
                }
                developers {
                    developer {
                        id.set("sonarsource-team")
                        name.set("SonarSource Team")
                    }
                }
            }
            artifact(bomArtifact)
        }
    }
}

artifactory {
    clientConfig.isIncludeEnvVars = true
    clientConfig.envVarsExcludePatterns =
        "*password*,*PASSWORD*,*secret*,*MAVEN_CMD_LINE_ARGS*,sun.java.command,*token*,*TOKEN*,*LOGIN*,*login*,*KEY*,*signing*"
    setContextUrl(System.getenv("ARTIFACTORY_URL"))
    publish {
        repository {
            setRepoKey(System.getenv("ARTIFACTORY_DEPLOY_REPO"))
            setUsername(System.getenv("ARTIFACTORY_DEPLOY_USERNAME"))
            setPassword(System.getenv("ARTIFACTORY_DEPLOY_PASSWORD"))
        }
        defaults {
            setProperty(
                "properties",
                mapOf(
                    "build.name" to "sonar-scanner-gradle",
                    "build.number" to System.getenv("BUILD_NUMBER"),
                    "pr.branch.target" to System.getenv("PULL_REQUEST_BRANCH_TARGET"),
                    "pr.number" to System.getenv("PULL_REQUEST_NUMBER"),
                    "vcs.branch" to System.getenv("GIT_BRANCH"),
                    "vcs.revision" to System.getenv("GIT_COMMIT"),
                    "version" to project.version as String,
                )
            )
            publications("pluginMaven")
            setPublishPom(true)
            setPublishIvy(false)
        }
    }
    clientConfig.info.buildName = "sonar-scanner-gradle"
    clientConfig.info.buildNumber = System.getenv("BUILD_NUMBER")
    // The name of this variable is important because it's used by the delivery process when extracting version from Artifactory build info.
    clientConfig.info.addEnvironmentProperty("PROJECT_VERSION", version.toString())
}

tasks.processResources {
    filesMatching("org/sonarqube/gradle/sonarqube-gradle-plugin-version.txt") {
        expand(mapOf("version" to project.version))
    }
}

signing {
    val signingKeyId: String? by project
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    setRequired {
      doArtifactsRequireSignature()
    }
    sign(publishing.publications)
}

tasks.withType<Sign> {
    onlyIf {
      doArtifactsRequireSignature()
    }
}

tasks.withType<Test>().configureEach {
    doLast {
        Thread.sleep(2000) // https://github.com/gradle/gradle/issues/16603
    }
}
