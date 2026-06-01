#!/usr/bin/env bash

if ! [[ "$BUILD_NUMBER" =~ ^[0-9]+$ ]]; then
  echo "Invalid BUILD_NUMBER: $BUILD_NUMBER. It must be a numeric value."
  exit 1
fi

CURRENT_VERSION="$(cat gradle.properties | grep -E '^version=' | awk -F= '{print $2}')"
RELEASE_VERSION="$(echo $CURRENT_VERSION | sed -E 's/-.*$//g')"
number_dots=`echo $RELEASE_VERSION | grep -o "\." | wc -l`
if [ $number_dots -lt 2 ]; then
  NEW_VERSION="$RELEASE_VERSION.0.$BUILD_NUMBER"
else
  NEW_VERSION="$RELEASE_VERSION.$BUILD_NUMBER"
fi
sed -E -i.bak "s/^version=.*/version=${NEW_VERSION}/g" gradle.properties
