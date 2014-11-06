#!/bin/bash -ex
# used from ci.jenkins-ci.org to actually generate the production OSS update center

umask

# prepare the www workspace for execution
rm -rf www2 || true
mkdir www2

mvn -e clean install

function generate() {
    java -jar target/update-center2-*-bin*/update-center2-*.jar \
      -id default \
      -no-experimental \
      -connectionCheckUrl http://www.google.com/ \
      -key $SECRET/update-center.key \
      -certificate $SECRET/update-center.cert \
      "$@"
}

RULE="$PWD/www2/rules.php"
echo '<?php $rules = array( ' > "$RULE"

# generate several update centers for different segments
# so that plugins can aggressively update baseline requirements
# without strnding earlier users.
#
# we use LTS as a boundary of different segments, to create
# a reasonable number of segments with reasonable sizes. Plugins
# tend to pick LTS baseline as the required version, so this works well.
#
# Looking at statistics like http://stats.jenkins-ci.org/jenkins-stats/svg/201409-jenkins.svg,
# I think three or four should be sufficient
# TODO: add back 1.532 1.545 once I sufficiently test this
for v in 1.565 1.580; do
    # for mainline up to $v, which advertises the latest core
    generate -www ./www2/$v -cap $v.999 -capCore 999

    # for LTS
    generate -www ./www2/stable-$v -cap $v.999
    lastLTS=$v

    echo "'$v' => '$v', " >> "$RULE"
    echo "'$v.999' => 'stable-$v', " >> "$RULE"
done

# for the latest without any cap
# also use this to generae https://updates.jenkins-ci.org/download layout, since this generator run
# will capture every plugin and every core
generate -www ./www2/current -www-download ./www2/download -pluginCount.txt ./www2/pluginCount.txt

echo "); ?>" >> "$RULE"

# generate symlinks to retain compatibility with past layout and make Apache index useful
pushd www2
  ln -s stable-$lastLTS stable
  for f in latest latestCore.txt release-history.json update-center.json update-center.json.html; do
    ln -s current/$f .
  done

  # copy other static resource files
  rsync -avz "../site/static/" ./
popd


# push generated index to the production servers
chmod -R a+r www2
rsync -avz www2/ www-data@updates.jenkins-ci.org:/var/www/updates2.jenkins-ci.org/