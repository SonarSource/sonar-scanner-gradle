#!/bin/bash

echo "*** BEFORE cirrus-env ***"

source cirrus-env QA

#echo "ANDROID_HOME="$ANDROID_HOME

cd integrationTests

echo "*** BEFORE set_maven_build_version ***"

# Make sure ITs are using the same version as the plugin
#. ./../cirrus/set_maven_build_version.sh $BUILD_NUMBER

BUILD_ID=$BUILD_NUMBER

echo "*** 1 ***"

VERSION=$(mvn -q \
  -Dexec.executable="echo" \
  -Dexec.args='${project.version}' \
  --non-recursive \
  org.codehaus.mojo:exec-maven-plugin:1.6.0:exec)

RELEASE_VERSION=${VERSION%"-SNAPSHOT"}

echo "*** 2 ***"

# In case of 2 digits, we need to add the 3rd digit (0 obviously)
# Mandatory in order to compare versions (patch VS non patch)
IFS=$'.'
DIGIT_COUNT=`echo $RELEASE_VERSION | wc -w`
unset IFS

echo "*** 3 ***"

if [ $DIGIT_COUNT -lt 3 ]; then
    RELEASE_VERSION="$RELEASE_VERSION.0"
fi
NEW_VERSION="$RELEASE_VERSION.$BUILD_ID"

echo "Replacing version $CURRENT_VERSION with $NEW_VERSION"

mvn org.codehaus.mojo:versions-maven-plugin:2.7:set -DnewVersion=$NEW_VERSION -DgenerateBackupPoms=false -B -e

export PROJECT_VERSION=$NEW_VERSION




echo "*** BEFORE start execution of IT ***"

#mkdir -p $ANDROID_HOME/licenses
#cp -f licenses/* $ANDROID_HOME/licenses

mvn -e -B clean verify -Dgradle.version=$GRADLE_VERSION




