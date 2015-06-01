package org.sonarqube.gradle;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

public class GradleTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testSimpleProject() throws Exception {
    File out = temp.newFile();
    File projectBaseDir = new File(this.getClass().getResource("/java-gradle-simple").toURI());
    ProcessBuilder pb = new ProcessBuilder("sh", "gradlew", "sonarRunner", "-DsonarRunner.dumpToFile=" + out.getAbsolutePath())
      .directory(projectBaseDir)
      .inheritIO();
    Process p = pb.start();
    p.waitFor();

    Properties props = new Properties();
    try (FileReader fr = new FileReader(out)) {
      props.load(fr);
    }

    assertThat(props).contains(
      entry("sonar.projectKey", "org.codehaus.sonar:example-java-gradle"));
    assertThat(props.get("sonar.sources").toString()).contains("src/main/java");
    assertThat(props.get("sonar.tests").toString()).contains("src/test/java");
  }
}
