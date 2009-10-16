#!/bin/bash -ex

# generate all the metadata
mvn -e clean install exec:java

# push to dlc.sun.com
rsync -avz --delete ./dlc.sun.com/ sjavatx@trx2.sun.com:/export/nfs/dlc/hudson/downloads

# push references to dlc.sun.com to the website
rm -rf www2 || true
svn co -N https://www.dev.java.net/svn/hudson/trunk/www2
svn up -N www2/latest
cp output.json www2/update-center.json
cp .htaccess www2/latest/.htaccess
svn commit -m "pushing new update center site" www2
