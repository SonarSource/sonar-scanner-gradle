#/bin/sh
# Requires the environment variables:
# - SONAR_HOST_URL: URL of SonarQube server
# - SONAR_TOKEN: access token to send analysis reports to $SONAR_HOST_URL
# - GITHUB_TOKEN: access token to send analysis of pull requests to GibHub
# - ARTIFACTORY_URL: URL to Artifactory repository
# - ARTIFACTORY_DEPLOY_REPO: name of deployment repository
# - ARTIFACTORY_DEPLOY_USERNAME: login to deploy to $ARTIFACTORY_DEPLOY_REPO
# - ARTIFACTORY_DEPLOY_PASSWORD: password to deploy to $ARTIFACTORY_DEPLOY_REPO

set -euo pipefail

# Used by Next
export INITIAL_VERSION=$(cat gradle.properties | grep version | awk -F= '{print $2}')


# Fetch all commit history so that SonarQube has exact blame information
# for issue auto-assignment
# This command can fail with "fatal: --unshallow on a complete repository does not make sense"
# if there are not enough commits in the Git repository
# For this reason errors are ignored with "|| true"
git fetch --unshallow || true

# fetch references from github for PR analysis
if [ ! -z "${GITHUB_BASE_BRANCH}" ]; then
	git fetch origin ${GITHUB_BASE_BRANCH}
fi

if [ "${GITHUB_BRANCH}" == "master" ] && [ "$PULL_REQUEST" == "false" ]; then
  echo '======= Build, deploy and analyze master'

  ./gradlew --no-daemon --console plain \
    -DbuildNumber=$BUILD_NUMBER \
    build sonarqube artifactoryPublish \
    -Dsonar.host.url=$SONAR_HOST_URL \
    -Dsonar.login=$SONAR_TOKEN \
    -Dsonar.projectVersion=$INITIAL_VERSION \
    -Dsonar.analysis.buildNumber=$BUILD_NUMBER \
    -Dsonar.analysis.pipeline=$PIPELINE_ID \
    -Dsonar.analysis.sha1=$GIT_SHA1 \
    -Dsonar.analysis.repository=$GITHUB_REPO \
    $*

elif [[ "${GITHUB_BRANCH}" == "branch-"* ]] && [ "$PULL_REQUEST" == "false" ]; then
  # analyze maintenance branches as long-living branches
  echo '======= Build, deploy and analyze maintenance branch'

  ./gradlew --no-daemon --console plain \
    -DbuildNumber=$BUILD_NUMBER \
    build sonarqube artifactoryPublish \
    -Dsonar.host.url=$SONAR_HOST_URL \
    -Dsonar.login=$SONAR_TOKEN \
    -Dsonar.branch.name=$GITHUB_BRANCH \
    -Dsonar.projectVersion=$INITIAL_VERSION \
    -Dsonar.analysis.buildNumber=$BUILD_NUMBER \
    -Dsonar.analysis.pipeline=$PIPELINE_ID \
    -Dsonar.analysis.sha1=$GIT_SHA1 \
    -Dsonar.analysis.repository=$GITHUB_REPO \
    $*


elif [ "$PULL_REQUEST" != "false" ] && [ -n "${GITHUB_TOKEN:-}" ]; then
  echo '======= Build and analyze pull request'

  ./gradlew --no-daemon --console plain \
    -DbuildNumber=$BUILD_NUMBER \
    build sonarqube artifactoryPublish \
    -Dsonar.host.url=$SONAR_HOST_URL \
    -Dsonar.login=$SONAR_TOKEN \
    -Dsonar.analysis.buildNumber=$BUILD_NUMBER \
    -Dsonar.analysis.pipeline=$PIPELINE_ID \
    -Dsonar.analysis.sha1=$PULL_REQUEST_SHA  \
    -Dsonar.analysis.repository=$GITHUB_REPO \
    -Dsonar.analysis.prNumber=$PULL_REQUEST \
    -Dsonar.pullrequest.branch=$GITHUB_BRANCH \
    -Dsonar.pullrequest.base=$GITHUB_BASE_BRANCH \
    -Dsonar.pullrequest.key=$PULL_REQUEST \
    $*

elif [[ "$GITHUB_BRANCH" == "dogfood-on-"* ]] && [ "$PULL_REQUEST" == "false" ]; then
  echo '======= Build and deploy dogfood branch'

  ./gradlew --no-daemon --console plain build artifactoryPublish -DbuildNumber=$BUILD_NUMBER $*

elif [[ "$GITHUB_BRANCH" == "feature/long/"* ]] && [ "$PULL_REQUEST" == "false" ]; then
  echo '======= Build and analyze long lived feature branch'

  ./gradlew --no-daemon --console plain \
    -DbuildNumber=$BUILD_NUMBER \
    build sonarqube \
    -Dsonar.host.url=$SONAR_HOST_URL \
    -Dsonar.login=$SONAR_TOKEN \
    -Dsonar.branch.name=$GITHUB_BRANCH \
    -Dsonar.analysis.buildNumber=$BUILD_NUMBER \
    -Dsonar.analysis.pipeline=$PIPELINE_ID \
    -Dsonar.analysis.sha1=$GIT_SHA1  \
    -Dsonar.analysis.repository=$GITHUB_REPO \
    $*

else
  echo '======= Build, no analysis, no deploy'

  ./gradlew --no-daemon --console plain build $*

fi
