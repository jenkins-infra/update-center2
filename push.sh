#!/bin/bash -ex

# obtain index
wget --verbose --timestamping http://download.java.net/maven/2/.index/nexus-maven-repository-index.zip
rm -rf index || true
mkdir index
pushd index
  unzip -o ../nexus-maven-repository-index.zip
popd

# prepare the www workspace for execution
rm -rf www2 || true
svn co -N https://www.dev.java.net/svn/hudson/trunk/www2
svn up www2/latest www2/download

# obtain key and certificate for signing
scp "hudson@hudson.sfbay.sun.com:~/server/keys/official-update-center.*" .

# generate all the metadata
mvn -e clean install
java -jar target/update-center2-*-bin*/update-center2-*.jar \
  -id default \
  -connectionCheckUrl http://www.google.com/ \
  -www ./www2 \
  -dlc ./dlc.sun.com \
  -key official-update-center.key \
  -certificate official-update-center.crt

# delete keys
rm official-update-center.*

# push to dlc.sun.com
rsync -avz --delete ./dlc.sun.com/ sjavatx@trx2.sun.com:/export/nfs/dlc/hudson/downloads

# push references to dlc.sun.com to the website
pushd www2/download
  svn add $(svn status | grep "^?" | cut -d " " -f2-) .
popd
svn commit -m "pushing new web contents" www2
