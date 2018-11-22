#!/bin/bash -ex

# Used later for rsyncing updates
UPDATES_SITE="updates.jenkins.io"
RSYNC_USER="www-data"

wget -O jq https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 || { echo "Failed to download jq" >&2 ; exit 1; }
chmod +x jq || { echo "Failed to make jq executable" >&2 ; exit 1; }

export PATH=.:$PATH

"$( dirname "$0" )/generate.sh" ./www2 ./download ./fallback

# push plugins to mirrors.jenkins-ci.org
chmod -R a+r download
rsync -avz --size-only download/plugins/ ${RSYNC_USER}@${UPDATES_SITE}:/srv/releases/jenkins/plugins

# push generated index to the production servers
# 'updates' come from tool installer generator, so leave that alone, but otherwise
# delete old sites
chmod -R a+r www2
rsync -acvz www2/ --exclude=/updates --delete ${RSYNC_USER}@${UPDATES_SITE}:/var/www/${UPDATES_SITE}

# push generated htaccess file on the azure file storage produpdatesproxy
# This file is used by updates.azure.jenkins.io as fallback service for updates.jenkins.io

az storage file upload-batch \
    --account-name produpdatesproxy \
    --account-key "${UPDATESPROXY_STORAGEACCOUNTKEY}" \
    --source ./fallback \
    --destination updates-proxy \
    --validate-content
