/*
 * Gradle Plugin :: Integration Tests
 * Copyright (C) 2015-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarqube.gradle;

import java.nio.file.Path;
import java.util.Map;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.sonarqube.gradle.snapshot.SnapshotCases;
import org.sonarqube.gradle.snapshot.SnapshotIO;
import org.sonarqube.gradle.support.AbstractGradleIT;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class PropertySnapshotTest extends AbstractGradleIT {

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> parameters() {
    return SnapshotCases.parameters();
  }

  private final SnapshotCases.Case snapshotCase;

  public PropertySnapshotTest(SnapshotCases.Case snapshotCase) {
    this.snapshotCase = snapshotCase;
  }

  @Test
  public void verifyExistingPropertySnapshots() throws Exception {
    Assume.assumeTrue("Snapshot case should run: " + snapshotCase.name(), snapshotCase.shouldRun());
    Map<String, String> actual = snapshotCase.collect(this);
    Path file = SnapshotIO.file(snapshotCase.name());
    assertThat(file).as("expected snapshot file for %s", snapshotCase.name()).exists();
    Map<String, String> expected = SnapshotIO.load(file);
    assertThat(actual).as(snapshotCase.name()).containsAllEntriesOf(expected);
    assertThat(expected).as(snapshotCase.name() + " (no unexpected extra properties)").containsAllEntriesOf(actual);
  }

  @Ignore("Run locally to regenerate all integration test property snapshots.")
  @Test
  public void rewritePropertySnapshot() throws Exception {
    var actual = snapshotCase.collectWithVersionsOverride(this);
    SnapshotIO.write(snapshotCase.name(), actual);
  }
}
