#!/bin/bash -ex

# obtain index
wget --timestamping http://download.java.net/maven/2/.index/nexus-maven-repository-index.zip
mkdir index || true
pushd index
  unzip ../nexus-maven-repository-index.zip
popd

# prepare the www workspace for execution
rm -rf www2 || true
svn co -N https://www.dev.java.net/svn/hudson/trunk/www2
svn up -N www2/latest www2/download

# generate all the metadata
mvn -e clean install exec:java

# push to dlc.sun.com
#rsync -avz --delete ./dlc.sun.com/ sjavatx@trx2.sun.com:/export/nfs/dlc/hudson/downloads

# push references to dlc.sun.com to the website
pushd www2/download
  svn add $(svn status | grep "^?" | cut -d " " -f2-) .
popd
#svn commit -m "pushing new web contents" www2
