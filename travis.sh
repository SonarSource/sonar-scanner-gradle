#!/bin/bash

set -euo pipefail

function strongEcho {
  echo ""
  echo "================ $1 ================="
}

function configureTravis {
  mkdir -p ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v28 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}
configureTravis

#build_snapshot SonarSource/sonar-scanner-api

if [ "$TRAVIS_BRANCH" == "master" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
  strongEcho 'Build analyze and deploy commit in master'
  # this commit is master must be built and analyzed (with upload of report)

  # Analyze with SNAPSHOT version as long as SQ does not correctly handle
  # purge of release data
  CURRENT_VERSION=`cat gradle.properties | grep version | awk -F= '{print $2}'`
  # Do not deploy a SNAPSHOT version but the release version related to this build
  sed -i.bak "s/-SNAPSHOT/-build$TRAVIS_BUILD_NUMBER/g" gradle.properties    
  # set the build name with travis build number
  echo buildInfo.build.name=sonar-scanner-gradle >> gradle.properties 
  echo buildInfo.build.number=$TRAVIS_BUILD_NUMBER >> gradle.properties 

  ./gradlew build check sonarqube artifactory \
      -Dsonar.projectVersion=$CURRENT_VERSION \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_TOKEN

elif [[ "${TRAVIS_BRANCH}" == "branch-"* ]] && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
  # no dory analysis on release branch
  if [[ $CURRENT_VERSION =~ "-SNAPSHOT" ]]; then
    echo "======= Found SNAPSHOT version ======="
    # Do not deploy a SNAPSHOT version but the release version related to this build
    # Do not deploy a SNAPSHOT version but the release version related to this build
    sed -i.bak "s/-SNAPSHOT/-build$TRAVIS_BUILD_NUMBER/g" gradle.properties    
    # set the build name with travis build number
    echo buildInfo.build.name=sonar-scanner-gradle >> gradle.properties 
    echo buildInfo.build.number=$TRAVIS_BUILD_NUMBER >> gradle.properties 
  else
    echo "======= Found RELEASE version ======="
  fi     
  #build and deploy
  ./gradlew build artifactory 

elif [ "$TRAVIS_PULL_REQUEST" != "false" ] && [ "$TRAVIS_SECURE_ENV_VARS" == "true" ]; then
  strongEcho 'Build and analyze pull request'  

  if [ "${DEPLOY_PULL_REQUEST:-}" == "true" ]; then
    echo '======= with deploy'

    # Analyze with SNAPSHOT version as long as SQ does not correctly handle
    # purge of release data
    CURRENT_VERSION=`cat gradle.properties | grep version | awk -F= '{print $2}'`
    # Do not deploy a SNAPSHOT version but the release version related to this build
    sed -i.bak "s/-SNAPSHOT/-build$TRAVIS_BUILD_NUMBER/g" gradle.properties    
    # set the build name with travis build number
    echo buildInfo.build.name=sonar-scanner-gradle >> gradle.properties 
    echo buildInfo.build.number=$TRAVIS_BUILD_NUMBER >> gradle.properties 
    
    ./gradlew build check sonarqube artifactory \
      -Dsonar.analysis.mode=issues \
      -Dsonar.github.pullRequest=$TRAVIS_PULL_REQUEST \
      -Dsonar.github.repository=$TRAVIS_REPO_SLUG \
      -Dsonar.github.oauth=$GITHUB_TOKEN \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_TOKEN

  else
    echo '======= no deploy'                                                                                                                            

    ./gradlew build check sonarqube \
      -Dsonar.analysis.mode=issues \
      -Dsonar.github.pullRequest=$TRAVIS_PULL_REQUEST \
      -Dsonar.github.repository=$TRAVIS_REPO_SLUG \
      -Dsonar.github.oauth=$GITHUB_TOKEN \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_TOKEN
  fi
else
  strongEcho 'Build, no analysis'
  # Build branch, without any analysis

  ./gradlew build check
fi

