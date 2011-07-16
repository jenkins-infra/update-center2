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
of [the arg4js annotated Main class](blob/master/src/main/java/org/jvnet/hudson/update_center/Main.java "the Main class")

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
    # warning this may take quite a bit of time!
    mvn package appassembler:assemble
    sh target/appassembler/bin/app -id com.example.jenkins -www www
