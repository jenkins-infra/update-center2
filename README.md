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

Jenkins groups plugins into various categories in the plugin manager and on [plugins.jenkins.io](https://plugins.jenkins.io/).

To set these on your plugin, add a [topic](https://help.github.com/en/github/administering-a-repository/classifying-your-repository-with-topics) to your github repository, with the prefix `jenkins-`.
For example, if you want the `matrix` label, you need to add `jenkins-matrix` to your repo's [topics](https://help.github.com/en/github/administering-a-repository/classifying-your-repository-with-topics) (also sometimes refered to as labels).

Only labels in the [whitelist file](./src/main/resources/allowed-labels.properties) will make it into the jenkins infrastructure.

See https://github.com/jenkinsci/jenkins/blob/master/core/src/main/resources/hudson/model/Messages.properties (look for `UpdateCenter.PluginCategory`) for the localization overrides applied by Jenkins.

Older plugins may have additional labels defined in the file [`label-definitions.properties`](https://github.com/jenkins-infra/update-center2/edit/master/src/main/resources/label-definitions.properties) in this repository, this approach is deprecated.

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


### Filtering Java versions

The `-javaVersion [version]` CLI argument can be used to filter plugins based on their minimum Java version requirement.
By default such filtering happens based on the `Minimum-Java-Version` manifest entry provided in Plugin HPIs starting from
[Maven HPI Plugin 3.0](https://github.com/jenkinsci/maven-hpi-plugin#30-2018-12-05)
and [Plugin POM 3.29](https://github.com/jenkinsci/plugin-pom/blob/master/CHANGELOG.md#329).

Plugin HPIs without `Minimum-Java-Version` will be accepted by default.
If you want to create an update center for old Java, use the `-cap` option to set the filter for core dependencies in plugins (``).

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
