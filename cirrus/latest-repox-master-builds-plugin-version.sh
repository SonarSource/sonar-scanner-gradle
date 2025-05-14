#!/usr/bin/env bash

curl -slf -H "Authorization: Bearer ${ARTIFACTORY_PRIVATE_PASSWORD}" -o - \
  "https://repox.jfrog.io/artifactory/sonarsource-public-builds/org/sonarsource/scanner/gradle/sonarqube-gradle-plugin/maven-metadata.xml" | \
  sed -E -n 's/^ *<latest>([^<]+)<\/latest> *$/\1/p'
