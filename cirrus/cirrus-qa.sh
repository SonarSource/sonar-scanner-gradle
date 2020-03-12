#!/bin/bash

source cirrus-env QA

echo "ANDROID_HOME="$ANDROID_HOME

cd integrationTests

mkdir -p $ANDROID_HOME/licenses
cp -f licenses/* $ANDROID_HOME/licenses

mvn -e -B clean verify -Dgradle.version=$GRADLE_VERSION




