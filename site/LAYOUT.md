# Site layout

The update center image contains the following pieces.

 * Version specific update sites like `1.xxx` and `2.xxx`: update sites that advertises the versions of plugins that are supported for different version ranges of masters. See [README.md](README.md).
 * `current`: update site for the rest of masters, which advertises the latest of everything.
 * [`/download` tree](http://updates.jenkins.io/download): an URL space that covers all the versions of all the plugins released to date. Thoes URLs are then redirected to `mirrors.jenkins.io`
 * `experimental`: update site for experimental plugins that advertises alpha and beta versions, in addition to what the `current` update site has.
 * `latest`: symlink to `current/latest`
 * `latestCore.txt`: a short text file that captures the current latest version of Jenkins.
 * `pluginCount.txt`: a short text file that captures the number of plugins in the `current` update center
 * `release-history.json`: Timeline of what plugins have been released when. Useful for usage analytics.
 * `stable-x.xxx`: update sites for LTS versions of Jenkins
 * `stable`: backward compatibility with older LTS masters that explicitly configure this URL as their update center. Symlink to the latest `stable-x.xxx` update site
 * `update-center.*`: symlink to `current/update-center.*`
 * `updates`: symlink to `current/updates`

## Per site
 * `latest` tree ([example](http://updates.jenkins.io/current/latest/)) is a collection of permalinks to the latest version of every plugin in this update site.
 * `latestCore.txt` contains the latest version of the core in this update site.
 * `update-center.js`, `update-center.json`, and `update-center.json.html` contain actual update center metadata in JSON, JSONP, and HTML format respectively.

