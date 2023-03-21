#!/usr/bin/env bash
echo "========================== Starting Copy to Release Bucket Script ==========================="
set -ev

# Identify latest annotated tag (latest version)
export VERSION="$(git describe --tags `git rev-list --tags --max-count=1` | cut -b 21-100)"

SOURCE="s3://alfresco-artefacts-staging/alfresco-file-transfer-receiver/releases/${VERSION}"
DESTINATION="s3://eu.dl.alfresco.com/release/alfresco-file-transfer-receiver/${VERSION}"

echo "Source:      ${SOURCE}"
echo "Destination: ${DESTINATION}"

aws s3 cp --acl private --copy-props none "${SOURCE}" "${DESTINATION}" --recursive --include "*.*"

echo "========================== Finishing Copy to Release Bucket Script =========================="

