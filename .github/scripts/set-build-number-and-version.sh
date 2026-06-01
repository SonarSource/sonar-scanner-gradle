#!/usr/bin/env bash

export BUILD_NUMBER="${VERSION##*.}"
if ! [[ "$BUILD_NUMBER" =~ ^[0-9]+$ ]]; then
  echo "Invalid BUILD_NUMBER: $BUILD_NUMBER. It must be a numeric value."
  exit 1
fi
sed -E -i.bak "s/^version=.*/version=${VERSION}/g" gradle.properties
