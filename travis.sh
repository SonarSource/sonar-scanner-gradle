#!/bin/bash

set -euo pipefail

if [ -n "${PR_ANALYSIS:-}" ] && [ "${PR_ANALYSIS}" == true ]
then
  if [ "$TRAVIS_PULL_REQUEST" != "false" ]
  then
    # PR analysis
    ./gradlew sonarqube \
      -Dsonar.analysis.mode=issues \
      -Dsonar.github.pullRequest=$TRAVIS_PULL_REQUEST \
      -Dsonar.github.repository=$TRAVIS_REPO_SLUG \
      -Dsonar.github.login=$SONAR_GITHUB_LOGIN \
      -Dsonar.github.oauth=$SONAR_GITHUB_OAUTH \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_LOGIN \
      -Dsonar.password=$SONAR_PASSWD  
  fi
else
  # Regular CI
  ./gradlew build install
fi

# ITs
if [ -n "${RUN_ITS:-}" ] && [ "${RUN_ITS}" == true ]
then
  cd integrationTests
  mvn clean verify -Dgradle.version=$GRADLE_VERSION
fi


