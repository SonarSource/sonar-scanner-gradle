#!/usr/bin/env bash

set -euo pipefail

readonly __DEFAULT_M2_HOME="${HOME}/.m2"
readonly __SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
readonly __BUILD_FOLDER="${__SCRIPT_DIR}/../build/"
readonly __LIBS_FOLDER="${__BUILD_FOLDER}/libs/"
readonly __PUBLICATIONS_FOLDER="${__BUILD_FOLDER}/publications/"

print_usage() {
  echo "Usage: ${0} <version>"
  echo ""
  echo "Downloads the sonar-scanner-gradle artifacts from repox based on a version with a build number."
}

check_dependencies() {
  if ! command -v curl 2>&1 1>/dev/null ; then
    echo "Cannot start script: missing dependency 'curl'" >&2
    exit 0
  fi
}

download_with_curl() {
  if [[ "${#}" -ne 3 ]]; then
    echo "Error: download_with_curl expects 3 parameters but was given ${#}" >&2
    echo "Usage: download_with_curl <version> <artifact extension> <destination directory>" >&2
    exit 0
  fi

  local version="${1}"
  local ext="${2}"

  local destination="${3}"
  mkdir --parents "${destination}"

  curl --silent \
    --header "Authorization: Bearer ${ARTIFACTORY_ACCESS_TOKEN}" \
    --output-dir "${destination}" --remote-name \
    "https://repox.jfrog.io/repox/sonarsource/org/sonarsource/scanner/gradle/sonarqube-gradle-plugin/${version}/sonarqube-gradle-plugin-${version}.${ext}";
}

download_artifacts() {
  if [[ "${#}" -ne 1 ]]; then
    print_usage
    exit 0
  fi

  check_dependencies

  local version="${1}"
  download_with_curl "${version}" "jar" "${__LIBS_FOLDER}"
  download_with_curl "${version}" "jar.asc" "${__PUBLICATIONS_FOLDER}"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  download_artifacts "${@}"
fi