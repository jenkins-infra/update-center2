package io.jenkins.update_center.wrappers;

import hudson.util.VersionNumber;
import io.jenkins.update_center.JenkinsWar;
import io.jenkins.update_center.HPI;
import io.jenkins.update_center.Plugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WhitelistMavenRepository extends MavenRepositoryWrapper {
    private static final Logger LOGGER = Logger.getLogger(WhitelistMavenRepository.class.getName());

    private final Properties whitelist;

    public WhitelistMavenRepository(Properties whitelist) {
        this.whitelist = whitelist;
    }

    @Override
    public Collection<Plugin> listJenkinsPlugins() throws IOException {
        final Collection<Plugin> plugins = base.listJenkinsPlugins();
        for (Iterator<Plugin> pluginIterator = plugins.iterator(); pluginIterator.hasNext(); ) {
            Plugin plugin = pluginIterator.next();
            final String whitelistEntry = whitelist.getProperty(plugin.getArtifactId());

            if (whitelistEntry == null) {
                pluginIterator.remove();
                continue;
            }

            if (whitelistEntry.equals("*")) {
                continue; // entire artifactId allowed
            }

            final List<String> allowedVersions = Arrays.stream(whitelistEntry.split("\\s+")).map(String::trim).collect(Collectors.toList());

            for (Iterator<Map.Entry<VersionNumber, HPI>> versionIterator = plugin.getArtifacts().entrySet().iterator(); versionIterator.hasNext(); ) {
                Map.Entry<VersionNumber, HPI> entry = versionIterator.next();
                HPI hpi = entry.getValue();
                if (!allowedVersions.contains(hpi.version)) {
                    versionIterator.remove();
                }
            }
            if (plugin.getArtifacts().isEmpty()) {
                LOGGER.log(Level.WARNING, "Individual versions of a plugin are whitelisted but none of them matched: " + plugin.getArtifactId() + " versions: " + whitelistEntry);
                pluginIterator.remove();
            }
        }
        return plugins;
    }

    @Override
    public TreeMap<VersionNumber, JenkinsWar> getJenkinsWarsByVersionNumber() throws IOException {
        final String whitelistEntry = whitelist.getProperty("jenkins-core");

        if (whitelistEntry == null) {
            return new TreeMap<>(); // TODO fix return type so it's only a Map
        }

        TreeMap<VersionNumber, JenkinsWar> releases = base.getJenkinsWarsByVersionNumber();

        if (whitelistEntry.equals("*")) {
            return releases;
        }

        final List<String> allowedVersions = Arrays.stream(whitelistEntry.split("\\s+")).map(String::trim).collect(Collectors.toList());

        releases.keySet().retainAll(releases.keySet().stream().filter(it -> allowedVersions.contains(it.toString())).collect(Collectors.toSet()));

        return releases;
    }
}
