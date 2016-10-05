package org.sonarqube.gradle;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    File out = temp.newFile();
    runGradlewSonarQubeWithEnv(project, env, "-DsonarRunner.dumpToFile=" + out.getAbsolutePath());

    Properties props = new Properties();
    try (FileReader fr = new FileReader(out)) {
      props.load(fr);
    }
    return props;
  }

  protected RunResult runGradlewSonarQubeWithEnv(String project, Map<String, String> env, String... args) throws Exception {
    RunResult result = runGradlewSonarQubeWithEnvQuietly(project, env, args);
    if (result.exitValue != 0) {
      throw new RuntimeException(result.log);
    }
    return result;
  }

  protected RunResult runGradlewSonarQubeWithEnvQuietly(String project, Map<String, String> env, String... args) throws Exception {
    File projectBaseDir = new File(this.getClass().getResource(project).toURI());
    File tempProjectDir = temp.newFolder(project);
    File outputFile = temp.newFile();
    FileUtils.copyDirectory(projectBaseDir, tempProjectDir);
    List<String> command = new ArrayList<>();
    if (System.getProperty("os.name").startsWith("Windows")) {
      command.addAll(Arrays.asList("cmd.exe", "/C"));
    } else {
      command.add("/bin/bash");
    }
    command.addAll(Arrays.asList("gradlew", "--stacktrace", "--no-daemon", "sonarqube"));
    command.addAll(Arrays.asList(args));
    ProcessBuilder pb = new ProcessBuilder(command)
      .directory(tempProjectDir)
      .redirectOutput(outputFile)
      .redirectErrorStream(true);
    pb.environment().put("GRADLE_OPTS", "-Xmx1024m");
    pb.environment().putAll(env);
    Process p = pb.start();
    p.waitFor();

    String output = FileUtils.readFileToString(outputFile, StandardCharsets.UTF_8);

    return new RunResult(output, p.exitValue());
  }

  protected static class RunResult {
    private String log;
    private int exitValue;

    RunResult(String log, int exitValue) {
      this.log = log;
      this.exitValue = exitValue;
    }

    public String getLog() {
      return log;
    }

    public int getExitValue() {
      return exitValue;
    }
  }
}
