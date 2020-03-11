#!/bin/bash

. ./private/cirrus/cirrus-env.sh QA

if [[ "$1" == oracle* ]]; then
  # Need ~ 10 minutes to have a running instance of Oracle
  sleep 600
fi

ORCHESTRATOR_CONFIG=$1

ITS_TASK=":private:it-core:integrationTest -Dcategory=${QA_CATEGORY}"
if [ "${QA_CATEGORY}" == "Gov" ]; then
	ITS_TASK=':private:it-governance:it-tests:integrationTest'
fi
if [ "${QA_CATEGORY}"  == "Branch" ]; then
	ITS_TASK=':private:it-branch:it-tests:integrationTest'
fi
if [ "${QA_CATEGORY}"  == "License" ]; then
	ITS_TASK=':private:it-license:it-tests:integrationTest'
fi
if [ "${QA_CATEGORY}"  == "HA" ]; then
        ITS_TASK=':private:it-ha:it-tests:integrationTest'
fi

gradle ${ITS_TASK} \
	-DbuildNumber=$BUILD_NUMBER \
	-Pqa -Ptime-tracker \
	-Dorchestrator.configUrl=file:///$CIRRUS_WORKING_DIR/private/cirrus/orchestrator-$ORCHESTRATOR_CONFIG.properties \
	--console plain --no-daemon --build-cache
