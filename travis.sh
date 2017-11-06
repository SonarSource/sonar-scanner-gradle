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
  strongEcho 'Build, deploy and analyze master'
  # this commit is master must be built and analyzed (with upload of report)

  git fetch --unshallow || true

  prepareBuildVersion
  ./gradlew build check sonarqube artifactory \
      -Dsonar.projectVersion=$CURRENT_VERSION \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_TOKEN \
      -Dsonar.analysis.buildNumber=$TRAVIS_BUILD_NUMBER \
      -Dsonar.analysis.pipeline=$TRAVIS_BUILD_NUMBER \
      -Dsonar.analysis.sha1=$TRAVIS_COMMIT  \
      -Dsonar.analysis.repository=$TRAVIS_REPO_SLUG


elif [ "$TRAVIS_PULL_REQUEST" != "false" ] && [ "$TRAVIS_SECURE_ENV_VARS" == "true" ]; then
  strongEcho 'Build and analyze pull request with deploy'

    prepareBuildVersion
    ./gradlew build check sonarqube artifactory \
      -Dsonar.analysis.mode=issues \
      -Dsonar.github.pullRequest=$TRAVIS_PULL_REQUEST \
      -Dsonar.github.repository=$TRAVIS_REPO_SLUG \
      -Dsonar.github.oauth=$GITHUB_TOKEN \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_TOKEN

    ./gradlew build check sonarqube \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_TOKEN \
      -Dsonar.analysis.buildNumber=$TRAVIS_BUILD_NUMBER \
      -Dsonar.analysis.pipeline=$TRAVIS_BUILD_NUMBER \
      -Dsonar.analysis.sha1=$TRAVIS_PULL_REQUEST_SHA  \
      -Dsonar.analysis.repository=$TRAVIS_REPO_SLUG \
      -Dsonar.analysis.prNumber=$TRAVIS_PULL_REQUEST \
      -Dsonar.branch.name=$TRAVIS_PULL_REQUEST_BRANCH \
      -Dsonar.branch.target=$TRAVIS_BRANCH

else
  strongEcho 'Build, no analysis'
  # Build branch, without any analysis

  ./gradlew build check
fi

