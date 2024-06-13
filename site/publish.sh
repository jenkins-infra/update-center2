#!/bin/bash -ex

## Environment variables that can be configured at the job level:
# - [optional] RUN_STAGES (string): list of top-level tasks ("stages") to execute. Separator is the pipe character '|'. Used to customize the tasks to run (when testing for instance)
# - [optional] SYNC_UC_TASKS (string): list of UC "sync" tasks to perform in parallel during the 'sync-uc' stage. Separator is the pipe character '|'. Used to customize the tasks to run (when testing for instance)
# - [mandatory] UPDATE_CENTER_FILESHARES_ENV_FILES (directory path): directory containing environment files to be sources for each sync. destination.
#     Each task named XX expects a file named 'env-XX' in this directory to be sourced by the script to retrieve settings for the task.
RUN_STAGES="${RUN_STAGES:-generate-site|sync-plugins|sync-uc}"
SYNC_UC_TASKS="${SYNC_UC_TASKS:-rsync-updates.jenkins.io|azsync-content|s3sync-westeurope}"

# Split strings to arrays for feature flags setup
run_stages=()
IFS='|' read -r -a run_stages <<< "${RUN_STAGES}"

www2_dir="${WWW2_DIR:-./www2}"
download_dir="${WWW2_DIR:-./download}"

if [[ "${run_stages[*]}" =~ 'generate-site' ]]
then
    ## Install jq, required by generate.sh script
    wget --no-verbose -O jq https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 || { echo "Failed to download jq" >&2 ; exit 1; }
    chmod +x jq || { echo "Failed to make jq executable" >&2 ; exit 1; }

    export PATH=.:$PATH

    ## Generate the content of $www2_dir and $download_dir folders
    "$( dirname "$0" )/generate.sh" "${www2_dir}" "${download_dir}"
fi

if [[ "${run_stages[*]}" =~ 'sync-plugins' ]]
then
    UPDATES_SITE="updates.jenkins.io"
    RSYNC_USER="mirrorbrain"

    ## $download_dir folder processing
    # push plugins to mirrors.jenkins-ci.org
    chmod -R a+r "${download_dir}"
    rsync -rlptDvz --chown=mirrorbrain:www-data --size-only "${download_dir}"/plugins/ "${RSYNC_USER}@${UPDATES_SITE}":/srv/releases/jenkins/plugins

    # Invoke a minimal mirrorsync to mirrorbits which will use the 'recent-releases.json' file as input
    ssh "${RSYNC_USER}@${UPDATES_SITE}" "cat > /tmp/update-center2-rerecent-releases.json" < "${www2_dir}"/experimental/recent-releases.json
    ssh "${RSYNC_USER}@${UPDATES_SITE}" "/srv/releases/sync-recent-releases.sh /tmp/update-center2-rerecent-releases.json"
fi

