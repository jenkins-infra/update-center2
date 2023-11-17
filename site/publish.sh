#!/bin/bash -ex

## Environment variables that could be configured at the job level:
# - OPT_IN_SYNC_FS_R2: (optional) Set it to "optin" to also update azure.updates.jenkins.io Files Share and R2 buckets

# Used later for rsyncing updates
UPDATES_SITE="updates.jenkins.io"
RSYNC_USER="www-data"

# For syncing R2 buckets aws-cli is configured through environment variables (from Jenkins credentials)
# https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-envvars.html
export AWS_DEFAULT_REGION="auto"

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
        time azcopy sync ./www3/ "https://updatesjenkinsio.file.core.windows.net/updates-jenkins-io/?${UPDATES_FILE_SHARE_QUERY_STRING}" \
            --recursive=true \
            --delete-destination=true
        ;;

    s3sync*)
        # Retrieve the R2 bucket and the R2 endpoint from the task name passed as argument, minus "s3sync" prefix
        updates_r2_bucket_and_endpoint="${1#s3sync}"
        r2_bucket=${updates_r2_bucket_and_endpoint%|*}
        r2_endpoint=${updates_r2_bucket_and_endpoint#*|}

        # Sync CloudFlare R2 buckets content excluding 'updates' folder from www3 sync (without symlinks)
        # as this folder is populated by https://github.com/jenkins-infra/crawler/blob/master/Jenkinsfile
        time aws s3 sync ./www3/ s3://"${r2_bucket}"/ \
            --no-progress \
            --no-follow-symlinks \
            --size-only \
            --exclude '.htaccess' \
            --endpoint-url "${r2_endpoint}"
        ;;

    *)
        echo -n "Warning: unknown parameter"
        ;;

    esac
}

# Export local variables used in parallelfunction
export UPDATES_SITE
export RSYNC_USER

# Export function to use it with parallel
export -f parallelfunction

# Sync only updates.jenkins.io by default
tasks=("rsync")

# Sync updates.jenkins.io and azure.updates.jenkins.io File Share and R2 bucket(s) if the flag is set
if [[ $OPT_IN_SYNC_FS_R2 == "optin" ]]
then
    # Add File Share sync to the tasks
    tasks+=("azsync")

    # Add each R2 bucket sync to the tasks
    updates_r2_bucket_and_endpoint_pairs=("westeurope-updates-jenkins-io|https://8d1838a43923148c5cee18ccc356a594.r2.cloudflarestorage.com")
    for r2_bucket_and_endpoint_pair in "${updates_r2_bucket_and_endpoint_pairs[@]}"
    do
        tasks+=("s3sync${r2_bucket_and_endpoint_pair}")
    done
fi

echo '----------------------- Launch synchronisation(s) -----------------------'
parallel --halt-on-error now,fail=1 parallelfunction ::: "${tasks[@]}"

# Wait for all deferred tasks
echo '============================ all done ============================'

echo '== Triggering a mirror scan on mirrorbits...'
# Kubernetes namespace of mirrorbits
mirrorbits_namespace="updates-jenkins-io"

# Requires a valid kubernetes credential file at $KUBECONFIG or $HOME/.kube/config by default
pod_name="$(kubectl --namespace=${mirrorbits_namespace} --no-headers=true get pod --output=name | grep mirrorbits-lite | head -n1)"
kubectl --namespace=${mirrorbits_namespace} --container=mirrorbits-lite exec "${pod_name}" -- mirrorbits scan -all -enable -timeout=120
