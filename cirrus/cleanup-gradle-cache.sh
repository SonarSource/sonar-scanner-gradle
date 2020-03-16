#!/bin/bash

rm -rf ~/.gradle/caches/$GRADLE_VERSION/
rm -rf ~/.gradle/daemon/
find ~/.gradle/caches/ -name "*.lock" -type f -delete
