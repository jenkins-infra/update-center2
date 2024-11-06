#!/bin/bash -ex

## Environment variables that can be configured at the job level:
# - [optional] RUN_STAGES (string): list of top-level tasks ("stages") to execute. Separator is the pipe character '|'. Used to customize the tasks to run (when testing for instance)
# - [optional] SYNC_UC_TASKS (string): list of UC "sync" tasks to perform in parallel during the 'sync-uc' stage. Separator is the pipe character '|'. Used to customize the tasks to run (when testing for instance)
# - [mandatory] UPDATE_CENTER_FILESHARES_ENV_FILES (directory path): directory containing environment files to be sources for each sync. destination.
#     Each task named XX expects a file named 'env-XX' in this directory to be sourced by the script to retrieve settings for the task.
RUN_STAGES="${RUN_STAGES:-generate-site|sync-plugins|sync-uc}"
SYNC_UC_TASKS="${SYNC_UC_TASKS:-rsync-updates.jenkins.io|azsync-content|azsync-redirections-unsecured|azsync-redirections-secured|s3sync-westeurope|s3sync-eastamerica}"
MIRRORBITS_HOST="${MIRRORBITS_HOST:-updates.jio-cli.trusted.ci.jenkins.io}"

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
    UPDATES_SITE="aws.updates.jenkins.io"
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
            : "${STORAGE_NAME?}" "${STORAGE_FILESHARE?}" "${STORAGE_DURATION_IN_MINUTE?}" "${STORAGE_PERMISSIONS?}" "${JENKINS_INFRA_FILESHARE_CLIENT_ID?}" "${JENKINS_INFRA_FILESHARE_CLIENT_SECRET?}" "${JENKINS_INFRA_FILESHARE_TENANT_ID?}" "${FILESHARE_SYNC_DEST_URI?}"

            ## 'get-fileshare-signed-url.sh' command is a script stored in /usr/local/bin used to generate a signed file share URL with a short-lived SAS token
            ## Source: https://github.com/jenkins-infra/pipeline-library/blob/master/resources/get-fileshare-signed-url.sh
            fileShareBaseUrl="$(get-fileshare-signed-url.sh)"
            # Fail fast if no share URL can be generated
            : "${fileShareBaseUrl?}"

            # Append the '$FILESHARE_SYNC_DEST_URI' path on the URI of the generated URL
            # But the URL has a query string so we need a text transformation
            # shellcheck disable=SC2001 # The shell internal search and replace would be tedious due to escapings, hence keeping sed
            fileShareUrl="$(echo "${fileShareBaseUrl}" | sed "s#/?#${FILESHARE_SYNC_DEST_URI}?#")"

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
    httpd_secured_dir=./www-redirections-secured
    httpd_unsecured_dir=./www-redirections-unsecured
    content_dir=./www-content

    # Cleanup
    rm -rf "${content_dir}" "${httpd_secured_dir}" "${httpd_unsecured_dir}"

    # Prepare www-content dir destined to the mirrorbits service
    # By retrieving all JSON files from $www2_dir
    # and dereferencing all internal symlinks to files (as AWS and azcopy CLIs do not support symlinks)
    # NOTE: order of include and exclude flags MATTERS A LOT
    rsync --archive --verbose \
        --copy-links `# dereference symlinks to avoid issues with aws s3 or azcopy` \
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

    # Prepare www-redirections-*secured/ directories, same content as $www2_dir (to allow directory listing) but with dereferenced symlinks, dedicated to httpd services
    rsync --archive --verbose \
        --copy-links `# derefence symlinks` \
        --safe-links `# ignore symlinks outside of copied tree` \
        "${www2_dir}"/ "${httpd_secured_dir}/"

    mirrorbits_hostname='mirrors.updates.jenkins.io'
    {
        # Append the httpd -> mirrorbits redirection as fallback (end of htaccess file) for www-redirections (both secured and unsecured)
        echo ''
        echo "## Send JSON files to ${mirrorbits_hostname}, except uctest.json (healthcheck served by Apache)"
        echo 'RewriteCond %{REQUEST_URI} ([.](json|json.html)|TIME)$'
        echo 'RewriteCond %{REQUEST_URI} !/uctest.json$'
        # shellcheck disable=SC2016 # The $1 expansion is for RedirectMatch pattern, not shell
        echo 'RewriteRule ^(.*)$ %{REQUEST_SCHEME}://'"${mirrorbits_hostname}"'/$1 [NC,L,R=307]'
    } >> "${httpd_secured_dir}"/.htaccess

    # Duplicate to a distinct dir (not required but allow custom HTTP customization if need be)
    # TODO: remove when we force HTTPS
    cp -r "${httpd_secured_dir}" "${httpd_unsecured_dir}"

    echo '----------------------- Launch synchronisation(s) -----------------------'
    parallel --halt-on-error now,fail=1 parallelfunction ::: "${sync_uc_tasks[@]}"

    # Wait for all deferred tasks
    echo '============================ all parallel sync tasks done ============================'

    # Trigger a mirror scan on mirrorbits once all synchronized copies are finished
    echo '== Triggering mirrors scans...'
    # MIRRORBITS_CLI_PASSWORD is a sensitive values (comes from encrypted credentials)
    # 3390 is the port of the "secured" instance (HTTPS) while 3391 of the "unsecured" (HTTP) instance. It's the only difference.
    for mirrorbits_cli_port in 3390 3391
    do
        echo "${MIRRORBITS_CLI_PASSWORD}" | mirrorbits -h "${MIRRORBITS_HOST}" -p "${mirrorbits_cli_port}" -a scan -all -enable -timeout=120
    done
fi
