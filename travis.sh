#!/bin/bash

set -euo pipefail

function strongEcho {
  echo ""
  echo "================ $1 ================="
}

function configureTravis {
  mkdir -p ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v27 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}
configureTravis

#build_snapshot SonarSource/sonar-scanner-api

case "$TARGET" in

CI)
  if [ "$TRAVIS_BRANCH" == "master" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
    strongEcho 'Build and analyze commit in master'
    # this commit is master must be built and analyzed (with upload of report)

    ./gradlew build check sonarqube \
        -Dsonar.host.url=$SONAR_HOST_URL \
        -Dsonar.login=$SONAR_TOKEN

  elif [ "$TRAVIS_PULL_REQUEST" != "false" ] && [ "$TRAVIS_SECURE_ENV_VARS" == "true" ]; then
    strongEcho 'Build and analyze pull request'                                                                                                                              

    ./gradlew build check sonarqube \
        -Dsonar.analysis.mode=issues \
        -Dsonar.github.pullRequest=$TRAVIS_PULL_REQUEST \
        -Dsonar.github.repository=$TRAVIS_REPO_SLUG \
        -Dsonar.github.oauth=$GITHUB_TOKEN \
        -Dsonar.host.url=$SONAR_HOST_URL \
        -Dsonar.login=$SONAR_TOKEN

  else
    strongEcho 'Build, no analysis'
    # Build branch, without any analysis

    ./gradlew build check
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
