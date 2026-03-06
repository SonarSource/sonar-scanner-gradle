package com.gradle.publish;

import com.gradle.publish.protocols.v1.models.publish.PublishArtifact;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

/**
 * This task is used to download Maven artifacts and publish them to the Gradle Plugin Portal.
 */
public abstract class DownloadMavenArtifactsAndPublishToGradlePluginPortal extends DefaultTask {

  private static final Logger LOGGER = Logging.getLogger(DownloadMavenArtifactsAndPublishToGradlePluginPortal.class);

  private static final Set<String> REQUIRED_ARTIFACT_TYPES = Set.of(
    "pom",
    "pom.asc",
    "jar",
    "jar.asc",
    "jar:sources",
    "jar.asc:sources",
    "jar:javadoc",
    "jar.asc:javadoc",
    "module",
    "module.asc");

  @Input
  private String pluginConfigurationName;

  @Input
  private String mavenSourceRepositoryUrl;

  @Input
  private String mavenAuthorizationToken;

  @Input
  private String groupId;

  @Input
  private String artifactId;

  @Input
  private String version;

  @Input
  private boolean validationOnly;

  @Input
  private boolean simulatePublication;

  /**
   * Main method to execute the task.
   * @throws Exception if any error occurs during task execution
   */
  @TaskAction
  public void executeTask() throws InterruptedException {
    downloadRequiredArtifactsInBuildFolder();
    registerDownloadedSignatureArtifactsOnPublication();
    validatePublishContent();
    if (simulatePublication) {
      try (var localGradlePortal = new SimpleGradlePluginPortalServer()) {
        overrideSystemProperty("gradle.portal.url", localGradlePortal.getServerUrl(), this::executePublishTask);
      }
    } else {
      executePublishTask();
    }
  }

  private static void overrideSystemProperty(String propertyName, String newValue, Runnable runnable) {
    String oldValue = System.getProperty("gradle.portal.url");
    try {
      System.setProperty(propertyName, newValue);
      runnable.run();
    } finally {
      if (oldValue != null) {
        System.setProperty(propertyName, oldValue);
      } else {
        System.clearProperty(propertyName);
      }
    }
  }

  /**
   * Download the required artifacts from "mavenSourceRepositoryUrl" and save them in the build folder like the "build" task would do.
   */
  private void downloadRequiredArtifactsInBuildFolder() throws InterruptedException {
    String baseUrl = (mavenSourceRepositoryUrl.endsWith("/")) ? mavenSourceRepositoryUrl : (mavenSourceRepositoryUrl + "/");
    String groupUrl = baseUrl + groupId.replace('.', '/');
    String artifactDirectoryUrl = groupUrl + "/" + artifactId + "/" + version + "/";
    LOGGER.info("Downloading artifacts to publish from  {}", artifactDirectoryUrl);
    String artifactUrlPrefix = artifactDirectoryUrl + artifactId + "-" + version;

    HttpClient client = HttpClient.newHttpClient();

    Path pluginMavenDir = Path.of("build", "publications", "pluginMaven");
    Path libsDir = Path.of("build", "libs");
    Path pluginMarkerDir = Path.of("build", "publications", pluginConfigurationName + "PluginMarkerMaven");

    downloadArtifact(client, artifactUrlPrefix + ".pom", pluginMavenDir.resolve("pom-default.xml"), pluginMarkerDir.resolve("pom-default.xml"));
    downloadArtifact(client, artifactUrlPrefix + ".pom.asc", pluginMavenDir.resolve("pom-default.xml.asc"), pluginMarkerDir.resolve("pom-default.xml.asc"));
    downloadArtifact(client, artifactUrlPrefix + ".jar", libsDir.resolve(artifactId + "-" + version + ".jar"));
    downloadArtifact(client, artifactUrlPrefix + ".jar.asc", libsDir.resolve(artifactId + "-" + version + ".jar.asc"));
    downloadArtifact(client, artifactUrlPrefix + "-sources.jar", libsDir.resolve(artifactId + "-" + version + "-sources.jar"));
    downloadArtifact(client, artifactUrlPrefix + "-sources.jar.asc", libsDir.resolve(artifactId + "-" + version + "-sources.jar.asc"));
    downloadArtifact(client, artifactUrlPrefix + ".module", pluginMavenDir.resolve("module.json"));
    downloadArtifact(client, artifactUrlPrefix + ".module.asc", pluginMavenDir.resolve("module.json.asc"));
    downloadArtifact(client, artifactUrlPrefix + "-javadoc.jar", libsDir.resolve(artifactId + "-" + version + "-javadoc.jar"));
    downloadArtifact(client, artifactUrlPrefix + "-javadoc.jar.asc", libsDir.resolve(artifactId + "-" + version + "-javadoc.jar.asc"));
  }

