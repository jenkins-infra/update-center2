package io.jenkins.update_center;

import hudson.util.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

public interface MavenRepository {
    Logger LOGGER = Logger.getLogger(MavenRepository.class.getName());

    Collection<Plugin> listJenkinsPlugins() throws IOException;

    /**
     * Discover all jenkins.war / hudson.war versions. Map must be sorted by version number, descending.
     */
    TreeMap<VersionNumber, JenkinsWar> getJenkinsWarsByVersionNumber() throws IOException;

    void addWarsInGroupIdToMap(Map<VersionNumber, JenkinsWar> r, String groupId, VersionNumber cap) throws IOException;

    Collection<ArtifactCoordinates> listAllPlugins() throws IOException;

    Digests getDigests(MavenArtifact artifact) throws IOException;

    Manifest getManifest(MavenArtifact artifact) throws IOException;

    InputStream getZipFileEntry(MavenArtifact artifact, String path) throws IOException;

    File resolve(ArtifactCoordinates artifact) throws IOException;

    default File resolve(ArtifactCoordinates a, String packaging, String classifier) throws IOException {
        return resolve(new ArtifactCoordinates(a.groupId, a.artifactId, a.version, packaging, classifier));
    }

    /**
     * Discover all plugins from this Maven repository in order released, not using PluginHistory.
     */
    default Map<Date,Map<String,HPI>> listPluginsByReleaseDate() throws IOException {
        Collection<Plugin> all = listJenkinsPlugins();

        Map<Date, Map<String,HPI>> plugins = new TreeMap<>();
        // TODO this is weird, we only include one release per plugin and day, and it's random which one if there are multiple

        for (Plugin plugin : all) {
            for (HPI hpi : plugin.getArtifacts().values()) {
                Date releaseDate = hpi.getTimestampAsDate();
                LOGGER.log(Level.FINE, "adding " + hpi.artifact.artifactId + ":" + hpi.version);
                Map<String, HPI> pluginsOnDate = plugins.computeIfAbsent(releaseDate, k -> new TreeMap<>());
                pluginsOnDate.put(plugin.getArtifactId(),hpi);
            }
        }

        return plugins;
    }

    class Digests {
        public String sha1;
        public String sha256;
    }
}
