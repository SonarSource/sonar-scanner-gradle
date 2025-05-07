#!/usr/bin/env bash

set -euo pipefail

readonly __DEFAULT_M2_HOME="${HOME}/.m2"
readonly __SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
readonly __BUILD_FOLDER="${__SCRIPT_DIR}/../build/"
readonly __LIBS_FOLDER="${__BUILD_FOLDER}/libs/"
readonly __PUBLICATIONS_FOLDER="${__BUILD_FOLDER}/publications/"
readonly __GRADLE_PROPERTIES="${__SCRIPT_DIR}/../gradle.properties"

print_usage() {
  echo "Usage: ${0} <build-number>"
  echo ""
  echo "Downloads the sonar-scanner-gradle artifacts from repox based on a full_version with a build number."
}

download_with_curl() {
  if [[ "${#}" -ne 3 ]]; then
    echo "Error: download_with_curl expects 3 parameters but was given ${#}" >&2
    echo "Usage: download_with_curl <full_version> <artifact extension> <destination directory>" >&2
    exit 3
  fi

  local full_version="${1}"
  local ext="${2}"

  local destination="${3}"
  mkdir --parents "${destination}"

  curl --silent \
    --header "Authorization: Bearer ${ARTIFACTORY_ACCESS_TOKEN}" \
    --output-dir "${destination}" --remote-name \
    "https://repox.jfrog.io/repox/sonarsource/org/sonarsource/scanner/gradle/sonarqube-gradle-plugin/${full_version}/sonarqube-gradle-plugin-${full_version}.${ext}";
}

check_dependencies() {
  if ! command -v curl >/dev/null 2>&1  ; then
    echo "Cannot start script: missing dependency 'curl'" >&2
    exit 2
  fi
}

download_artifacts() {
  if [[ "${#}" -ne 1 ]]; then
    print_usage
    exit 1
  fi

  check_dependencies

  local build_number="${1}"
  local full_version="$(grep --perl-regexp --only-matching '^version\s*+=\s*+(.++)$' "${__GRADLE_PROPERTIES}" | sed 's/version=//g' | sed "s/-SNAPSHOT/.0.${build_number}/g")"

  echo "${full_version}"

  download_with_curl "${full_version}" "jar" "${__LIBS_FOLDER}"
  download_with_curl "${full_version}" "jar.asc" "${__PUBLICATIONS_FOLDER}"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  download_artifacts "${@}"
fi