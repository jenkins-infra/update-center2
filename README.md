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

Extra features:

* allow to blacklist plugins to ignore. See src/main/resources/artifact-ignores.properties
* temporary override of the plugins wiki page. src/main/resources/wiki-overrides.properties

The generator pulls information from:

* a nexus index site
* remote and local maven repositories
* confluence

The generator doesn't have a full usage page yet. Meanwhile you can read the code
of [the arg4js annotated Main class](backend-update-center2/blob/master/src/main/java/org/jvnet/hudson/update_center/Main.java "the Main class")

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
    mvn compile
    mvn exec:java -Dexec.args="-id com.example.jenkins -www www"

Arguments
---------
* -id
	* Required
	* Uniquely identifies this update center.
	* We recommend you use a dot-separated name like "com.sun.wts.jenkins".
		* This text is from the original source code of backend-update-center2.
		* But it seems that the name containing dots does not works correctly (caused NPE in PluginManager.class)
	* This value is not exposed to users, but instead internally used by Jenkins.
	* Used as "id" field in update-center.json
* -repository
	* Alternate repository for plugins.
	* If not specified, http://repo.jenkins-ci.org/public/ is used.
	* Default: null
* -repositoryName
	* Name of repository. This is a value for n opition of nexus-indexer-cli.
	* If not specified, "public" is used.
	* Default: null
* -remoteIndex
	* Nexus index file in repository.
	* If not specified, .index/nexus-maven-repository-index.gz is used.
	* Default: null
* -directLink
	* Use the links into the maven repository as the plugin URL.
* -hpiDirectory
	* Build update center files from plugin files (hpi and jpi) contained in this directory.
	* When specified this option...
		* Specified directory must be placed to the path specified with -repository.
		* Nexus index file is not needed.
		* repositoryName and remoteIndex is ignored.
* -includeSnapshots
	* Include releases as well as snapshots found in hpiDirectory.
* -nowiki
	* Does not refer http://wiki.jenkins-ci.org/
	* Information in pom files are trusted.
* -download
	* Specify a directory to build download server layout.
	* Packages will be deployed into the directory.
	* It is refered as "http://updates.jenkins-ci.org/download/", or ${repository}/download used with -repository.
	* Default: null
* -www
	* Built jenkins-ci.org layout
	* Default: null
* -r
	* release history JSON file
	* When -www is specified, $(www)/release-history.json is used.
	* Default: release-history.json
* -h
	* htaccess file
	* /dev/null indicates not to write(works also in Windows).
	* When -www is specified, $(www)/latest/.htaccess is used.
	* Default: .htaccess
* -o
	* json file(update-center.json)
	* When -www is specified, $(www)/update-center.json is used.
	* Default: output.json
* -index.html
	* Update the version number of the latest jenkins.war in jenkins-ci.org/index.html
	* When -www is specified, $(www)/index.html is used.
	* Default: null
* -latestCore.txt
	* Update the version number of the latest jenkins.war in latestCore.txt
	* When -www is specified, $(www)/latestCore.txt is used. 
	* Default: null
* -maxPlugins
	* For testing purposes.
	* Limit the number of plugins managed to the specified number.
	* Default: null
* -connectionCheckUrl
	* Specify an URL of the 'always up' server for performing connection check.
	* Used as "connectionCheckUrl" field in update-center.json
	* Default: null
* -pretty
	* Pretty-print the json
	* Default: false
* -cap
	* Cap the version number and only report data that's compatible with

Typical Usage
-------------

### When you use nexus index

```
mvn exec:java -Dexec.args="-id UPDATE-CENTER-ID -h /dev/null -o PATH_TO_WRITE_update-center.json -repository http://YOURSERVER/PATH_TO_REPOSITORY/ -remoteIndex REL_PATH_TO_nexus-maven-repository-index.gz -repositoryName 'REPOSITORY_NAME' -directLink -nowiki -key PATH_TO_KEY_FILE -certificate PATH_TO_CERTIFICATE_FILE -root-certificate PATH_TO_CERTIFICATE_FILE"
```

### When you do not use nexus index

```
mvn exec:java -Dexec.args="-id UPDATE-CENTER-ID -h /dev/null -o PATH_TO_WRITE_update-center.json -repository http://YOURSERVER/PATH_TO_REPOSITORY/ -hpiDirectory PATH_TO_LOCAL_REPOSITORY -nowiki -key PATH_TO_KEY_FILE -certificate PATH_TO_CERTIFICATE_FILE -root-certificate PATH_TO_CERTIFICATE_FILE"
```

