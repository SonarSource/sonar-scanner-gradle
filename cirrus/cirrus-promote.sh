#!/bin/bash

. ./cirrus/cirrus-env.sh PROMOTE

echo "Promoting build $CIRRUS_REPO_NAME#$BUILD_NUMBER"
HTTP_CODE=$(curl -s -o /dev/null -w %{http_code} -sfSL -H "Authorization: Bearer $GCF_ACCESS_TOKEN" "$PROMOTE_URL/$GITHUB_REPO/$GITHUB_BRANCH/$BUILD_NUMBER/$PULL_REQUEST"

if [ "$HTTP_CODE" != "200" ]; then
  echo "Cannot promote build $CIRRUS_REPO_NAME#$BUILD_NUMBER: ($HTTP_CODE)"
  exit 1
else
  echo "Build ${CIRRUS_REPO_NAME}#${BUILD_NUMBER} promoted"
  ./cirrus/burgr-notify-promotion.sh
fi
