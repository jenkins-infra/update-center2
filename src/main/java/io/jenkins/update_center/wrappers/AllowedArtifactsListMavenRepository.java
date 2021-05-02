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

public class AllowedArtifactsListMavenRepository extends MavenRepositoryWrapper {
    private static final Logger LOGGER = Logger.getLogger(AllowedArtifactsListMavenRepository.class.getName());

    private final Properties allowedArtifactsList;

    public AllowedArtifactsListMavenRepository(Properties allowedArtifactsList) {
        this.allowedArtifactsList = allowedArtifactsList;
    }

    @Override
    public Collection<Plugin> listJenkinsPlugins() throws IOException {
        final Collection<Plugin> plugins = base.listJenkinsPlugins();
        for (Iterator<Plugin> pluginIterator = plugins.iterator(); pluginIterator.hasNext(); ) {
            Plugin plugin = pluginIterator.next();
            final String listEntry = allowedArtifactsList.getProperty(plugin.getArtifactId());

            if (listEntry == null) {
                pluginIterator.remove();
                continue;
            }

            if (listEntry.equals("*")) {
                continue; // entire artifactId allowed
            }

            final List<String> allowedVersions = Arrays.stream(listEntry.split("\\s+")).map(String::trim).collect(Collectors.toList());

            for (Iterator<Map.Entry<VersionNumber, HPI>> versionIterator = plugin.getArtifacts().entrySet().iterator(); versionIterator.hasNext(); ) {
                Map.Entry<VersionNumber, HPI> entry = versionIterator.next();
                HPI hpi = entry.getValue();
                if (!allowedVersions.contains(hpi.version)) {
                    versionIterator.remove();
                }
            }
            if (plugin.getArtifacts().isEmpty()) {
                LOGGER.log(Level.WARNING, "Individual versions of a plugin are allowed, but none of them matched: " + plugin.getArtifactId() + " versions: " + listEntry);
                pluginIterator.remove();
            }
        }
        return plugins;
    }

    @Override
    public TreeMap<VersionNumber, JenkinsWar> getJenkinsWarsByVersionNumber() throws IOException {
        final String listEntry = allowedArtifactsList.getProperty("jenkins-core");

        if (listEntry == null) {
            return new TreeMap<>(); // TODO fix return type so it's only a Map
        }

        TreeMap<VersionNumber, JenkinsWar> releases = base.getJenkinsWarsByVersionNumber();

        if (listEntry.equals("*")) {
            return releases;
        }

        final List<String> allowedVersions = Arrays.stream(listEntry.split("\\s+")).map(String::trim).collect(Collectors.toList());

        releases.keySet().retainAll(releases.keySet().stream().filter(it -> allowedVersions.contains(it.toString())).collect(Collectors.toSet()));

        return releases;
    }
}
