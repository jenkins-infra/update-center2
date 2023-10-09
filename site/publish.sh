#!/bin/bash -ex

# Used later for rsyncing updates
UPDATES_SITE="updates.jenkins.io"
RSYNC_USER="www-data"
UPDATES_R2_BUCKETS="westeurope-updates-jenkins-io"
UPDATES_R2_ENDPOINT="https://8d1838a43923148c5cee18ccc356a594.r2.cloudflarestorage.com"
if [[ -z "$ROOT_FOLDER" ]]; then
    ROOT_FOLDER="/home/jenkins/lemeurherve/pr-745" # TODO: remove after debug
fi

echo "ROOT_FOLDER: ${ROOT_FOLDER}"

wget --no-verbose -O jq https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 || { echo "Failed to download jq" >&2 ; exit 1; }
chmod +x jq || { echo "Failed to make jq executable" >&2 ; exit 1; }

export PATH=.:$PATH

# "$( dirname "$0" )/generate.sh" "${ROOT_FOLDER}"/www2 ./download

# push plugins to mirrors.jenkins-ci.org
# chmod -R a+r download
# rsync -avz --size-only download/plugins/ ${RSYNC_USER}@${UPDATES_SITE}:/srv/releases/jenkins/plugins

# # Invoke a minimal mirrorsync to mirrorbits which will use the 'recent-releases.json' file as input
# ssh ${RSYNC_USER}@${UPDATES_SITE} "cat > /tmp/update-center2-rerecent-releases.json" < www2/experimental/recent-releases.json
# ssh ${RSYNC_USER}@${UPDATES_SITE} "/srv/releases/sync-recent-releases.sh /tmp/update-center2-rerecent-releases.json"

# # push generated index to the production servers
# # 'updates' come from tool installer generator, so leave that alone, but otherwise
# # delete old sites
chmod -R a+r "${ROOT_FOLDER}"/www2
# # rsync -acvz www2/ --exclude=/updates --delete ${RSYNC_USER}@${UPDATES_SITE}:/var/www/${UPDATES_SITE}

# ### TODO: cleanup original commands above when https://github.com/jenkins-infra/helpdesk/issues/2649 is ready for production

# Original-like rsync to pkg VM for testing and timing purposes
time rsync -acvz "${ROOT_FOLDER}"/www2/ --exclude=/updates --delete --stats ${RSYNC_USER}@${UPDATES_SITE}:/tmp/lemeurherve/pr-745/www/${UPDATES_SITE}

### Above ^: not to be modified

### Below: parallelise

# copy & transform simlinks into referent file/dir
# time rsync -acvz --copy-links --safe-links --stats "${ROOT_FOLDER}"/www2/ --exclude=/updates --delete "${ROOT_FOLDER}"/www3/
time rsync -acvz --no-links --stats "${ROOT_FOLDER}"/www2/ --exclude=/updates --delete "${ROOT_FOLDER}"/www3/

ls -l "${ROOT_FOLDER}"/www3/
ls -l "${ROOT_FOLDER}"/www3/current

# Sync Azure File Share content
time azcopy sync "${ROOT_FOLDER}"/www3/ "${UPDATES_FILE_SHARE_URL}" --recursive=true --delete-destination=true --exclude-path="updates"

# Debug
echo "= azcopy sync done."

# Sync CloudFlare R2 buckets content using the updates-jenkins-io profile, excluding 'updates' folder which comes from tool installer generator
time aws s3 sync "${ROOT_FOLDER}"/www3/ s3://"${UPDATES_R2_BUCKETS}"/ --profile updates-jenkins-io --no-progress --size-only --no-follow-symlinks --exclude="updates/*" --endpoint-url "${UPDATES_R2_ENDPOINT}"
# aws s3 cp "${ROOT_FOLDER}"/www2/ s3://"${UPDATES_R2_BUCKETS}"/ --profile updates-jenkins-io --no-progress --no-follow-symlinks --exclude="updates/*" --endpoint-url "${UPDATES_R2_ENDPOINT}"


## TODO: test if needed rclone both rsync VM and R2 bucket(s) replacing these 2 calls

# Debug
echo "= aws sync done."

# # /TIME sync, used by mirrorbits to know the last update date to take in account
# date +%s > ./www2/TIME
# aws s3 cp ./www2/TIME s3://"${UPDATES_R2_BUCKETS}"/ --profile updates-jenkins-io --endpoint-url "${UPDATES_R2_ENDPOINT}"
# azcopy cp ./www2/TIME "${UPDATES_FILE_SHARE_URL}" --overwrite=true
