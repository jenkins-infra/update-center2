#!/bin/bash -ex

## Environment variables that could be configured at the job level:
# - OPT_IN_SYNC_FS_R2: (optional) Set it to "optin" to also update azure.updates.jenkins.io Files Share and R2 buckets

# Used later for rsyncing updates
UPDATES_SITE="updates.jenkins.io"
RSYNC_USER="www-data"

# For syncing R2 buckets with aws-cli configured through environment variables (from Jenkins credentials)
# https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-envvars.html
export AWS_DEFAULT_REGION="auto"
UPDATES_R2_BUCKET="westeurope-updates-jenkins-io"
UPDATES_R2_ENDPOINT="https://8d1838a43923148c5cee18ccc356a594.r2.cloudflarestorage.com"

# For syncing Azure File Share
UPDATES_FILE_SHARE_URL_AND_PATH="https://updatesjenkinsio.file.core.windows.net/updates-jenkins-io/"

# For triggering a mirror scan on mirrorbits
MIRRORBITS_POD_NAME_PREFIX="mirrorbits-lite"
MIRRORBITS_CONTAINER_NAME="mirrorbits-lite"
MIRRORBITS_NAMESPACE="updates-jenkins-io"

## Install jq, required by generate.sh script
wget --no-verbose -O jq https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 || { echo "Failed to download jq" >&2 ; exit 1; }
chmod +x jq || { echo "Failed to make jq executable" >&2 ; exit 1; }

export PATH=.:$PATH

## Generate the content of 'www2' and 'download' folders
"$( dirname "$0" )/generate.sh" ./www2 ./download

## 'download' folder processing
# push plugins to mirrors.jenkins-ci.org
chmod -R a+r ./download
rsync -avz --size-only download/plugins/ ${RSYNC_USER}@${UPDATES_SITE}:/srv/releases/jenkins/plugins

# Invoke a minimal mirrorsync to mirrorbits which will use the 'recent-releases.json' file as input
ssh ${RSYNC_USER}@${UPDATES_SITE} "cat > /tmp/update-center2-rerecent-releases.json" < www2/experimental/recent-releases.json
ssh ${RSYNC_USER}@${UPDATES_SITE} "/srv/releases/sync-recent-releases.sh /tmp/update-center2-rerecent-releases.json"

## 'www2' folder processing
chmod -R a+r ./www2

# TIME sync, used by mirrorbits to know the last update date to take in account
date +%s > ./www2/TIME

## No need to remove the symlinks as the `azcopy sync` for symlinks is not yet supported and we use `--no-follow-symlinks` for `aws s3 sync`
# Perform a copy with dereference symlink (object storage do not support symlinks)
# copy & transform simlinks into referent file/dir
rsync --archive --checksum --verbose --compress \
            --copy-links `# derefence symlinks` \
            --safe-links `# ignore symlinks outside of copied tree` \
            --stats `# add verbose statistics` \
            --exclude='updates' `# populated by https://github.com/jenkins-infra/crawler` \
            --delete `# delete old sites` \
            www2/ www3/

function parallelfunction() {
    echo "=== parallelfunction: $1"

    case $1 in
    rsync*)
        # Push generated index to the production server
        time rsync --archive --checksum --verbose --compress \
            --exclude=/updates `# populated by https://github.com/jenkins-infra/crawler` \
            --delete `# delete old sites` \
            --stats `# add verbose statistics` \
            www2/ ${RSYNC_USER}@${UPDATES_SITE}:/var/www/${UPDATES_SITE}
        ;;

    azsync*)
        # Sync Azure File Share content using www3 to avoid symlinks
        time azcopy sync ./www3/ "${UPDATES_FILE_SHARE_URL_AND_PATH}?${UPDATES_FILE_SHARE_QUERY_STRING}" \
            --recursive=true \
            --delete-destination=true
        ;;

    s3sync*)
        # Sync CloudFlare R2 buckets content excluding 'updates' folder from www3 sync (without symlinks)
        # as this folder is populated by https://github.com/jenkins-infra/crawler/blob/master/Jenkinsfile
        time aws s3 sync ./www3/ s3://"${UPDATES_R2_BUCKET}"/ \
            --no-progress \
            --no-follow-symlinks \
            --size-only \
            --exclude '.htaccess' \
            --endpoint-url "${UPDATES_R2_ENDPOINT}"
        ;;

    *)
        echo -n "Warning: unknown parameter"
        ;;

    esac
}

# Export local variables used in parallelfunction
export UPDATES_SITE
export RSYNC_USER
export UPDATES_R2_BUCKET
export UPDATES_R2_ENDPOINT
export UPDATES_FILE_SHARE_URL_AND_PATH

# Export function to use it with parallel
export -f parallelfunction

echo '----------------------- Launch synchronisation(s) -----------------------'
if [[ $OPT_IN_SYNC_FS_R2 == 'optin' ]]
then
    # Sync updates.jenkins.io and azure.updates.jenkins.io
    parallel --halt-on-error now,fail=1 parallelfunction ::: rsync azsync s3sync
else
    # Sync only updates.jenkins.io
    parallel --halt-on-error now,fail=1 parallelfunction ::: rsync

    # ## If we prefer to avoid parallel when not opt-in, we can replace the previous instruction by the following one:
    # # push generated index to the production server
    # rsync --archive --checksum --verbose --compress \
    #     --exclude=/updates `# populated by https://github.com/jenkins-infra/crawler` \
    #     --delete `# delete old sites` \
    #     --stats `# add verbose statistics` \
    #     ./www2/ ${RSYNC_USER}@${UPDATES_SITE}:/var/www/${UPDATES_SITE}
fi

# Wait for all deferred tasks
echo '============================ all done ============================'

echo '== Triggering a mirror scan on mirrorbits...'
# Requires a valid kubernetes credential file at $KUBECONFIG or $HOME/.kube/config by default
pod_name="$(kubectl --namespace=${MIRRORBITS_NAMESPACE} --no-headers=true get pod --output=name | grep "${MIRRORBITS_POD_NAME_PREFIX}" | head -n1)"
kubectl --namespace=${MIRRORBITS_NAMESPACE} --container="${MIRRORBITS_CONTAINER_NAME}" exec "${pod_name}" -- mirrorbits scan -all -enable -timeout=120
