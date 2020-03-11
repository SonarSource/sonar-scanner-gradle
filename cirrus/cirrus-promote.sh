#!/bin/bash

. ./cirrus/cirrus-env.sh PROMOTE

if [ "$PULL_REQUEST" != "" ]; then
  PRIVATE_TARGET_REPO='sonarsource-private-dev'
  PUBLIC_TARGET_REPO='sonarsource-public-dev'
  STATUS='it-passed-pr'
else
  if [[ "$GITHUB_BRANCH" == "master" ]] || [[ "$GITHUB_BRANCH" == "branch-"* ]]; then
    PRIVATE_TARGET_REPO='sonarsource-private-builds'
    PUBLIC_TARGET_REPO='sonarsource-public-builds'
    STATUS='it-passed'
  fi
  if [[ "$GITHUB_BRANCH" == "dogfood-on-"* ]]; then
    PRIVATE_TARGET_REPO='sonarsource-dogfood-builds'
    PUBLIC_TARGET_REPO='sonarsource-dogfood-builds'
    STATUS='it-passed'
  fi
fi

if [ -n "${STATUS:-}" ]; then
  echo "Promoting build $CIRRUS_REPO_NAME#$BUILD_NUMBER"
  HTTP_CODE=$(curl -s -o /dev/null -w %{http_code} -H "X-JFrog-Art-Api:${ARTIFACTORY_API_KEY}" "$ARTIFACTORY_URL/api/plugins/execute/multiRepoPromote?params=buildName=$CIRRUS_REPO_NAME;buildNumber=$BUILD_NUMBER;src1=$ARTIFACTORY_DEPLOY_REPO_PRIVATE;target1=$PRIVATE_TARGET_REPO;src2=$ARTIFACTORY_DEPLOY_REPO;target2=$PUBLIC_TARGET_REPO;status=$STATUS")
  if [ "$HTTP_CODE" != "200" ]; then
    echo "Cannot promote build $CIRRUS_REPO_NAME#$BUILD_NUMBER: ($HTTP_CODE)"
    exit 1
  else
    echo "Build ${CIRRUS_REPO_NAME}#${BUILD_NUMBER} promoted to ${PRIVATE_TARGET_REPO} and ${PUBLIC_TARGET_REPO}"
    ./cirrus/burgr-notify-promotion.sh
  fi
else
  echo 'No promotion for builds coming from a development branch'
fi
