Jenkins update center generator
===============================

This project is primarily used to generate the jenkins-ci.org update center laying out the files to and generating
summary files about the core [aka the wars] and the plugins.

With a few modifications it could easily be used to generate your corporate update center as well.

The generator
-------------

The generator can:

* generate a static navigation site for the update center site including
    * index.html files for the core and the plugins. Each index.html points to the latest release and all known versions identified by their number
    * .htaccess files containing 302 redirects for managing latest versions of the core and the plugins
    * a txt file containing the latest version of the core
    * json files:
        * the core release history
        * the plugins release history
    * symlinks
* download the hpis and wars
* sign the json files

The generator pulls information from:

* a nexus index site
* remote and local maven repositories

Extra features
--------------

### Categorizing plugins

Jenkins groups plugins into various categories in the plugin manager.

These categories historically were labels on the plugins' wiki page with Jenkins applying localization on the raw label value, if defined.
To remove the need to scrape wiki pages in this tool, we've changed this behavior, and plugins now have the labels defined in this repository.
See the file `src/main/resources/label-definitions.properties` for the plugin/label assignments.

See https://github.com/jenkinsci/jenkins/blob/master/core/src/main/resources/hudson/model/Messages.properties (look for `UpdateCenter.PluginCategory`) for the localization overrides Jenkins applies.


### Wiki Page Override

Plugins are generally expected to provide a `<url>` to their documentation in their POM.
Historically, these URLs have been pages on the Jenkins wiki, but can point anywhere.
The wiki page override feature was primarily used when wiki pages were a requirement for plugins to be distributed by this tool at all.

This requirement no longer exists, but it may still be useful to define a documentation URL for plugins that don't do that:
Due to update center tiers that can result in older releases of a plugin being distributed, it might not be enough to have a URL in the latest release.

The file `src/main/resources/wiki-overrides.properties` defines these wiki page overrides.


### Removing plugins from distribution

The update center generator allows to specify that certain plugins, or plugin releases, should not be included in the output.

There are various reasons to need to do this, such as:

* A plugin release causes major regressions and a fix is not immediately available.
* A plugin integrates with a service that has been shut down.

Both use cases (entire plugins, or specific versions) are controlled via the file `src/main/resources/artifact-ignores.properties`.
See that file for usage examples.


### Security warnings

Since the releases 2.32.2 and 2.40, Jenkins can display security warnings about core and plugins.
These warnings are part of the update center metadata downloaded by Jenkins.
These warnings are defined in the file `src/main/resources/warnings.json`.


Usage
-----

The generator doesn't have a full usage page yet. Meanwhile you can read the code
of [the arg4js annotated Main class](https://github.com/jenkinsci/backend-update-center2/blob/master/src/main/java/org/jvnet/hudson/update_center/Main.java)

Standard www layout (without hpis and wars)
-------------------------------------------

    www/latestCore.txt
    www/release-history.json
    www/update-center.json
    www/latest/
    www/latest/.htaccess
    www/download
    www/download/plugins/
    www/download/plugins/checkstyle/
    www/download/plugins/checkstyle/index.html
    www/download/plugins/cccc/
    www/download/plugins/cccc/index.html
    [...]
    www/download/war/
    www/download/war/index.html

Running the generator
---------------------

The project various artifacts to be used on a site hosting a jenkins update center
The project produces a jar and a zip file containing all the required dependencies to run the generator.

If you want to run the generator from within your development environment,
you can try to use the appassembler plugin as described below. The exec:java plugin won't work.

    # to generate the files in a standard layout
    # warning this may take quite a bit of time, so you might want to add the -maxPlugins 1 option
    mvn package appassembler:assemble
    sh target/appassembler/bin/app -id com.example.jenkins -www www

Private Update Center
---------------------

Jenkins offical update center has lots of plugins. You could follow below steps to create your own
private update center:

1. Create your own certificate. For example:

```shell script
openssl genrsa -out rootCA/demo.key 1024
openssl req -new -x509 -days 1095 -key rootCA/demo.key \
    -out rootCA/demo.crt \
    -subj "/C=CN/ST=GD/L=SZ/O=vihoo/OU=dev/CN=demo.com/emailAddress=demo@demo.com"
```
2. Fetch plugins information then generate update.json

```shell script
echo "localization-zh-cn=" > whiteList.properties
mvn package appassembler:assemble
sh target/appassembler/bin/app -id default -www www \
    -skip-release-history -cache plugins -whitelist whiteList.properties \
    -key rootCA/demo.key -certificate rootCA/emo.crt \
    -root-certificate rootCA/demo.crt \
    -cache-server http://localhost:9090/plugins/ \
    -connectionCheckUrl http://localhost:9090/
```
3. Start your update center server (e.g. Nginx). Copy `www` into publish directory.
4. Start your Jenkins server then copy demo.crt into `$JENKINS_HOME/war/WEB-INF/update-center-rootCAs/`.
5. Change the update center url in `http://localhost:8080/pluginManager/advanced`.

Finally, you could see your favour plugins.