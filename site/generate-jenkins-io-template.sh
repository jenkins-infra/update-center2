#!/usr/bin/env bash

# Downloads the template from jenkins.io, then updates the navshell web components to update center specific values

USAGE="Usage: $0 [WWW_ROOT_DIR] [PROPERTY]

1 - WWW_ROOT_DIR - where index.html should be stored - default ./www2
2 - PROPERTY - which jenkins property - default https://updates.jenkins.io

Can be either environmental variables or ordered arguments
"

[[ $# -gt 2 ]] && { echo "${USAGE}" >&2 ; exit 1 ; }

WWW_ROOT_DIR="${WWW_ROOT_DIR:-${1:-./www2}}"
PROPERTY="${PROPERTY:-${2:-https://updates.jenkins.io}}"

[[ -z "$WWW_ROOT_DIR" ]] && { echo "${USAGE}No WWW_ROOT provided" >&2 ; exit 1 ; }
[[ -z "$PROPERTY" ]] && { echo "${USAGE}No PROPERTY provided" >&2 ; exit 1 ; }

set -o errexit
set -o nounset
set -o pipefail

wget --quiet --convert-links --output-document "$WWW_ROOT_DIR/index.tmp.html" https://www.jenkins.io/templates/updates/index.html 
cat "$WWW_ROOT_DIR/index.tmp.html" | \
  sed -e "s|<jio-navbar.*/jio-navbar>|<jio-navbar class='fixed-top' id='ji-toolbar' property='${PROPERTY}'></jio-navbar>|g" | \
  sed -e "s|<jio-footer.*/jio-footer>|<jio-footer property='${PROPERTY}'></jio-footer>|g" \
 > "$WWW_ROOT_DIR/index.html"
rm "$WWW_ROOT_DIR/index.tmp.html"
