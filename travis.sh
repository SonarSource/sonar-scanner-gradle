#!/bin/bash

set -euo pipefail

function strongEcho {
  echo ""
  echo "================ $1 ================="
}

case "$TARGET" in

CI)
  if [ "${TRAVIS_BRANCH}" == "master" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
    strongEcho 'Build and analyze commit in master'
    # this commit is master must be built and analyzed (with upload of report)

    ./gradlew sonarqube \
        -Dsonar.host.url=$SONAR_HOST_URL \
        -Dsonar.login=$SONAR_TOKEN

  elif [ "$TRAVIS_PULL_REQUEST" != "false" ] && [ -n "$GITHUB_TOKEN" ]; then
    # For security reasons environment variables are not available on the pull requests
    # coming from outside repositories
    # http://docs.travis-ci.com/user/pull-requests/#Security-Restrictions-when-testing-Pull-Requests                                                                         
    # That's why the analysis does not need to be executed if the variable SONAR_GITHUB_OAUTH is not defined.                                                                
                                                                                                                                                                             
    strongEcho 'Build and analyze pull request'                                                                                                                              

    ./gradlew sonarqube \
        -Dsonar.analysis.mode=issues \
        -Dsonar.github.pullRequest=$TRAVIS_PULL_REQUEST \
        -Dsonar.github.repository=$TRAVIS_REPO_SLUG \
        -Dsonar.github.oauth=$GITHUB_TOKEN \
        -Dsonar.host.url=$SONAR_HOST_URL \
        -Dsonar.login=$SONAR_TOKEN

  else
    strongEcho 'Build, no analysis'
    # Build branch, without any analysis

    ./gradlew build install 
  fi
  ;;

IT)
  ./gradlew build install

  cd integrationTests
  mvn clean verify -Dgradle.version=$GRADLE_VERSION
  ;;

*)
  echo "Unexpected TARGET value: $TARGET"
  exit 1
  ;;

esac