  /**
   * Register the downloaded .asc signature files on the pluginMaven publication so that the
   * plugin-publish plugin's collectArtifacts() sees them and validation passes.
   */
  private void registerDownloadedSignatureArtifactsOnPublication() {
    File rootDir = getProject().getRootDir();
    Path pluginMavenDir = Path.of("build", "publications", "pluginMaven");
    Path libsDir = Path.of("build", "libs");

    File pomAsc = rootDir.toPath().resolve(pluginMavenDir).resolve("pom-default.xml.asc").toFile();
    File jarAsc = rootDir.toPath().resolve(libsDir).resolve(artifactId + "-" + version + ".jar.asc").toFile();
    File sourcesJarAsc = rootDir.toPath().resolve(libsDir).resolve(artifactId + "-" + version + "-sources.jar.asc").toFile();
    File javadocJarAsc = rootDir.toPath().resolve(libsDir).resolve(artifactId + "-" + version + "-javadoc.jar.asc").toFile();
    File moduleAsc = rootDir.toPath().resolve(pluginMavenDir).resolve("module.json.asc").toFile();

    if (!pomAsc.isFile() || !jarAsc.isFile() || !sourcesJarAsc.isFile() || !javadocJarAsc.isFile() || !moduleAsc.isFile()) {
      throw new GradleException(
        "Signature files missing under build/ after download. Ensure .asc files exist in the release repository. "
            + "pom.asc=" + pomAsc.isFile() + " jar.asc=" + jarAsc.isFile()
            + " sources.asc=" + sourcesJarAsc.isFile() + " javadoc.asc=" + javadocJarAsc.isFile()
            + " module.asc=" + moduleAsc.isFile());
    }

    PublishingExtension publishing = getProject().getExtensions().getByType(PublishingExtension.class);
    MavenPublication publication = publishing.getPublications().withType(MavenPublication.class).getByName("pluginMaven");

    publication.artifact(pomAsc, artifact -> {
      artifact.setExtension("pom.asc");
      artifact.setClassifier(null);
    });
    publication.artifact(jarAsc, artifact -> {
      artifact.setExtension("jar.asc");
      artifact.setClassifier(null);
    });
    publication.artifact(sourcesJarAsc, artifact -> {
      artifact.setExtension("jar.asc");
      artifact.setClassifier("sources");
    });
    publication.artifact(javadocJarAsc, artifact -> {
      artifact.setExtension("jar.asc");
      artifact.setClassifier("javadoc");
    });
    publication.artifact(moduleAsc, artifact -> {
      artifact.setExtension("module.asc");
      artifact.setClassifier(null);
    });

    LOGGER.info("Registered downloaded signature artifacts on publication pluginMaven");
  }

  /**
   * Validate that minimum publish content contains: pom, jar, sources, javadoc, module and their signatures.
   */
  private void validatePublishContent() {
    LOGGER.info("ValidatePublishContent artifacts");
    Map<PublishArtifact, File> artifacts = collectArtifacts();
    artifacts.forEach((artifact, file) -> LOGGER.info("Publish artifact {} from file {}", artifact.encode(), file));

    Set<String> actualArtifactTypes = artifacts.keySet().stream().map(PublishArtifact::getType).collect(Collectors.toSet());

    Set<String> missingArtifactTypes = REQUIRED_ARTIFACT_TYPES.stream()
      .filter(type -> !actualArtifactTypes.contains(type))
      .collect(Collectors.toSet());

    if (!missingArtifactTypes.isEmpty()) {
      String missingArtifactTypesString = String.join(", ", missingArtifactTypes);
      throw new GradleException("Missing required artifact types: " + missingArtifactTypesString);
    }
  }

