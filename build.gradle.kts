plugins {
    id("java-gradle-plugin")
    java
    jacoco
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.21.0"
    id("com.jfrog.artifactory") version "4.24.23"
    id("com.github.hierynomus.license") version "0.16.1"
    id("pl.droidsonroids.jacoco.testkit") version "1.0.9"
    id("org.cyclonedx.bom") version "1.5.0"
    signing
}

val projectTitle: String by project

val docUrl = "http://redirect.sonarsource.com/doc/gradle.html"
val githubUrl = "https://github.com/SonarSource/sonar-scanner-gradle"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
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

repositories {
    maven {
        url = uri("https://maven.google.com")
    }

    val repository = if (project.hasProperty("qa")) "sonarsource-qa" else "sonarsource"
    maven {
        url = uri("https://repox.jfrog.io/repox/${repository}")

        // The environment variables ARTIFACTORY_PRIVATE_USERNAME and ARTIFACTORY_PRIVATE_PASSWORD are used on QA env (Jenkins)
        // On local box, please add artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
        val artifactoryUsername = System.getenv("ARTIFACTORY_PRIVATE_USERNAME") ?: project.findProperty("artifactoryUsername") ?: ""
        val artifactoryPassword = System.getenv("ARTIFACTORY_PRIVATE_PASSWORD") ?: project.findProperty("artifactoryPassword") ?: ""

        if (artifactoryUsername is String && artifactoryUsername.isNotEmpty() && artifactoryPassword is String && artifactoryPassword.isNotEmpty()) {
            credentials {
                username = artifactoryUsername
                password = artifactoryPassword
            }
        }
    }
}

dependencies {
    implementation("org.sonarsource.scanner.api:sonar-scanner-api:2.16.2.588")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("com.android.tools.build:gradle:7.3.1")
    testImplementation("com.android.tools.build:gradle:7.3.1")
    testImplementation(localGroovy())
    testImplementation("org.assertj:assertj-core:3.11.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("org.hamcrest:hamcrest-all:1.3")
    testImplementation("org.mockito:mockito-core:4.7.0")
    testImplementation("org.spockframework:spock-core:2.1-groovy-3.0") {
        exclude(module = "groovy-all")
    }
}

gradlePlugin {
    plugins {
        create("sonarqubePlugin") {
            displayName = projectTitle
            description = project.description
            id = "org.sonarqube"
            implementationClass = "org.sonarqube.gradle.SonarQubePlugin"
        }
    }
}

pluginBundle {
    website = docUrl
    vcsUrl = githubUrl
    tags = listOf("sonarqube", "sonar", "quality", "qa")
    group = project.group as String
}


license {
    header = rootProject.file("HEADER")
    mapping("java", "SLASHSTAR_STYLE")
    strictCheck = true
    exclude("**/version.txt")
}

jacoco {
    toolVersion = "0.8.7"
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
    }
}

apply(from = "sonarqube-build.gradle")
tasks.getByName("sonarqube").dependsOn(tasks.jacocoTestReport)

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
    filesMatching("**/version.txt") {
        expand(mapOf("version" to project.version))
    }
}

signing {
    val signingKeyId: String? by project
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    setRequired {
        val branch = System.getenv()["CIRRUS_BRANCH"] ?: ""
        (branch == "master" || branch.matches("branch-[\\d.]+".toRegex())) &&
            gradle.taskGraph.hasTask(":artifactoryPublish")
    }
    sign(publishing.publications)
}

tasks.withType<Sign> {
    onlyIf {
        val branch = System.getenv()["CIRRUS_BRANCH"] ?: ""
        val artifactorySkip: Boolean = tasks.artifactoryPublish.get().skip
        !artifactorySkip && (branch == "master" || branch.matches("branch-[\\d.]+".toRegex())) &&
            gradle.taskGraph.hasTask(":artifactoryPublish")
    }
}

tasks.withType<Test>().configureEach {
    doLast {
        Thread.sleep(2000) // https://github.com/gradle/gradle/issues/16603
    }
}