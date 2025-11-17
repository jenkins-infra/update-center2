#!/bin/bash -ex

## Environment variables that can be configured at the job level:
# - [optional] RUN_STAGES (string): list of top-level tasks ("stages") to execute. Separator is the pipe character '|'. Used to customize the tasks to run (when testing for instance)
# - [optional] SYNC_UC_TASKS (string): list of UC "sync" tasks to perform in parallel during the 'sync-uc' stage. Separator is the pipe character '|'. Used to customize the tasks to run (when testing for instance)
# - [mandatory] UPDATE_CENTER_FILESHARES_ENV_FILES (directory path): directory containing environment files to be sources for each sync. destination.
#     Each task named XX expects a file named 'env-XX' in this directory to be sourced by the script to retrieve settings for the task.
RUN_STAGES="${RUN_STAGES:-generate-site|sync-plugins|sync-uc}"
SYNC_UC_TASKS="${SYNC_UC_TASKS:-rsync-archives.jenkins.io|localrsync-updates.jenkins.io-content|localrsync-updates.jenkins.io-redirections|s3sync-westeurope|s3sync-eastamerica}"
MIRRORBITS_HOST="${MIRRORBITS_HOST:-updates.jenkins.io.privatelink.azurecr.io}"

# Split strings to arrays for feature flags setup
run_stages=()
IFS='|' read -r -a run_stages <<< "${RUN_STAGES}"

www2_dir="${WWW2_DIR:-./www2}"
download_dir="${DOWNLOAD_DIR:-./download}"

# Allow using binaries such as `jq` from local directory
export PATH=.:$PATH

recent_releases_json_file="${1:-"${www2_dir}"/experimental/recent-releases.json}"

# Ensure jq is present or install it;io
# TODO: stop relying on this code block once jq is installed (and maintained) in the "agent-1" (agent.trusted.ci.jenkins.io)
if ! command -v jq >/dev/null
then
    ## Install jq, required by generate.sh script
    wget --no-verbose -O jq https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 || { echo "Failed to download jq" >&2 ; exit 1; }
    chmod +x jq || { echo "Failed to make jq executable" >&2 ; exit 1; }

fi

if [[ "${run_stages[*]}" =~ 'generate-site' ]]
then
    ## Generate the content of $www2_dir and $download_dir folders
    "$( dirname "$0" )/generate.sh" "${www2_dir}" "${download_dir}"
fi

