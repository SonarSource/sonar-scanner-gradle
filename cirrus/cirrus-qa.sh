#!/bin/bash

source cirrus-env QA

# Set ITs to have the same version as the plugin
CURRENT_VERSION=`cat gradle.properties | grep version | awk -F= '{print $2}'`
RELEASE_VERSION=`echo $CURRENT_VERSION | sed "s/-.*//g"`
number_dots=`echo $RELEASE_VERSION | grep -o "\." | wc -l`
if [ $number_dots -lt 2 ]; then
  NEW_VERSION="$RELEASE_VERSION.0.$BUILD_NUMBER"
else
  NEW_VERSION="$RELEASE_VERSION.$BUILD_NUMBER"
fi
sed -i.bak "s/$CURRENT_VERSION/$NEW_VERSION/g" gradle.properties

# We need to build this small plugin first, that will dump the analysis properties in a local file
mvn -f property-dump-plugin/pom.xml --batch-mode install

cd integrationTests

mvn org.codehaus.mojo:versions-maven-plugin:2.7:set -DnewVersion=$NEW_VERSION -DgenerateBackupPoms=false -B -e

# Execute ITs
if [ -v ANDROID_GRADLE_VERSION ]; then
  mvn --errors --batch-mode clean verify -Dgradle.version=$GRADLE_VERSION -DandroidGradle.version=$ANDROID_GRADLE_VERSION
else
  mvn --errors --batch-mode clean verify -Dgradle.version=$GRADLE_VERSION -DandroidGradle.version=NOT_AVAILABLE
fi




