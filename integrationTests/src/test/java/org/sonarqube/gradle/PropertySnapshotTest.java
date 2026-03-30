/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 */
package org.sonarqube.gradle;

import java.nio.file.Path;
import java.util.Map;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.sonarqube.gradle.support.AbstractGradleIT;
import org.sonarqube.gradle.support.snapshot.SnapshotCase;
import org.sonarqube.gradle.support.snapshot.SnapshotCases;
import org.sonarqube.gradle.support.snapshot.SnapshotComparisonPolicy;
import org.sonarqube.gradle.support.snapshot.SnapshotPaths;
import org.sonarqube.gradle.support.snapshot.SnapshotRepository;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class PropertySnapshotTest extends AbstractGradleIT {
  private static final SnapshotRepository SNAPSHOTS = new SnapshotRepository(SnapshotPaths.repositoryRoot());
  private static final SnapshotComparisonPolicy POLICY = new SnapshotComparisonPolicy();

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> parameters() {
    return SnapshotCases.parameters();
  }

  private final SnapshotCase snapshotCase;

  public PropertySnapshotTest(SnapshotCase snapshotCase) {
    this.snapshotCase = snapshotCase;
  }

  @Test
  public void verifyExistingPropertySnapshots() throws Exception {
    Assume.assumeTrue("Snapshot case should run: " + snapshotCase.name(), snapshotCase.shouldRun());
    Map<String, String> actual = snapshotCase.collect(this, POLICY);
    Path file = SNAPSHOTS.file(snapshotCase.name());
    assertThat(file).as("expected snapshot file for %s", snapshotCase.name()).exists();
    Map<String, String> expected = snapshotCase.expected(SNAPSHOTS.load(file), actual, POLICY);
    assertThat(actual).as(snapshotCase.name()).containsAllEntriesOf(expected);
  }

  @Ignore("Run locally to regenerate all integration test property snapshots.")
  @Test
  public void rewritePropertySnapshot() throws Exception {
    SNAPSHOTS.write(snapshotCase.name(), snapshotCase.collect(this, POLICY));
  }
}
