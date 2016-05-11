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

  elif [ "$TRAVIS_BRANCH" == "feature/cix" ]; then
    strongEcho 'feature/cix: build analyse deploy on repox'
    
    # Analyze with SNAPSHOT version as long as SQ does not correctly handle
    # purge of release data
    CURRENT_VERSION=`cat gradle.properties | grep version | awk -F= '{print $2}'`
    # Do not deploy a SNAPSHOT version but the release version related to this build
    sed -i.bak "s/-SNAPSHOT//g" gradle.properties    
    # set the build name with travis build number
    echo buildInfo.build.name=$TRAVIS_REPO_SLU >> gradle.properties 
    echo buildInfo.build.number=$TRAVIS_BUILD_NUMBER >> gradle.properties 


    ./gradlew artifactory check sonarqube \
        -Dsonar.projectVersion=$CURRENT_VERSION \
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
