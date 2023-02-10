#!/usr/bin/env bash
set -ev

# Use full history for release
git checkout -B "${BRANCH_NAME}"

mvn -B \
    -Dresume=false \
    -DignoreSnapshots \
    -Darguments="-DskipTests -Dmaven.javadoc.skip=true" \
    -DscmCommentPrefix="[maven-release-plugin][skip ci] " \
    -Dusername="${GIT_USERNAME}" \
    -Dpassword="${GIT_PASSWORD}" \
    release:clean release:prepare release:perform