# Publish freshly released plugins to get.jenkins.io (if any)
if [[ "${run_stages[*]}" =~ 'sync-plugins' ]]
then
    RECENT_RELEASES=$( ./jq --raw-output '.releases[] | .name + "/" + .version' "${recent_releases_json_file}" )
    if [[ -n "${RECENT_RELEASES}" ]] ; then
        pushd "${download_dir}"

        # Build the list of files to publish
        declare -a UPLOAD_LIST
        while IFS= read -r release; do
            UPLOAD_LIST+=("plugins/${release}")
        done <<< "${RECENT_RELEASES}"
        # This marker file named 'TIME' must be created locally and then uploaded (to ensure mirrors reads its modtime and humans its content)
        date +%s > ./TIME
        UPLOAD_LIST+=("TIME")

        #### Sync files to archives.jenkins.io as first step (as it is the fallback for downloads from get.jenkins.io)
        # Load the env variables (setting up and credentials) corresponding to the bucket to sync to
        # Note: we don't use all variables from the env. file, unlike the UC_SYNC_TASKS below
        envToLoad="${UPDATE_CENTER_FILESHARES_ENV_FILES}/.env-rsync-archives.jenkins.io"
        # shellcheck source=/dev/null
        source "${envToLoad}"

        # Required variables that should now be set from the .env file
        : "${RSYNC_HOST?}" "${RSYNC_USER?}" "${RSYNC_GROUP?}" "${RSYNC_REMOTE_DIR?}" "${RSYNC_IDENTITY_NAME?}"

        # TODO: use the credentials to retrieve the destination mount point
        time rsync --chown="${RSYNC_USER}":"${RSYNC_GROUP}" --recursive \
            --links --perms --devices --specials `# Unix filesystem for both source and destination` \
            --times `# Use default rsync heuristic with size but also check timestamp (when we re-issue a plugin).` \
            --rsh="ssh -i ${UPDATE_CENTER_FILESHARES_ENV_FILES}/${RSYNC_IDENTITY_NAME}" `# rsync identity file is stored with .env files` \
            --verbose \
            --relative `# Keep the source files relative path in the destination`\
            --stats `# add verbose statistics` \
            --compress `# Sending data to remote servers means data transfer costs, while CPU time is cheap.` \
            "${UPLOAD_LIST[@]}" "${RSYNC_USER}"@"${RSYNC_HOST}":/srv/releases/

        #### Sync files to get.jenkins.io as second step (its repository directory is mounted in an NFS mount)
        # TODO: use the credentials to retrieve the destination mount point
        time rsync --recursive \
            --links --perms --devices --specials `# Unix filesystem for both source and destination` \
            --times `# Use default rsync heuristic with size but also check timestamp (when we re-issue a plugin).` \
            --verbose \
            --relative `# Keep the source files relative path in the destination`\
            --stats `# add verbose statistics` \
            "${UPLOAD_LIST[@]}" /data-storage-jenkins-io/get.jenkins.io/mirrorbits/

        popd
    else
        echo "No recent releases to deploy on get.jenkins.io"
    fi
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

            time rsync --chown="${RSYNC_USER}":"${RSYNC_GROUP}" --recursive --links --perms --times --devices --specials \
                --rsh="ssh -i ${UPDATE_CENTER_FILESHARES_ENV_FILES}/${RSYNC_IDENTITY_NAME}" `# rsync identity file is stored with .env files` \
                --checksum --verbose --compress \
                --exclude=/updates `# populated by https://github.com/jenkins-infra/crawler` \
                --delete `# delete old sites` \
                --stats `# add verbose statistics` \
                "${fileshare_sync_source_abs}" "${RSYNC_USER}"@"${RSYNC_HOST}":"${RSYNC_REMOTE_DIR}"
            ;;

        localrsync*)
            # Required variables that should now be set from the .env file
            : "${RSYNC_REMOTE_DIR?}"

            time rsync --recursive --links --times --devices --specials \
                --checksum --verbose \
                --exclude=/updates `# populated by https://github.com/jenkins-infra/crawler` \
                --delete `# delete old sites` \
                --stats `# add verbose statistics` \
                "${fileshare_sync_source_abs}" "${RSYNC_REMOTE_DIR}"
            ;;

        s3sync*)
            # Required variables that should now be set from the .env file
            : "${BUCKET_NAME?}" "${BUCKET_ENDPOINT_URL?}" "${AWS_ACCESS_KEY_ID?}" "${AWS_SECRET_ACCESS_KEY?}" "${AWS_DEFAULT_REGION?}"

            # Sync 'www-content' (without symlinks) to the bucket,
            # excluding 'updates/' folder as it is populated by https://github.com/jenkins-infra/crawler/blob/master/Jenkinsfile
            time aws s3 sync \
                --no-progress \
                --no-follow-symlinks \
                --checksum-algorithm CRC32 \
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
    chmod -R a+r "${www2_dir}" # Required for updates.jenkins.io rsync copy using distinct user than httpd's
    date +%s > "${www2_dir}"/TIME # Used by mirrorbits and healthchecks

    # Note: these PATH must map to the FILESHARE_SYNC_SOURCE in the ZIP env files (!)
    httpd_dir=./www-redirections
    content_dir=./www-content

    # Cleanup
    rm -rf "${httpd_dir}" "${content_dir}"
    mkdir -p "${httpd_dir}" "${content_dir}"

    # Prepare www-content dir destined to the mirrorbits service
    # By retrieving all JSON files from $www2_dir
    # and dereferencing all internal symlinks to files (as AWS CLIs do not support symlinks)
    # NOTE: order of include and exclude flags MATTERS A LOT
    rsync --archive --verbose \
        --copy-links `# dereference symlinks to avoid issues with aws s3` \
        --safe-links `# ignore symlinks outside of copied tree` \
        --prune-empty-dirs `# Do not copy empty directories` \
        --exclude='updates/' `# Exclude ALL 'updates' directories, not only the root /updates (because symlink dereferencing create additional directories` \
        --exclude="uctest.json" `# Service Healthcheck (empty JSON) only used by Apache` \
        --exclude="download/***" `# Virtual Tree of the download service, redirected to get.jio, with only HTML version listings with relative links to UC itself` \
        --include='*/' `# Include all other directories` \
        --include='*.json' `# Only include JSON files` \
        --include='*.json.html' `# Only include HTML-wrapped JSON files` \
        --exclude='*' `# Exclude all other files` \
        "${www2_dir}"/ "${content_dir}"/

    # Prepare "httpd_dir" directory, same content as $www2_dir
    ## TODO: use only www2_dir when the old PKG machine will be decommissioned
    rsync -av "${www2_dir}"/ "${httpd_dir}"/
    mirrorbits_hostname='mirrors.updates.jenkins.io'
    {
        # Append the httpd -> mirrorbits redirection as fallback (end of htaccess file)
        echo ''
        echo "## Send JSON files to ${mirrorbits_hostname}, except uctest.json (healthcheck served by Apache)"
        echo 'RewriteCond %{REQUEST_URI} ([.](json|json.html)|TIME)$'
        echo 'RewriteCond %{REQUEST_URI} !/uctest.json$'
        # shellcheck disable=SC2016 # The $1 expansion is for RedirectMatch pattern, not shell
        echo 'RewriteRule ^(.*)$ %{REQUEST_SCHEME}://'"${mirrorbits_hostname}"'/$1 [NC,L,R=307]'
    } >> "${httpd_dir}"/.htaccess

    echo '----------------------- Launch synchronisation(s) -----------------------'
    parallel --halt-on-error now,fail=1 parallelfunction ::: "${sync_uc_tasks[@]}"

    # Wait for all deferred tasks
    echo '============================ all parallel sync tasks done ============================'

    # Trigger a mirror scan on mirrorbits once all synchronized copies are finished
    echo '== Triggering mirrors scans...'
    # MIRRORBITS_CLI_PASSWORD is a sensitive values (comes from encrypted credentials)
    mirrorbits_cli_port=3390
    echo "${MIRRORBITS_CLI_PASSWORD}" | mirrorbits -h "${MIRRORBITS_HOST}" -p "${mirrorbits_cli_port}" -a list # Sanity check
    echo "${MIRRORBITS_CLI_PASSWORD}" | mirrorbits -h "${MIRRORBITS_HOST}" -p "${mirrorbits_cli_port}" -a refresh -rehash
    echo "${MIRRORBITS_CLI_PASSWORD}" | mirrorbits -h "${MIRRORBITS_HOST}" -p "${mirrorbits_cli_port}" -a scan -all -enable -timeout=120
fi
