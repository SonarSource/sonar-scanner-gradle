#!/bin/bash

#set -euo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

BUILD_ID=2000
CURRENT_VERSION=`$DIR/maven_expression "project.version"`
RELEASE_VERSION=${CURRENT_VERSION%"-SNAPSHOT"}

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
