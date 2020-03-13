#!/bin/bash

echo "*** BEFORE cirrus-env ***"

#source cirrus-env QA

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

echo "*** 2 ***"

NEW_VERSION="${VERSION%"-SNAPSHOT"}.$BUILD_ID"

echo "Replacing version $CURRENT_VERSION with $NEW_VERSION"

mvn org.codehaus.mojo:versions-maven-plugin:2.7:set -DnewVersion=$NEW_VERSION -DgenerateBackupPoms=false -B -e

export PROJECT_VERSION=$NEW_VERSION




echo "*** BEFORE start execution of IT ***"

#mkdir -p $ANDROID_HOME/licenses
#cp -f licenses/* $ANDROID_HOME/licenses

mvn -e -B clean verify -Dgradle.version=$GRADLE_VERSION




