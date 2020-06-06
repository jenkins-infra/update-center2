#!/usr/bin/env bash

# Usage: SECRET=dirname ./site/generate.sh "./www2" "./download"
[[ $# -gt 1 ]] || { echo "Usage: $0 <www root dir> <download root dir> [extra update-center2.jar args ...]" >&2 ; exit 1 ; }
[[ -n "$1" ]] || { echo "Non-empty www root dir required" >&2 ; exit 1 ; }
[[ -n "$2" ]] || { echo "Non-empty download root dir required" >&2 ; exit 1 ; }

[[ -n "$SECRET" ]] || { echo "SECRET env var not defined" >&2 ; exit 1 ; }
[[ -d "$SECRET" ]] || { echo "SECRET env var not a directory" >&2 ; exit 1 ; }
[[ -f "$SECRET/update-center.key" ]] || { echo "update-center.key does not exist in SECRET dir" >&2 ; exit 1 ; }
[[ -f "$SECRET/update-center.cert" ]] || { echo "update-center.cert does not exist in SECRET dir" >&2 ; exit 1 ; }

WWW_ROOT_DIR="$1"
DOWNLOAD_ROOT_DIR="$2"
shift
shift
EXTRA_ARGS="$*"

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

TOOLS=( curl wget "$SORT" jq )

for tool in "${TOOLS[@]}" ; do
  test_which "$tool"
done

# We have associated resource files, so determine script directory -- greadlink is GNU coreutils readlink on Mac OS with Homebrew
SIMPLE_SCRIPT_DIR="$( dirname "$0" )"
MAIN_DIR="$( readlink -f "$SIMPLE_SCRIPT_DIR/../" 2>/dev/null || greadlink -f "$SIMPLE_SCRIPT_DIR/../" )" || { echo "Failed to determine script directory using (g)readlink -f" >&2 ; exit 1 ; }

echo "Main directory: $MAIN_DIR"
mkdir -p "$MAIN_DIR"/tmp/

readarray -t RELEASES < <( curl --silent --fail 'https://repo.jenkins-ci.org/api/search/versions?g=org.jenkins-ci.main&a=jenkins-core&repos=releases&v=?.*.1' | jq --raw-output '.results[].version' | head -n 5 | $SORT --version-sort ) || { echo "Failed to retrieve list of releases" >&2 ; exit 1 ; }

# prepare the www workspace for execution
rm -rf "$WWW_ROOT_DIR"
mkdir -p "$WWW_ROOT_DIR"

# Generate htaccess file
"$( dirname "$0" )"/generate-htaccess.sh "${RELEASES[@]}" > "$WWW_ROOT_DIR/.htaccess"

rm -rf "$MAIN_DIR"/tmp/generator/
rm -rf "$MAIN_DIR"/tmp/generator.zip
wget --no-verbose -O "$MAIN_DIR"/tmp/generator.zip "https://repo.jenkins-ci.org/releases/org/jenkins-ci/update-center2/3.2.1/update-center2-3.2.1-bin.zip"
unzip -q "$MAIN_DIR"/tmp/generator.zip -d "$MAIN_DIR"/tmp/generator/


# Reset arguments file
echo "# one update site per line" > "$MAIN_DIR"/tmp/args.lst

function generate {
  echo "--key $SECRET/update-center.key --certificate $SECRET/update-center.cert --root-certificate $( dirname "$0" )/../resources/certificates/jenkins-update-center-root-ca.crt $EXTRA_ARGS $*" >> "$MAIN_DIR"/tmp/args.lst
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
  generate --limit-plugin-core-dependency "$v.999" --write-latest-core --latest-links-directory "$WWW_ROOT_DIR/$v/latest" --www-dir "$WWW_ROOT_DIR/$v"

  # For LTS, advertising the latest LTS core
  generate --limit-plugin-core-dependency "$v.999" --write-latest-core --latest-links-directory "$WWW_ROOT_DIR/stable-$v/latest" --www-dir "$WWW_ROOT_DIR/stable-$v" --only-stable-core
done


# Experimental update center without version caps, including experimental releases.
# This is not a part of the version-based redirection rules, admins need to manually configure it.
# Generate this first, including --downloads-directory, as this includes all releases, experimental and otherwise.
generate --www-dir "$WWW_ROOT_DIR/experimental" --with-experimental --downloads-directory "$DOWNLOAD_ROOT_DIR" --latest-links-directory "$WWW_ROOT_DIR/experimental/latest"

# Current update site without version caps, excluding experimental releases.
# This generates -download after the experimental update site above to change the 'latest' symlinks to the latest released version.
# This also generates --download-links-directory to only visibly show real releases on index.html pages.
generate --generate-release-history --generate-plugin-versions --generate-plugin-documentation-urls \
    --write-latest-core --write-plugin-count \
    --www-dir "$WWW_ROOT_DIR/current" --download-links-directory "$WWW_ROOT_DIR/download" --downloads-directory "$DOWNLOAD_ROOT_DIR" --latest-links-directory "$WWW_ROOT_DIR/current/latest"

# Actually run the update center build.
# The fastjson library cannot handle a file.encoding of US-ASCII even when manually specifying the encoding at every opportunity, so set a sane default here.
java -Dfile.encoding=UTF-8 -jar "$MAIN_DIR"/tmp/generator/update-center2-*.jar --resources-dir "$MAIN_DIR"/resources --arguments-file "$MAIN_DIR"/tmp/args.lst

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