if [[ "${run_stages[*]}" =~ 'sync-uc' ]]
then
    # Ensure credentials are defined
    : "${UPDATE_CENTER_FILESHARES_ENV_FILES?}"

    sync_uc_tasks=()
    IFS='|' read -r -a sync_uc_tasks <<< "${SYNC_UC_TASKS}"

    command -v parallel >/dev/null 2>&1 || { echo 'ERROR: parallel command not found. Exiting.'; exit 1; }

    # Define function to be called for each parallel UC tasks (see call after the function code)
    function parallelfunction() {
        echo "=== parallelfunction: $1"

        # Load the env variables (setting up and credentials) corresponding to the bucket to sync to
        # Note that some variables are needed by get-fileshare-signed-url.sh
        envToLoad="${UPDATE_CENTER_FILESHARES_ENV_FILES}/.env-${1}"
        # shellcheck source=/dev/null
        source "${envToLoad}"

        : "${FILESHARE_SYNC_SOURCE?}"

        # Ensure absolute path WITH a trailing slash (as it will be a source for different commands where it has a meaning)
        local fileshare_sync_source_abs
        fileshare_sync_source_abs="$(cd "${FILESHARE_SYNC_SOURCE}" && pwd -P)/"

        case $1 in
        rsync*)
            # Required variables that should now be set from the .env file
            : "${RSYNC_HOST?}" "${RSYNC_USER?}" "${RSYNC_GROUP?}" "${RSYNC_REMOTE_DIR?}" "${RSYNC_IDENTITY_NAME?}"

            time rsync --chown="${RSYNC_USER}":"${RSYNC_GROUP}" --recursive --links --perms --times -D \
                --rsh="ssh -i ${UPDATE_CENTER_FILESHARES_ENV_FILES}/${RSYNC_IDENTITY_NAME}" `# rsync identity file is stored with .env files` \
                --checksum --verbose --compress \
                --exclude=/updates `# populated by https://github.com/jenkins-infra/crawler` \
                --delete `# delete old sites` \
                --stats `# add verbose statistics` \
                "${fileshare_sync_source_abs}" "${RSYNC_USER}"@"${RSYNC_HOST}":"${RSYNC_REMOTE_DIR}"
            ;;

        azsync*)
            # Required variables that should now be set from the .env file
            : "${STORAGE_NAME?}" "${STORAGE_FILESHARE?}" "${STORAGE_DURATION_IN_MINUTE?}" "${STORAGE_PERMISSIONS?}" "${JENKINS_INFRA_FILESHARE_CLIENT_ID?}" "${JENKINS_INFRA_FILESHARE_CLIENT_SECRET?}" "${JENKINS_INFRA_FILESHARE_TENANT_ID?}"

            ## 'get-fileshare-signed-url.sh' command is a script stored in /usr/local/bin used to generate a signed file share URL with a short-lived SAS token
            ## Source: https://github.com/jenkins-infra/pipeline-library/blob/master/resources/get-fileshare-signed-url.sh
            fileShareUrl="$(get-fileshare-signed-url.sh)"
            # Fail fast if no share URL can be generated
            : "${fileShareUrl?}"

            # Sync Azure File Share
            time azcopy sync \
                --skip-version-check `# Do not check for new azcopy versions (we have updatecli for this)` \
                --recursive=true \
                --exclude-path="updates" `# populated by https://github.com/jenkins-infra/crawler` \
                --delete-destination=true \
                "${fileshare_sync_source_abs}" "${fileShareUrl}"
            ;;

        s3sync*)
            # Required variables that should now be set from the .env file
            : "${BUCKET_NAME?}" "${BUCKET_ENDPOINT_URL?}" "${AWS_ACCESS_KEY_ID?}" "${AWS_SECRET_ACCESS_KEY?}" "${AWS_DEFAULT_REGION?}"

            # Sync 'www-content' (without symlinks) to the bucket,
            # excluding 'updates/' folder as it is populated by https://github.com/jenkins-infra/crawler/blob/master/Jenkinsfile
            time aws s3 sync \
                --no-progress \
                --no-follow-symlinks \
                --size-only \
                --exclude '.htaccess' \
                --endpoint-url "${BUCKET_ENDPOINT_URL}" \
                "${fileshare_sync_source_abs}" "s3://${BUCKET_NAME}/"
            ;;

        *)
            echo -n "Warning: unknown sync UC task: ${1}"
            ;;

        esac
    }
    # Export function to use it with parallel
    export -f parallelfunction

    ############# Prepare the different UC source directories to be copied to different destinations
    chmod -R a+r "${www2_dir}"
    date +%s > "${www2_dir}"/TIME # TIME sync, used by mirrorbits to know the last update date to take in account

    ## No need to remove the symlinks as the `azcopy sync` for symlinks is not yet supported and we use `--no-follow-symlinks` for `aws s3 sync`
    # Perform a copy with dereference symlink (object storage do not support symlinks)
    rm -rf ./www-content/ ./www-redirections/ # Cleanup

    # Prepare www-content, a copy of the $www2_dir dedicated to mirrorbits service, excluding every .htaccess files
    rsync --archive --verbose \
        --copy-links `# derefence symlinks` \
        --safe-links `# ignore symlinks outside of copied tree` \
        --prune-empty-dirs `# Do not copy empty directories` \
        --exclude='updates/' `# Exclude ALL 'updates' directories, not only the root /updates (because symlink dereferencing create additional directories` \
        --exclude='.htaccess' `# Exclude every .htaccess files` \
        "${www2_dir}"/ ./www-content/

    # Prepare www-redirections, a copy of $www2_dir dedicated to httpd service, including only .htaccess files (TODO: and html for plugin versions listing?)
    rsync --archive --verbose \
        --copy-links `# derefence symlinks` \
        --safe-links `# ignore symlinks outside of copied tree` \
        --prune-empty-dirs `# Do not copy empty directories` \
        --include "*/" `# Includes all directories in the filtering` \
        --include=".htaccess" `# Includes all elements named '.htaccess' in the filtering - redirections logic` \
        --exclude="*" `# Exclude all elements found in source and not matching pattern aboves (must be the last filter flag)` \
        "${www2_dir}"/ ./www-redirections/

    # Append the httpd -> mirrorbits redirection as fallback (end of htaccess file) for www-redirections only
    mirrorbits_hostname='mirrors.updates.jenkins.io'
    {
        echo ''
        echo "## Fallback: if not rules match then redirect to ${mirrorbits_hostname}"
        echo "RewriteRule ^.* https://${mirrorbits_hostname}%{REQUEST_URI}? [NC,L,R=307]"
    } >> ./www-redirections/.htaccess

    echo '----------------------- Launch synchronisation(s) -----------------------'
    parallel --halt-on-error now,fail=1 parallelfunction ::: "${sync_uc_tasks[@]}"

    # Wait for all deferred tasks
    echo '============================ all parallel sync tasks done ============================'

    # Trigger a mirror scan on mirrorbits once all synchronized copies are finished
    echo '== Triggering a mirror scan on mirrorbits...'
    # Kubernetes namespace of mirrorbits
    mirrorbits_namespace='updates-jenkins-io'

    # Requires a valid kubernetes credential file at $KUBECONFIG or $HOME/.kube/config by default
    pod_name="$(kubectl --namespace="${mirrorbits_namespace}" --no-headers=true get pod --output=name | grep mirrorbits | head -n1)"
    kubectl --namespace="${mirrorbits_namespace}" --container=mirrorbits exec "${pod_name}" -- mirrorbits scan -all -enable -timeout=120
fi
