#!/bin/bash
# Evaluate a Maven expression
# Usage: maven_expression "expression"
# Example: maven_expression "project.version"

#set -euo pipefail

mvn -q -Dexec.executable="echo" -Dexec.args="\${$1}" --non-recursive org.codehaus.mojo:exec-maven-plugin:1.3.1:exec
