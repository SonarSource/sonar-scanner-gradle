#!/bin/bash

. ./cirrus/cirrus-env.sh BUILD

ADDITIONAL_TASKS=""
ADDITIONAL_PARAMS=""
if [[ ! -z "$PULL_REQUEST" ]] || [[ "$GITHUB_BRANCH" == "master" ]] || [[ "$GITHUB_BRANCH" == "branch-"* ]]; then
	ADDITIONAL_TASKS="sonarqube artifactoryPublish"
	ADDITIONAL_PARAMS="--info \
		-Dsonar.host.url=$SONAR_HOST_URL \
		-Dsonar.login=$SONAR_TOKEN \
		-Dsonar.analysis.buildNumber=$BUILD_NUMBER \
		-Dsonar.analysis.pipeline=$CIRRUS_BUILD_ID \
		-Dsonar.analysis.repository=$GITHUB_REPO \
		-Dsonar.analysis.sha1=$GIT_SHA1"

	if [ ! -z "${GITHUB_BASE_BRANCH}" ]; then
		git fetch origin ${GITHUB_BASE_BRANCH}
	fi
	if [[ ! -z "$PULL_REQUEST" ]]; then
		ADDITIONAL_PARAMS="${ADDITIONAL_PARAMS} -Dsonar.analysis.prNumber=${PULL_REQUEST}"
	fi
fi

gradle build ${ADDITIONAL_TASKS} \
	-DbuildNumber=$BUILD_NUMBER \
	${ADDITIONAL_PARAMS} \
	--console plain --no-daemon \
