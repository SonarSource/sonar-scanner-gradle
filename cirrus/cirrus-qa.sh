#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

source cirrus-env QA

# Set ITs to have the same version as the plugin
source "${SCRIPT_DIR}/set-version-from-build-number.sh"


# We need to build this small plugin first, that will dump the analysis properties in a local file
mvn -f property-dump-plugin/pom.xml --batch-mode install

cd integrationTests

mvn --errors --batch-mode --no-transfer-progress org.codehaus.mojo:versions-maven-plugin:2.7:set -DnewVersion=$NEW_VERSION -DgenerateBackupPoms=false

# Execute ITs
if [ -v ANDROID_GRADLE_VERSION ]; then
  mvn --errors --batch-mode --no-transfer-progress clean verify -Dgradle.version=$GRADLE_VERSION -DandroidGradle.version=$ANDROID_GRADLE_VERSION
else
  mvn --errors --batch-mode --no-transfer-progress clean verify -Dgradle.version=$GRADLE_VERSION -DandroidGradle.version=NOT_AVAILABLE
fi




