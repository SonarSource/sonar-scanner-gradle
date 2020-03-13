#!/bin/bash

#set -euo pipefail

BUILD_ID=$1

VERSION=$(mvn -q \
  -Dexec.executable="echo" \
  -Dexec.args='${project.version}' \
  --non-recursive \
  org.codehaus.mojo:exec-maven-plugin:1.6.0:exec)

RELEASE_VERSION=${VERSION%"-SNAPSHOT"}

# In case of 2 digits, we need to add the 3rd digit (0 obviously)
# Mandatory in order to compare versions (patch VS non patch)
IFS=$'.'
DIGIT_COUNT=`echo $RELEASE_VERSION | wc -w`
unset IFS
if [ $DIGIT_COUNT -lt 3 ]; then
    RELEASE_VERSION="$RELEASE_VERSION.0"
fi
NEW_VERSION="$RELEASE_VERSION.$BUILD_ID"

echo "Replacing version $CURRENT_VERSION with $NEW_VERSION"

mvn org.codehaus.mojo:versions-maven-plugin:2.7:set -DnewVersion=$NEW_VERSION -DgenerateBackupPoms=false -B -e

export PROJECT_VERSION=$NEW_VERSION
