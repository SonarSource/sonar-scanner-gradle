#!/bin/bash

set -euo pipefail

function strongEcho {
  echo ""
  echo "================ $1 ================="
}

function configureTravis {
  mkdir -p ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v33 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}

function prepareBuildVersion {
    # Analyze with SNAPSHOT version as long as SQ does not correctly handle purge of release data
    CURRENT_VERSION=`cat gradle.properties | grep version | awk -F= '{print $2}'`
    RELEASE_VERSION=`echo $CURRENT_VERSION | sed "s/-.*//g"`
    # In case of 2 digits, we need to add the 3rd digit (0 obviously)
    # Mandatory in order to compare versions (patch VS non patch)
    IFS=$'.'
    DIGIT_COUNT=`echo $RELEASE_VERSION | wc -w`
    unset IFS
    if [ $DIGIT_COUNT -lt 3 ]; then
        RELEASE_VERSION="$RELEASE_VERSION.0"
    fi
    NEW_VERSION="$RELEASE_VERSION.$TRAVIS_BUILD_NUMBER"
    export PROJECT_VERSION=$NEW_VERSION

    # Deply the release version related to this build instead of snapshot
    sed -i.bak "s/$CURRENT_VERSION/$NEW_VERSION/g" gradle.properties
    # set the build name with travis build number
    echo buildInfo.build.name=sonar-scanner-gradle >> gradle.properties
    echo buildInfo.build.number=$TRAVIS_BUILD_NUMBER >> gradle.properties
}


if [ "$TRAVIS_BRANCH" == "master" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
  strongEcho 'Build and analyze commit in master and publish in artifactory'
  # this commit is master must be built and analyzed (with upload of report)

  prepareBuildVersion
  ./gradlew build check sonarqube artifactory \
      -Dsonar.projectVersion=$CURRENT_VERSION \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_TOKEN

elif [[ "${TRAVIS_BRANCH}" == "branch-"* ]] && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
  strongEcho 'Build and publish in artifactory'
  prepareBuildVersion

  #build and deploy - no dory analysis on release branch
  ./gradlew build check artifactory 

elif [ "$TRAVIS_PULL_REQUEST" != "false" ] && [ "$TRAVIS_SECURE_ENV_VARS" == "true" ]; then
  strongEcho 'Build and analyze pull request'  

  if [ "${DEPLOY_PULL_REQUEST:-}" == "true" ]; then
    echo '======= with deploy'

    prepareBuildVersion
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

