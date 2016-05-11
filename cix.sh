#!/bin/bash
set -euo pipefail
echo "Running with GRADLE_VERSION=$GRADLE_VERSION"
    
cd integrationTests
mvn clean verify -Dgradle.version=$GRADLE_VERSION

