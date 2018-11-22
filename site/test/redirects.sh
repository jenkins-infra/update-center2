#!/usr/bin/env bash

# Simple script to test actual output is sane
# Ideally we'd set up a local test server with the output, but for now, this will help us quickly figure out if things broke.

# TODO automatically determine these values from current update sites
CURRENT_WEEKLY=2.133
RECENT_WEEKLY=2.125
NEWEST_LTS_BASELINE=2.121
OLDEST_SUPPORTED_LTS_BASELINE=2.60

set -o pipefail
set -o errexit
set -o nounset

function test_redirect () {
  REQUEST_URL="$1"
  DESTINATION="$2"
  echo "Requesting $REQUEST_URL"
  REDIRECT=$( curl -Ii "$REQUEST_URL" 2>/dev/null | fgrep 'Location:' | cut -d' ' -f2 | tr -d '[:space:]' )
  if [ $REDIRECT != $DESTINATION ] ; then
    echo "Expected $DESTINATION but got $REDIRECT for $REQUEST_URL"
    exit 1
  fi
}

test_redirect "https://updates.jenkins.io/update-center.json" "https://updates.jenkins.io/current/update-center.json"
test_redirect "https://updates.jenkins.io/update-center.json.html" "https://updates.jenkins.io/current/update-center.json.html"
test_redirect "https://updates.jenkins.io/update-center.actual.json" "https://updates.jenkins.io/current/update-center.actual.json"
test_redirect "https://updates.jenkins.io/release-history.json" "https://updates.jenkins.io/current/release-history.json"
test_redirect "https://updates.jenkins.io/plugin-versions.json" "https://updates.jenkins.io/current/plugin-versions.json"
test_redirect "https://updates.jenkins.io/plugin-documentation-urls.json" "https://updates.jenkins.io/current/plugin-documentation-urls.json"
test_redirect "https://updates.jenkins.io/latestCore.txt" "https://updates.jenkins.io/current/latestCore.txt"


test_redirect "https://updates.jenkins.io/stable/update-center.json" "https://updates.jenkins.io/stable-$NEWEST_LTS_BASELINE/update-center.json"

test_redirect "https://updates.jenkins.io/update-center.json?version=$CURRENT_WEEKLY" "https://updates.jenkins.io/current/update-center.json"
test_redirect "https://updates.jenkins.io/update-center.json?version=$RECENT_WEEKLY" "https://updates.jenkins.io/current/update-center.json"
test_redirect "https://updates.jenkins.io/update-center.json?version=$NEWEST_LTS_BASELINE" "https://updates.jenkins.io/$NEWEST_LTS_BASELINE/update-center.json"
test_redirect "https://updates.jenkins.io/update-center.json?version=$NEWEST_LTS_BASELINE.1" "https://updates.jenkins.io/stable-$NEWEST_LTS_BASELINE/update-center.json"
test_redirect "https://updates.jenkins.io/update-center.json?version=2.55" "https://updates.jenkins.io/$OLDEST_SUPPORTED_LTS_BASELINE/update-center.json"
test_redirect "https://updates.jenkins.io/update-center.json?version=2.6" "https://updates.jenkins.io/$OLDEST_SUPPORTED_LTS_BASELINE/update-center.json"

#test_redirect "https://updates.jenkins.io/update-center.json?version=1.650" "https://updates.jenkins.io/$OLDEST_SUPPORTED_LTS_BASELINE/update-center.json"
#test_redirect "https://updates.jenkins.io/update-center.json?version=1.580" "https://updates.jenkins.io/$OLDEST_SUPPORTED_LTS_BASELINE/update-center.json"
test_redirect "https://updates.jenkins.io/update-center.json?version=1.580.1" "https://updates.jenkins.io/stable-$OLDEST_SUPPORTED_LTS_BASELINE/update-center.json"
test_redirect "https://updates.jenkins.io/update-center.json?version=2.46.1" "https://updates.jenkins.io/stable-$OLDEST_SUPPORTED_LTS_BASELINE/update-center.json"


# This is probably better -- drop down if older than newest LTS baseline, this isn't getting updates weekly
#test_redirect "https://updates.jenkins.io/update-center.json?version=2.120" "https://updates.jenkins.io/2.107/update-center.json"
# This is the current result
# This test is obviously time dependent, and only useful as long as 2.121 is NEWER THAN the oldest supported baseline.
# While still true when it's the oldest, then this is the expected result.
test_redirect "https://updates.jenkins.io/update-center.json?version=2.115" "https://updates.jenkins.io/2.121/update-center.json"
