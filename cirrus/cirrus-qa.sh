#!/bin/bash

echo "*** BEFORE cirrus-env ***"

source cirrus-env QA

#echo "ANDROID_HOME="$ANDROID_HOME

echo "*** BEFORE set_maven_build_version ***"

CURRENT_VERSION=`cat gradle.properties | grep version | awk -F= '{print $2}'`
RELEASE_VERSION=`echo $CURRENT_VERSION | sed "s/-.*//g"`
number_dots=`echo $RELEASE_VERSION | grep -o "\." | wc -l`
if [ $number_dots -lt 2 ]; then
  NEW_VERSION="$RELEASE_VERSION.0.$BUILD_NUMBER"
else
  NEW_VERSION="$RELEASE_VERSION.$BUILD_NUMBER"
fi
sed -i.bak "s/$CURRENT_VERSION/$NEW_VERSION/g" gradle.properties

cd integrationTests

mvn org.codehaus.mojo:versions-maven-plugin:2.7:set -DnewVersion=$NEW_VERSION -DgenerateBackupPoms=false -B -e

#source set_maven_build_version $BUILD_NUMBER

echo "*** BEFORE start execution of IT ***"

#mkdir -p $ANDROID_HOME/licenses
#cp -f licenses/* $ANDROID_HOME/licenses

mvn -e -B clean verify -Dgradle.version=$GRADLE_VERSION