  public void downloadArtifact(HttpClient client, String artifactUrl, Path... destinationPaths) throws InterruptedException {
    LOGGER.info("Downloading {}", artifactUrl);
    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(artifactUrl))
      .header("Authorization", "Bearer " + mavenAuthorizationToken)
      .GET()
      .build();
    HttpResponse<byte[]> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException e) {
      throw new GradleException("Failed to download content from '" + artifactUrl + "'. Error: " + e.getMessage(), e);
    }
    if (response.statusCode() != 200) {
      if (response.statusCode() == 404 && artifactUrl.contains(".asc")) {
        throw new GradleException(
          "Signature file missing in release repository: '" + artifactUrl + "'. "
              + "Ensure .asc files are promoted to the release repository (e.g. sonarsource-public-releases).");
      }
      throw new GradleException("Failed to download content from '" + artifactUrl + "'. HTTP status: " + response.statusCode());
    }
    byte[] content = response.body();
    for (Path destinationPath : destinationPaths) {
      Path rootDir = getProject().getRootDir().toPath();
      Path destination = rootDir.resolve(destinationPath);
      LOGGER.info("Saving to {}", destination);
      try {
        Files.createDirectories(destination.getParent());
        Files.write(destination, content);
      } catch (Exception e) {
        throw new GradleException("Failed to save content to '" + destination + "'. Error: " + e.getMessage(), e);
      }
    }
  }

  private void executePublishTask() {
    try {
      LOGGER.info("Invoke 'publishPlugins' task (validationOnly: {})", validationOnly);
      PublishTask publishTask = (PublishTask) getProject().getTasks().getByName("publishPlugins");
      publishTask.setValidate(validationOnly);
      publishTask.executeTask();
    } catch (Exception e) {
      throw new GradleException("Failed to invoke task 'publishPlugins': " + e.getMessage(), e);
    }
  }

  private Map<PublishArtifact, File> collectArtifacts() {
    try {
      PublishTask publishTask = (PublishTask) getProject().getTasks().getByName("publishPlugins");
      Method collectArtifacts = PublishTask.class.getDeclaredMethod("collectArtifacts");
      collectArtifacts.setAccessible(true);
      return (Map<PublishArtifact, File>) collectArtifacts.invoke(publishTask);
    } catch (Exception e) {
      throw new GradleException("Failed to collect artifacts: " + e.getMessage(), e);
    }
  }

  /**
   * @return The plugin name in the gradle configuration
   */
  public String getPluginConfigurationName() {
    return pluginConfigurationName;
  }

  /**
   * @param pluginConfigurationName indicate the plugin name in the gradle configuration
   */
  public void setPluginConfigurationName(String pluginConfigurationName) {
    this.pluginConfigurationName = pluginConfigurationName;
  }

  /**
   * @return The maven repository URL to download artifacts from.
   */
  public String getMavenSourceRepositoryUrl() {
    return mavenSourceRepositoryUrl;
  }

  /**
   * @param mavenSourceRepositoryUrl set the maven repository URL to download artifacts from.
   */
  public void setMavenSourceRepositoryUrl(String mavenSourceRepositoryUrl) {
    this.mavenSourceRepositoryUrl = mavenSourceRepositoryUrl;
  }

  /**
   * @return The authorization token to access the maven repository.
   */
  public String getMavenAuthorizationToken() {
    return mavenAuthorizationToken;
  }

  /**
   * @param mavenAuthorizationToken set the authorization token to access the maven repository.
   */
  public void setMavenAuthorizationToken(String mavenAuthorizationToken) {
    this.mavenAuthorizationToken = mavenAuthorizationToken;
  }

  /**
   * @return The group ID of the artifact to download.
   */
  public String getGroupId() {
    return groupId;
  }

  /**
   * @param groupId set the group ID of the artifact to download.
   */
  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  /**
   * @return The artifact ID of the artifact to download.
   */
  public String getArtifactId() {
    return artifactId;
  }

  /**
   * @param artifactId set the artifact ID of the artifact to download.
   */
  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }

  /**
   * @return The version of the artifact to download.
   */
  public String getVersion() {
    return version;
  }

  /**
   * @param version set the version of the artifact to download.
   */
  public void setVersion(String version) {
    this.version = version;
  }

  /**
   * @return The validation mode to ask Gradle plugin portal to validate the plugin without publishing it.
   */
  public boolean getValidationOnly() {
    return validationOnly;
  }

  /**
   * @param validationOnly set the validation mode to ask Gradle plugin portal to validate the plugin without publishing it.
   */
  public void setValidationOnly(boolean validationOnly) {
    this.validationOnly = validationOnly;
  }

  /**
   * @return The simulation mode, simulation means using a fake localhost Gradle plugin portal and simulate the publication
   */
  public boolean getSimulatePublication() {
    return simulatePublication;
  }

  /**
   * @param simulatePublication set the simulation mode, simulation means using a fake localhost Gradle plugin portal and simulate the publication
   */
  public void setSimulatePublication(boolean simulatePublication) {
    this.simulatePublication = simulatePublication;
  }

}
