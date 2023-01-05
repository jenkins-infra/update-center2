#!/usr/bin/env bash

WWW_ROOT_DIR="${WWW_ROOT_DIR:-${1:-./www2}}"
SELF_URL="${SELF_URL:-${2:-https://updates.jenkins.io}}"

set -o errexit
set -o nounset
set -o pipefail

wget --quiet --convert-links --output-document "$WWW_ROOT_DIR/index.html" --convert-links https://www.jenkins.io/templates/updates/index.html
sed -i.bak -e "s|<jio-navbar.*/jio-navbar>|<jio-navbar class='fixed-top' id='ji-toolbar' property='${SELF_URL}'></jio-navbar>|g" "$WWW_ROOT_DIR/index.html"
sed -i.bak -e "s|<jio-footer.*/jio-footer>|<jio-footer property='${SELF_URL}'></jio-footer>|g" "$WWW_ROOT_DIR/index.html"
