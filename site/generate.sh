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

version=3.14
coordinates=org/jenkins-ci/update-center2/$version/update-center2-$version-bin.zip

if [[ -f "$MAIN_DIR"/tmp/generator-$version.zip ]] ; then
  echo "tmp/generator-$version.zip already exists, skipping download"
else
  echo "tmp/generator-$version.zip does not exist, downloading ..."
  rm -rf "$MAIN_DIR"/tmp/generator*.zip
  wget --no-verbose -O "$MAIN_DIR"/tmp/generator-$version.zip "https://repo.jenkins-ci.org/releases/$coordinates"
fi

rm -rf "$MAIN_DIR"/tmp/generator/
unzip -q "$MAIN_DIR"/tmp/generator-$version.zip -d "$MAIN_DIR"/tmp/generator/

function execute {
  # The fastjson library cannot handle a file.encoding of US-ASCII even when manually specifying the encoding at every opportunity, so set a sane default here.
  # Define the default for certificate expiration and recent release age threshold, but if environment variables of the same name are defined, use their values.
  java -DCERTIFICATE_MINIMUM_VALID_DAYS="${CERTIFICATE_MINIMUM_VALID_DAYS:-30}" -DRECENT_RELEASES_MAX_AGE_HOURS="${RECENT_RELEASES_MAX_AGE_HOURS:-3}" -Dfile.encoding=UTF-8 -jar "$MAIN_DIR"/tmp/generator/update-center2-*.jar "$@"
  # To use a locally built snapshot, use the following command instead:
  # java -Dfile.encoding=UTF-8 -jar target/update-center2-*-bin/update-center2-*.jar "$@"
}

execute --dynamic-tier-list-file tmp/tiers.json
readarray -t WEEKLY_RELEASES < <( jq --raw-output '.weeklyCores[]' tmp/tiers.json ) || { echo "Failed to determine weekly tier list" >&2 ; exit 1 ; }
readarray -t STABLE_RELEASES < <( jq --raw-output '.stableCores[]' tmp/tiers.json ) || { echo "Failed to determine stable tier list" >&2 ; exit 1 ; }

# Workaround for https://github.com/jenkinsci/docker/issues/954 -- still generate fixed tier update sites
readarray -t RELEASES < <( curl --silent --fail 'https://repo.jenkins-ci.org/api/search/versions?g=org.jenkins-ci.main&a=jenkins-core&repos=releases&v=?.*.1' | jq --raw-output '.results[].version' | head -n 5 | $SORT --version-sort ) || { echo "Failed to retrieve list of recent LTS releases" >&2 ; exit 1 ; }

# prepare the www workspace for execution
rm -rf "$WWW_ROOT_DIR"
mkdir -p "$WWW_ROOT_DIR"

# Generate htaccess file
"$( dirname "$0" )"/generate-htaccess.sh "${WEEKLY_RELEASES[@]}" "${STABLE_RELEASES[@]}" > "$WWW_ROOT_DIR/.htaccess"

# Reset arguments file
echo "# one update site per line" > "$MAIN_DIR"/tmp/args.lst

function generate {
  echo "--key $SECRET/update-center.key --certificate $SECRET/update-center.cert --root-certificate $( dirname "$0" )/../resources/certificates/jenkins-update-center-root-ca-2.crt --index-template-url https://www.jenkins.io/templates/downloads/ $EXTRA_ARGS $*" >> "$MAIN_DIR"/tmp/args.lst
}

function sanity-check {
  dir="$1"
  file="$dir/update-center.json"
  if [[ 1500000 -ge $( wc -c < "$file" ) ]] ; then
    echo "Sanity check: $file looks too small" >&2
    exit 1
  else
    echo "Sanity check: $file looks OK" >&2
  fi
}

# Generate tiered update sites for different segments so that plugins can
# aggressively update baseline requirements without stranding earlier users.
#
# We generate tiered update sites for all core releases newer than
# about 13 months that are actually used as plugin dependencies.
# This supports updating Jenkins (core) once a year while getting offered compatible plugin updates.
for version in "${WEEKLY_RELEASES[@]}" ; do
  # For mainline, advertising the latest core
  generate --limit-plugin-core-dependency "$version" --write-latest-core --latest-links-directory "$WWW_ROOT_DIR/dynamic-$version/latest" --www-dir "$WWW_ROOT_DIR/dynamic-$version"
done

for version in "${STABLE_RELEASES[@]}" ; do
  # For LTS, advertising the latest LTS core
  generate --limit-plugin-core-dependency "$version" --write-latest-core --latest-links-directory "$WWW_ROOT_DIR/dynamic-stable-$version/latest" --www-dir "$WWW_ROOT_DIR/dynamic-stable-$version" --only-stable-core
done

# Experimental update center without version caps, including experimental releases.
# This is not a part of the version-based redirection rules, admins need to manually configure it.
# Generate this first, including --downloads-directory, as this includes all releases, experimental and otherwise.
generate --www-dir "$WWW_ROOT_DIR/experimental" --generate-recent-releases --with-experimental --downloads-directory "$DOWNLOAD_ROOT_DIR" --latest-links-directory "$WWW_ROOT_DIR/experimental/latest"

# Current update site without version caps, excluding experimental releases.
# This generates -download after the experimental update site above to change the 'latest' symlinks to the latest released version.
# This also generates --download-links-directory to only visibly show real releases on index.html pages.
generate --generate-release-history --generate-recent-releases --generate-plugin-versions --generate-plugin-documentation-urls \
    --write-latest-core --write-plugin-count \
    --www-dir "$WWW_ROOT_DIR/current" --download-links-directory "$WWW_ROOT_DIR/download" --downloads-directory "$DOWNLOAD_ROOT_DIR" --latest-links-directory "$WWW_ROOT_DIR/current/latest"

# Actually run the update center build.
execute --resources-dir "$MAIN_DIR"/resources --arguments-file "$MAIN_DIR"/tmp/args.lst

# Generate symlinks to global /updates directory (created by crawler)
for version in "${WEEKLY_RELEASES[@]}" ; do
  sanity-check "$WWW_ROOT_DIR/dynamic-$version"
  ln -sf ../updates "$WWW_ROOT_DIR/dynamic-$version/updates"
done

for version in "${STABLE_RELEASES[@]}" ; do
  sanity-check "$WWW_ROOT_DIR/dynamic-stable-$version"
  ln -sf ../updates "$WWW_ROOT_DIR/dynamic-stable-$version/updates"

  # needed for the stable/ directory (below)
  lastLTS=dynamic-stable-$version
done

sanity-check "$WWW_ROOT_DIR/experimental"
sanity-check "$WWW_ROOT_DIR/current"
ln -sf ../updates "$WWW_ROOT_DIR/experimental/updates"
ln -sf ../updates "$WWW_ROOT_DIR/current/updates"


# generate symlinks to retain compatibility with past layout and make Apache index useful
pushd "$WWW_ROOT_DIR"
  ln -s "$lastLTS" stable
  for f in latest latestCore.txt plugin-documentation-urls.json release-history.json plugin-versions.json update-center.json update-center.actual.json update-center.json.html ; do
    ln -s "current/$f" .
  done
popd

# copy other static resource files
echo '{}' > "$WWW_ROOT_DIR/uctest.json"
wget -q --convert-links -O "$WWW_ROOT_DIR/index.html" --convert-links https://www.jenkins.io/templates/updates/index.html
cp -av "tmp/tiers.json" "$WWW_ROOT_DIR/tiers.json"
