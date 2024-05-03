#!/bin/bash -ex

## Environment variables that could be configured at the job level:
# - OPT_IN_SYNC_FS_R2: (optional) Set it to "optin" to also update azure.updates.jenkins.io Files Share and R2 buckets

# Used later for rsyncing updates
UPDATES_SITE="updates.jenkins.io"
RSYNC_USER="mirrorbrain"

# For syncing R2 buckets aws-cli is configured through environment variables (from Jenkins credentials)
# https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-envvars.html
export AWS_DEFAULT_REGION='auto'

## Install jq, required by generate.sh script
wget --no-verbose -O jq https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 || { echo "Failed to download jq" >&2 ; exit 1; }
chmod +x jq || { echo "Failed to make jq executable" >&2 ; exit 1; }

export PATH=.:$PATH

## Generate the content of 'www2' and 'download' folders
"$( dirname "$0" )/generate.sh" ./www2 ./download

## 'download' folder processing
# push plugins to mirrors.jenkins-ci.org
chmod -R a+r download
rsync -rlptDvz --chown=mirrorbrain:www-data --size-only download/plugins/ ${RSYNC_USER}@${UPDATES_SITE}:/srv/releases/jenkins/plugins

# Invoke a minimal mirrorsync to mirrorbits which will use the 'recent-releases.json' file as input
ssh ${RSYNC_USER}@${UPDATES_SITE} "cat > /tmp/update-center2-rerecent-releases.json" < www2/experimental/recent-releases.json
ssh ${RSYNC_USER}@${UPDATES_SITE} "/srv/releases/sync-recent-releases.sh /tmp/update-center2-rerecent-releases.json"

## 'www2' folder processing
chmod -R a+r www2

function parallelfunction() {
    echo "=== parallelfunction: $1"

    case $1 in
    rsync*)
        # Push generated index to the production server
        time rsync --chown=mirrorbrain:www-data --recursive --links --perms --times -D \
            --checksum --verbose --compress \
            --exclude=/updates `# populated by https://github.com/jenkins-infra/crawler` \
            --delete `# delete old sites` \
            --stats `# add verbose statistics` \
            ./www2/ "${RSYNC_USER}@${UPDATES_SITE}:/var/www/${UPDATES_SITE}"
        ;;

    azsync*)
        # Load the env variables corresponding to use get-fileshare-signed-url.sh for the Azure File Share to sync, extracted from the end of the task name
        envToLoad=".env-${1#azsync-}"
        # shellcheck source=/dev/null
        source "${envToLoad}"

        # Required variables that should now be set from the .env file
        : "${STORAGE_FILESHARE?}" "${JENKINS_INFRA_FILESHARE_CLIENT_ID?}" "${JENKINS_INFRA_FILESHARE_CLIENT_SECRET?}" "${FILESHARE_SYNC_SOURCE?}"

# Ensure a trailing slash is always present (as it will be a source for a recursive `azcopy sync`)
FILESHARE_SYNC_SOURCE="${FILESHARE_SYNC_SOURCE%/}/"
        # Script stored in /usr/local/bin used to generate a signed file share URL with a short-lived SAS token
        # Source: https://github.com/jenkins-infra/pipeline-library/blob/master/resources/get-fileshare-signed-url.sh
        fileShareUrl=$(get-fileshare-signed-url.sh)
        # Sync Azure File Share
        time azcopy sync \
            --skip-version-check `# Do not check for new azcopy versions (we have updatecli for this)` \
            --recursive=true \
            --exclude-path="updates" `# populated by https://github.com/jenkins-infra/crawler` \
            --delete-destination=true \
            "${FILESHARE_SYNC_SOURCE}" "${fileShareUrl}"
        ;;

    s3sync*)
        # Retrieve the R2 bucket and the R2 endpoint from the task name passed as argument, minus "s3sync" prefix
        updates_r2_bucket_and_endpoint="${1#s3sync}"
        r2_bucket=${updates_r2_bucket_and_endpoint%|*}
        r2_endpoint=${updates_r2_bucket_and_endpoint#*|}

        # Sync CloudFlare R2 buckets content excluding 'updates' folder from www-content sync (without symlinks)
        # as this folder is populated by https://github.com/jenkins-infra/crawler/blob/master/Jenkinsfile
        # TODO: review/remove .htaccess exclude, already taken in account (?)
        time aws s3 sync \
            --no-progress \
            --no-follow-symlinks \
            --size-only \
            --exclude '.htaccess' \
            --endpoint-url "${r2_endpoint}" \
            ./www-content/ "s3://${r2_bucket}/"
        ;;

    *)
        echo -n 'Warning: unknown parameter'
        ;;

    esac
}

