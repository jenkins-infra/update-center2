#!/bin/bash -ex

# Used later for rsyncing updates
UPDATES_SITE="updates.jenkins.io"
RSYNC_USER="www-data"
UPDATES_R2_BUCKETS="westeurope-updates-jenkins-io"
UPDATES_R2_ENDPOINT="https://8d1838a43923148c5cee18ccc356a594.r2.cloudflarestorage.com"
if [[ -z "$ROOT_FOLDER" ]]; then
    ROOT_FOLDER="/home/jenkins/lemeurherve/pr-745" # TODO: remove after debug
fi

# parallel added within the permanent trusted agent here : https://github.com/jenkins-infra/jenkins-infra/blob/production/dist/profile/manifests/buildagent.pp
command -v parallel >/dev/null 2>&1 || { echo "ERROR: parralel command not found. Exiting."; exit 1; }

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

####   no need to remove the symlinks as the `azcopy sync`for symlinks is not yet supported and we use `--no-follow-symlinks` for `aws s3 sync``
# Perform a copy with dereference symlink (object storage do not support symlinks)
# copy & transform simlinks into referent file/dir
time rsync  -acvz \
            --copy-links `# derefence symlinks` \
            --safe-links `# ignore symlinks outside of copied tree` \
            --stats `# add verbose statistics` \
            "${ROOT_FOLDER}"/www2/ --exclude=updates/ --delete "${ROOT_FOLDER}"/www3/
## "${ROOT_FOLDER}"/www3/ doesn't have symlinks already
## "${ROOT_FOLDER}"/www2/ still have symlinks
### Below: parallelise
echo '--------------------------- Launch Parallelization -----------------------'


## define function
function parallelfunction() {
    echo "=== parallelfunction: $1"

    case $1 in
    rsync*)
        # keep exclude as from www2 with symlinks
        time rsync -acz "${ROOT_FOLDER}"/www2/ --exclude=/updates --delete --stats ${RSYNC_USER}@${UPDATES_SITE}:/tmp/lemeurherve/pr-745/www/${UPDATES_SITE}
        ;;

    azsync*)
        # Sync Azure File Share content (using www3 to avoid symlinks)
        time azcopy sync "${ROOT_FOLDER}"/www3/ "${UPDATES_FILE_SHARE_URL}" --recursive=true --delete-destination=true
        ;;

    s3sync*)
        # Sync CloudFlare R2 buckets content using the updates-jenkins-io profile, excluding 'updates' folder which comes from tool installer generator (using www3 to avoid symlinks)
        time aws s3 sync "${ROOT_FOLDER}"/www3/ s3://"${UPDATES_R2_BUCKETS}"/ --profile updates-jenkins-io --no-progress --no-follow-symlinks --size-only --exclude '.htaccess' --endpoint-url "${UPDATES_R2_ENDPOINT}"
        ;;

    *)
        echo -n "unknown"
        ;;
    esac

}

## need to export variables used within the functions above
export UPDATES_SITE
export RSYNC_USER
export UPDATES_R2_BUCKETS
export UPDATES_R2_ENDPOINT
export ROOT_FOLDER

## export function to use with parallel
export -f parallelfunction
parallel --halt-on-error now,fail=1 parallelfunction ::: rsync azsync s3sync


# wait for all deferred task
echo '===============================    all done   ============================'

## TODO: test if needed rclone both rsync VM and R2 bucket(s) replacing these 2 calls

# Debug

# # /TIME sync, used by mirrorbits to know the last update date to take in account
# date +%s > ./www2/TIME
# aws s3 cp ./www2/TIME s3://"${UPDATES_R2_BUCKETS}"/ --profile updates-jenkins-io --endpoint-url "${UPDATES_R2_ENDPOINT}"
# azcopy cp ./www2/TIME "${UPDATES_FILE_SHARE_URL}" --overwrite=true
