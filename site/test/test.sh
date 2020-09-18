#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o pipefail

SIMPLE_SCRIPT_DIR="$( dirname "$0" )"
SCRIPT_DIR="$( readlink -f "${SIMPLE_SCRIPT_DIR}" 2>/dev/null || greadlink -f "${SIMPLE_SCRIPT_DIR}" )" || { echo "Failed to determine script directory using (g)readlink -f" >&2 ; exit 1 ; }

"${SCRIPT_DIR}"/../generate-htaccess.sh \
    2.172 2.173 2.176 2.177 2.181 2.184 2.185 2.191 2.195 2.199 2.204 2.205 2.212 2.217 2.222 2.223 2.240 \
    2.164.2 2.164.3 2.176.1 2.176.2 2.176.3 2.176.4 2.190.1 2.190.3 2.204.1 2.204.2 2.204.4 2.204.6 2.222.1 \
    > "${SCRIPT_DIR}/htaccess.tmp"

docker build -t update-center2-test "${SCRIPT_DIR}" || { echo "Failed to build Docker image" >&2 ; exit 1 ; }

trap 'cleanup' 0
function cleanup {
  docker stop update-center2-test
}
docker run --rm -dit --name update-center2-test -p 8080:80 update-center2-test || { echo "Failed to start Docker container" >&2 ; exit 1 ; }

echo "Waiting for the container to be ready..."
sleep 3

TEST_BASE_URL="http://localhost:8080"

function test_redirect () {
  local REQUEST_URL="$1"
  local DESTINATION="$2"
  echo "Requesting $REQUEST_URL (-> $DESTINATION)"
  REDIRECT=$( curl -I "$REQUEST_URL" 2>/dev/null | fgrep 'Location:' | cut -d' ' -f2 | tr -d '[:space:]' ) || { echo "Failed to curl $REQUEST_URL" >&2 ; }
  if [ "$REDIRECT" != "$DESTINATION" ] ; then
    echo "Expected $DESTINATION but got $REDIRECT for $REQUEST_URL"
  fi
}

test_redirect "$TEST_BASE_URL/update-center.json" "$TEST_BASE_URL/current/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json.html" "$TEST_BASE_URL/current/update-center.json.html"

# Accessed by https://github.com/jenkins-infra/jenkins.io/blob/3892ea2ad4b4a67e1f8aebbfab261ae88628c176/scripts/fetch-external-resources#L48
test_redirect "$TEST_BASE_URL/update-center.actual.json" "$TEST_BASE_URL/current/update-center.actual.json"

# Accessed by https://github.com/jenkins-infra/jenkins.io/blob/a9d12fe7234199ee8c4426f6eca98b71de8dffbd/Makefile#L46
# Target accessed by https://github.com/jenkins-infra/plugin-site-api/blob/bd2a5fe337ddf9526666ef5a7ad6a3f5bb388c6f/src/main/java/io/jenkins/plugins/generate/parsers/WikiPluginDataParser.java#L30
test_redirect "$TEST_BASE_URL/release-history.json" "$TEST_BASE_URL/current/release-history.json"

# Target accessed by https://github.com/jenkinsci/plugin-installation-manager-tool/blob/fbdbd6b8e8e291db28fadc4ad4b5ec9795bd3c37/plugin-management-library/src/main/java/io/jenkins/tools/pluginmanager/config/Settings.java#L16
test_redirect "$TEST_BASE_URL/plugin-versions.json" "$TEST_BASE_URL/current/plugin-versions.json"
test_redirect "$TEST_BASE_URL/plugin-documentation-urls.json" "$TEST_BASE_URL/current/plugin-documentation-urls.json"

# Expect no redirect at all -- this depends on HTTP, so ignore
#test_redirect "$TEST_BASE_URL/tiers.json" ""

# Accessed by https://github.com/jenkins-infra/jenkins.io/blob/3892ea2ad4b4a67e1f8aebbfab261ae88628c176/scripts/fetch-external-resources#L18
test_redirect "$TEST_BASE_URL/latestCore.txt" "$TEST_BASE_URL/current/latestCore.txt"


