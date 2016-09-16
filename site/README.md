# OSS Update Center Site Architecture
This script generate the code and data behind https://updates.jenkins-ci.org/

The service this website provides is as follows.

 1. Jenkins will hit well-known URLs hard-coded into Jenkins binaries with
    the Jenkins version number attached as a query string, like
    http://updates.jenkins-ci.org/update-center.json.html?version=1.345

 1. We use the version number to redirect the traffic to the right update site,
    among all the ones that we generate.

 1. HTTP traffic will be directed to mirrors through http://mirror.jenkins-ci.org/
    HTTPS traffic will be served locally because we don't have any HTTPS mirrors.
    In this way, Jenkins running in HTTPS will not see the insecure content warning
    in the browser.

## Multiple update sites for different version ranges

Update center metadata can contain only one version per a plugin.
Because newer versions of the same plugin may depend on newer version of Jenkins,
if we just serve one update center for every Jenkins out there, older versions of
Jenkins will see plugin versions that do not work with them, making it impossible to install
the said plugin. This is unfortunate because some younger versions of the plugin might have
worked with that Jenkins core. This creates a disincentive for plugin developers
to move to the new base version, and slows down the pace in which it adopts new core features.

So in this "v3" UC, we generate several update centers targeted for different
version ranges. Consider the following example:



              1.532      1.554        1.565
     -----------+-----------+-----------+--------------->  1.590
                 \           \           \
                  \           \           \
                   --------->  --------->  ----------->


The version range is defined based on LTS branch points. We pick up 3 or 4 recent
LTS branch points to create 6 or 8 ranges.

 * version in the range (,1.532] will see the UC that advertises 1.590 as the core release,
   and they will only see plugins that are compatible with 1.532 or earlier.

 * next, version range in (1.532, 1.532.*], which is basically 1.532.x LTS, will see
   the UC that advertises 1.532.x as the core release, and only see plugins that are
   compatible with 1.532.* or earlier.

 * next, version in the range (1.532, 1.554] will see 1.590 as the core, and
   only see plugins compatible with 1.554 or earlier.

 * next, version range is (1,554, 1.554.*] and you get the idea

 * finally, the catch-all update center advertises 1.590 core release and latest plugins
   regardless of their required core versions. This applies to (1.565,*]

## Redirection logic

[generate.sh](generate.sh) is run by [a CI job](https://trusted.ci.jenkins.io/job/update_center/)
and generates all the different sites as static files, and deploys the directory into Apache.

A part of this is [.htaccess](static/.htaccess) that uses `mod_rewrite` to
redirect inbound requests to the right version specific website.

## Layout
See [a separate doc](LAYOUT.md) for the layout of the generated update site.