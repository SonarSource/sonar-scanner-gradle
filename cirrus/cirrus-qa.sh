#!/bin/bash

echo "*** BFFORE cirrus-env ***"

source cirrus-env QA

echo "*** BEFORE change build number ***"

# Make sure ITs are using the same version as the plugin
source set_maven_build_version $BUILD_NUMBER



echo "*** BEFORE go to IT ***"

#echo "ANDROID_HOME="$ANDROID_HOME

cd integrationTests



echo "*** BEFORE start execution of IT ***"

#mkdir -p $ANDROID_HOME/licenses
#cp -f licenses/* $ANDROID_HOME/licenses

mvn -e -B clean verify -Dgradle.version=$GRADLE_VERSION