test_redirect "$TEST_BASE_URL/stable/update-center.json" "$TEST_BASE_URL/dynamic-stable-2.222.1/update-center.json"

# Accessed by https://github.com/jenkins-infra/jenkins.io/blob/3892ea2ad4b4a67e1f8aebbfab261ae88628c176/scripts/fetch-external-resources#L24
test_redirect "$TEST_BASE_URL/stable/latestCore.txt" "$TEST_BASE_URL/dynamic-stable-2.222.1/latestCore.txt"

test_redirect "$TEST_BASE_URL/update-center.json?version=2.246" "$TEST_BASE_URL/dynamic-2.240/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.240" "$TEST_BASE_URL/dynamic-2.240/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.225" "$TEST_BASE_URL/dynamic-2.223/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.223" "$TEST_BASE_URL/dynamic-2.223/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.222" "$TEST_BASE_URL/dynamic-2.222/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.222.1" "$TEST_BASE_URL/dynamic-stable-2.222.1/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.55" "$TEST_BASE_URL/dynamic-2.172/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.6" "$TEST_BASE_URL/dynamic-2.172/update-center.json"


test_redirect "$TEST_BASE_URL/update-center.json?version=2.204.1" "$TEST_BASE_URL/dynamic-stable-2.204.1/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.204.2" "$TEST_BASE_URL/dynamic-stable-2.204.2/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.204.3" "$TEST_BASE_URL/dynamic-stable-2.204.2/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.204.4" "$TEST_BASE_URL/dynamic-stable-2.204.4/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.204.5" "$TEST_BASE_URL/dynamic-stable-2.204.4/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.204.6" "$TEST_BASE_URL/dynamic-stable-2.204.6/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.204.7" "$TEST_BASE_URL/dynamic-stable-2.204.6/update-center.json"

test_redirect "$TEST_BASE_URL/update-center.actual.json?version=2.222" "$TEST_BASE_URL/dynamic-2.222/update-center.actual.json"
test_redirect "$TEST_BASE_URL/update-center.actual.json?version=2.222.1" "$TEST_BASE_URL/dynamic-stable-2.222.1/update-center.actual.json"

# No more redirects to tiers
test_redirect "$TEST_BASE_URL/plugin-documentation-urls.json?version=2.222.1" "$TEST_BASE_URL/current/plugin-documentation-urls.json"
test_redirect "$TEST_BASE_URL/latestCore.txt?version=2.222.1" "$TEST_BASE_URL/current/latestCore.txt"

# Jenkins 1.x gets the oldest update sites
test_redirect "$TEST_BASE_URL/update-center.json?version=1.650" "$TEST_BASE_URL/dynamic-2.172/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=1.580" "$TEST_BASE_URL/dynamic-2.172/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=1.580.1" "$TEST_BASE_URL/dynamic-stable-2.164.2/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=2.46.1" "$TEST_BASE_URL/dynamic-stable-2.164.2/update-center.json"

# This would probably be ideal: Drop down if older than newest LTS baseline, this instance isn't getting updates weekly
test_redirect "$TEST_BASE_URL/update-center.json?version=2.200" "$TEST_BASE_URL/dynamic-2.199/update-center.json"

# Future major releases go to the most recent update sites:
test_redirect "$TEST_BASE_URL/update-center.json?version=3.0" "$TEST_BASE_URL/dynamic-2.240/update-center.json"
test_redirect "$TEST_BASE_URL/update-center.json?version=3.0.1" "$TEST_BASE_URL/dynamic-stable-2.222.1/update-center.json"

# Workaround, see generate-htaccess.sh
test_redirect "$TEST_BASE_URL/download/war/latest/jenkins.war" "https://updates.jenkins.io/latest/jenkins.war"
test_redirect "$TEST_BASE_URL/download/plugins/git/latest/git.hpi" "https://updates.jenkins.io/latest/git.hpi"
test_redirect "$TEST_BASE_URL/download/plugins/lolwut/latest/git.hpi" "https://updates.jenkins.io/latest/git.hpi" # Fun side effect of the redirect rule