# Export local variables used in parallelfunction
export UPDATES_SITE
export RSYNC_USER

# Export function to use it with parallel
export -f parallelfunction

# parallel added within the permanent trusted agent here:
# https://github.com/jenkins-infra/jenkins-infra/blob/production/dist/profile/manifests/buildagent.pp
command -v parallel >/dev/null 2>&1 || { echo 'ERROR: parralel command not found. Exiting.'; exit 1; }

# Sync only updates.jenkins.io by default
tasks=('rsync')

# Sync updates.jenkins.io and azure.updates.jenkins.io File Share and R2 bucket(s) if the flag is set
if [[ ${OPT_IN_SYNC_FS_R2} == 'optin' ]]
then
    # TIME sync, used by mirrorbits to know the last update date to take in account
    date +%s > ./www2/TIME

    ## No need to remove the symlinks as the `azcopy sync` for symlinks is not yet supported and we use `--no-follow-symlinks` for `aws s3 sync`
    # Perform a copy with dereference symlink (object storage do not support symlinks)
    rm -rf ./www-content/ # Cleanup
    
    # Prepare www-content, a copy of www2 dedicated to mirrorbits service, excluding every .htaccess files
    rsync --archive --verbose \
        --copy-links `# derefence symlinks` \
        --safe-links `# ignore symlinks outside of copied tree` \
        --exclude='updates' `# Exclude ALL 'updates' directories, not only the root /updates (because symlink dereferencing create additional directories` \
        --exclude='**/.htaccess' `# Exclude every .htaccess files` \
        ./www2/ ./www-content/

    # Prepare www-redirections, a copy of www2 dedicated to httpd service, including only .htaccess files (TODO: and html for plugin versions listing?)
    rsync --archive --verbose \
        --copy-links `# derefence symlinks` \
        --safe-links `# ignore symlinks outside of copied tree` \
        --exclude='updates' `# Exclude ALL 'updates' directories, not only the root /updates (because symlink dereferencing create additional directories` \
        --include='**/.htaccess' `# Include only .htaccess files` \
        ./www2/ ./www-redirections/

    # Append the httpd -> mirrorbits redirection as fallback (end of htaccess file) for www-redirections only
    mirrorbits_hostname='mirrors.updates.jenkins.io'
    {
        echo ''
        echo "## Fallback: if not rules match then redirect to ${mirrorbits_hostname}"
        echo "RewriteRule ^.* https://${mirrorbits_hostname}%{REQUEST_URI}? [NC,L,R=307]"
    } >> ./www-redirections/.htaccess

    # Add mirrorbits and httpd file shares sync to the tasks
    tasks+=('azsync-content' 'azsync-htaccess')

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

# Trigger a mirror scan on mirrorbits if the flag is set
if [[ ${OPT_IN_SYNC_FS_R2} == 'optin' ]]
then
    echo '== Triggering a mirror scan on mirrorbits...'
    # Kubernetes namespace of mirrorbits
    mirrorbits_namespace='updates-jenkins-io'

    # Requires a valid kubernetes credential file at $KUBECONFIG or $HOME/.kube/config by default
    pod_name=$(kubectl --namespace="${mirrorbits_namespace}" --no-headers=true get pod --output=name | grep mirrorbits | head -n1)
    kubectl --namespace="${mirrorbits_namespace}" --container=mirrorbits exec "${pod_name}" -- mirrorbits scan -all -enable -timeout=120
fi
