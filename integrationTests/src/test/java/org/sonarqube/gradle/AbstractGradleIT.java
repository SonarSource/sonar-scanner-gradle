package org.sonarqube.gradle;

import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public abstract class AbstractGradleIT {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  protected Properties runGradlewSonarQubeSimulationMode(String project) throws Exception {
    return runGradlewSonarQubeSimulationModeWithEnv(project, Collections.emptyMap());
  }

  protected Properties runGradlewSonarQubeSimulationModeWithEnv(String project, Map<String, String> env) throws Exception {
    File projectBaseDir = new File(this.getClass().getResource(project).toURI());
    File tempProjectDir = temp.newFolder(project);
    FileUtils.copyDirectory(projectBaseDir, tempProjectDir);
    File out = temp.newFile();
    ProcessBuilder pb = new ProcessBuilder("/bin/bash", "gradlew", "--stacktrace", "--no-daemon", "sonarqube", "-DsonarRunner.dumpToFile=" + out.getAbsolutePath())
      .directory(tempProjectDir)
      .inheritIO();
    pb.environment().put("GRADLE_OPTS", "-Xmx1024m");
    pb.environment().putAll(env);
    Process p = pb.start();
    p.waitFor();

    Properties props = new Properties();
    try (FileReader fr = new FileReader(out)) {
      props.load(fr);
    }
    return props;
  }
}
