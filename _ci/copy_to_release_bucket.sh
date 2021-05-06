#!/usr/bin/env bash
echo "========================== Starting Copy to Release Bucket Script ==========================="
set -ev

export AWS_ACCESS_KEY_ID="${RELEASE_AWS_ACCESS_KEY}"
export AWS_SECRET_ACCESS_KEY="${RELEASE_AWS_SECRET_KEY}"

# Identify latest annotated tag (latest version)
export VERSION="$(git describe --tags `git rev-list --tags --max-count=1` | cut -b 21-100)"

SOURCE="s3://alfresco-artefacts-staging/alfresco-file-transfer-receiver/release/${VERSION}"
DESTINATION="s3://eu.dl.alfresco.com/release/alfresco-file-transfer-receiver/${VERSION}"

echo "Source:      ${SOURCE}"
echo "Destination: ${DESTINATION}"

aws s3 cp --acl private "${SOURCE}" "${DESTINATION}" --recursive --include "*.zip"

echo "========================== Finishing Copy to Release Bucket Script =========================="

