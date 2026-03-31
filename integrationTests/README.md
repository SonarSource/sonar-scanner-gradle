## Integration Tests

### Add a Project

Add the Gradle project under `integrationTests/src/test/resources`.

During CI executions, Maven filters some files in `src/test/resources` and replaces:
- `${gradle.version}`
- `${androidGradle.version}`
- `${version}`

On a local machine, publish the plugin to your local Maven cache before running the integration tests. Local runs do not rely on Repox.

Parameterize at least:
- the Gradle version, usually through `gradle-wrapper.properties`
- the Android Gradle Plugin version, if the project is Android-based
- the scanner plugin version

To make plugin resolution work in test projects, reuse the setup from `integrationTests/src/test/resources/gradle-9-example/settings.gradle.kts`.

### Property Snapshots

`PropertySnapshotTest` verifies the scanner properties produced for a set of sample projects.

The test:
- runs the sample project in simulation mode
- extracts the comparable Sonar properties
- normalizes environment-dependent values
- compares the actual result with a stored JSON snapshot

Snapshots are stored in:
- `integrationTests/src/test/resources/Snapshots`

### Regenerate Snapshots

`PropertySnapshotTest.rewritePropertySnapshot()` is intentionally disabled with `@Ignore`.

To regenerate snapshots:
1. Open `integrationTests/src/test/java/org/sonarqube/gradle/PropertySnapshotTest.java`
2. Comment out or remove the `@Ignore` annotation on `rewritePropertySnapshot()`
3. Run `rewritePropertySnapshot`
4. Restore the `@Ignore` annotation after regeneration

Each parameterized case rewrites its own JSON file in `integrationTests/src/test/resources/Snapshots`.

Snapshot writing sorts keys alphabetically before serialization so regenerating the same snapshot twice produces stable JSON ordering.

### Add a Snapshot Case

To add a new project to `PropertySnapshotTest`:
1. Add the sample project under `integrationTests/src/test/resources`
2. Add a new case in `integrationTests/src/test/java/org/sonarqube/gradle/snapshot/SnapshotCases.java`
3. Add constraints when needed:
- `minGradle(...)`
- `maxGradleExclusive(...)`
- `gradleRange(...)`
- `requiresAndroid()`
- `minAndroidGradle(...)`
- `subdir(...)`
- `ignoreProperty(...)`
4. Regenerate snapshots to create the new JSON file
5. Review the generated snapshot before committing it

Use `ignoreProperty(...)` only for values that are genuinely unstable and cannot be normalized safely.
