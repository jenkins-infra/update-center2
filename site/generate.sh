#!/usr/bin/env bash

# Usage: SECRET=dirname ./site/generate.sh "./www2" "./download"
[[ $# -eq 2 ]] || { echo "Usage: $0 <www root dir> <download root dir>" >&2 ; exit 1 ; }
[[ -n "$1" ]] || { echo "Non-empty www root dir required" >&2 ; exit 1 ; }
[[ -n "$2" ]] || { echo "Non-empty download root dir required" >&2 ; exit 1 ; }

[[ -n "$SECRET" ]] || { echo "SECRET env var not defined" >&2 ; exit 1 ; }
[[ -d "$SECRET" ]] || { echo "SECRET env var not a directory" >&2 ; exit 1 ; }
[[ -f "$SECRET/update-center.key" ]] || { echo "update-center.key does not exist in SECRET dir" >&2 ; exit 1 ; }
[[ -f "$SECRET/update-center.cert" ]] || { echo "update-center.cert does not exist in SECRET dir" >&2 ; exit 1 ; }

WWW_ROOT_DIR="$1"
DOWNLOAD_ROOT_DIR="$2"

set -o nounset
set -o pipefail
set -o errexit

echo "Bash: $BASH_VERSION" >&2

# platform specific behavior
UNAME="$( uname )"
if [[ $UNAME == Linux ]] ; then
  SORT="sort"
elif [[ $UNAME == Darwin ]] ; then
  SORT="gsort"
else
  echo "Unknown platform: $UNAME" >&2
  exit 1
fi

function test_which {
  command -v "$1" >/dev/null || { echo "Not on PATH: $1" >&2 ; exit 1 ; }
}

test_which curl
test_which wget
test_which $SORT
test_which jq

readarray -t RELEASES < <( curl 'https://repo.jenkins-ci.org/api/search/versions?g=org.jenkins-ci.main&a=jenkins-core&repos=releases&v=?.*.1' | jq --raw-output '.results[].version' | head -n 5 | $SORT --version-sort ) || { echo "Failed to retrieve list of releases" >&2 ; exit 1 ; }

# prepare the www workspace for execution
rm -rf "$WWW_ROOT_DIR"
mkdir -p "$WWW_ROOT_DIR"

# Generate htaccess file
"$( dirname "$0" )"/generate-htaccess.sh "${RELEASES[@]}" > "$WWW_ROOT_DIR/.htaccess"

rm -rfv generator/
rm -rfv generator.zip
wget --no-verbose -O generator.zip "https://repo.jenkins-ci.org/snapshots/org/jenkins-ci/update-center2/3.0-SNAPSHOT/update-center2-3.0-20200502.001422-22-bin.zip"
unzip generator.zip -d generator/


# Reset arguments file
echo "# one update site per line" > args.lst

function generate {
  echo "--key $SECRET/update-center.key --certificate $SECRET/update-center.cert $*" >> args.lst
}

function sanity-check {
  dir="$1"
  file="$dir/update-center.json"
  if [[ 1500000 -ge $( wc -c < "$file" ) ]] ; then
    echo "Sanity check: $file looks too small" >&2
    exit 1
  fi
}

# Generate several update sites for different segments so that plugins can
# aggressively update baseline requirements without stranding earlier users.
#
# We use LTS as a boundary of different segments, to create
# a reasonable number of segments with reasonable sizes. Plugins
# tend to pick LTS baseline as the required version, so this works well.
#
# We generate tiered update sites for the five most recent LTS baselines, which
# means admins get compatible updates offered on releases up to about one year old.
for ltsv in "${RELEASES[@]}" ; do
  v="${ltsv/%.1/}"
  # For mainline up to $v, advertising the latest core
  generate --www-dir "$WWW_ROOT_DIR/$v" --limit-plugin-core-dependency "$v.999" --write-latest-core

  # For LTS, advertising the latest LTS core
  generate --www-dir "$WWW_ROOT_DIR/stable-$v" --limit-plugin-core-dependency "$v.999" --write-latest-core --only-stable-core
done


# Experimental update center without version caps, including experimental releases.
# This is not a part of the version-based redirection rules, admins need to manually configure it.
# Generate this first, including --downloads-directory, as this includes all releases, experimental and otherwise.
generate --www-dir "$WWW_ROOT_DIR/experimental" --with-experimental --downloads-directory "$DOWNLOAD_ROOT_DIR"

# Current update site without version caps, excluding experimental releases.
# This generates -download after the experimental update site above to change the 'latest' symlinks to the latest released version.
# This also generates --download-links-directory to only visibly show real releases on index.html pages.
generate --generate-release-history --generate-plugin-versions \
    --write-latest-core --write-plugin-count \
    --www-dir "$WWW_ROOT_DIR/current" --download-links-directory "$WWW_ROOT_DIR/download" --downloads-directory "$DOWNLOAD_ROOT_DIR" --latest-links-directory "$WWW_ROOT_DIR/current/latest"

# Actually run the update center build.
# The fastjson library cannot handle a file.encoding of US-ASCII even when manually specifying the encoding at every opportunity, so set a sane default here.
java -Dfile.encoding=UTF-8 -jar generator/update-center2-*.jar --resources-dir ./resources --arguments-file ./args.lst

# Generate symlinks to global /updates directory (created by crawler)
for ltsv in "${RELEASES[@]}" ; do
  v="${ltsv/%.1/}"

  sanity-check "$WWW_ROOT_DIR/$v"
  sanity-check "$WWW_ROOT_DIR/stable-$v"
  ln -sf ../updates "$WWW_ROOT_DIR/$v/updates"
  ln -sf ../updates "$WWW_ROOT_DIR/stable-$v/updates"

  # needed for the stable/ directory (below)
  lastLTS=$v
done

sanity-check "$WWW_ROOT_DIR/experimental"
sanity-check "$WWW_ROOT_DIR/current"
ln -sf ../updates "$WWW_ROOT_DIR/experimental/updates"
ln -sf ../updates "$WWW_ROOT_DIR/current/updates"


# generate symlinks to retain compatibility with past layout and make Apache index useful
pushd "$WWW_ROOT_DIR"
  ln -s "stable-$lastLTS" stable
  for f in latest latestCore.txt plugin-documentation-urls.json release-history.json plugin-versions.json update-center.json update-center.actual.json update-center.json.html ; do
    ln -s "current/$f" .
  done
popd

# copy other static resource files
cp -av "$( dirname "$0" )/static/readme.html" "$WWW_ROOT_DIR"
