#!/bin/bash

echo "*** QA ***"

source cirrus-env QA

#echo "ANDROID_HOME="$ANDROID_HOME

cd integrationTests

# Make sure ITs are using the same version as the plugin
source set_maven_build_version $BUILD_NUMBER

#mkdir -p $ANDROID_HOME/licenses
#cp -f licenses/* $ANDROID_HOME/licenses

mvn -e -B clean verify -Dgradle.version=$GRADLE_VERSION




