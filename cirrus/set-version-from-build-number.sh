#!/usr/bin/env bash

CURRENT_VERSION=`cat gradle.properties | grep version | awk -F= '{print $2}'`
RELEASE_VERSION=`echo $CURRENT_VERSION | sed "s/-.*//g"`
number_dots=`echo $RELEASE_VERSION | grep -o "\." | wc -l`
if [ $number_dots -lt 2 ]; then
  NEW_VERSION="$RELEASE_VERSION.0.$BUILD_NUMBER"
else
  NEW_VERSION="$RELEASE_VERSION.$BUILD_NUMBER"
fi
sed -i.bak "s/$CURRENT_VERSION/$NEW_VERSION/g" gradle.properties
