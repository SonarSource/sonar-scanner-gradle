#!/usr/bin/env bash

echo "Release version: ${CIRRUS_TAG}"
export CI_BUILD_NUMBER="${CIRRUS_TAG##*.}"
source cirrus-env RELEASE
source cirrus/set-version-from-build-number.sh
cat gradle.properties | grep -E "^version"
