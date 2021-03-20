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
     *
     * @return a map from version number to war
     * @throws IOException when an exception contacting the artifacts repository occurs
     */
    TreeMap<VersionNumber, JenkinsWar> getJenkinsWarsByVersionNumber() throws IOException;

    void addWarsInGroupIdToMap(Map<VersionNumber, JenkinsWar> r, String groupId, VersionNumber cap) throws IOException;

    Collection<ArtifactCoordinates> listAllPlugins() throws IOException;

    ArtifactMetadata getMetadata(MavenArtifact artifact) throws IOException;

    Manifest getManifest(MavenArtifact artifact) throws IOException;

    InputStream getZipFileEntry(MavenArtifact artifact, String path) throws IOException;

    File resolve(ArtifactCoordinates artifact) throws IOException;

    default File resolve(ArtifactCoordinates a, String packaging, String classifier) throws IOException {
        return resolve(new ArtifactCoordinates(a.groupId, a.artifactId, a.version, packaging));
    }

    /**
     * Discover all plugins from this Maven repository in order released, not using PluginHistory.
     * Only the latest release for a given release on a given day will be included.
     *
     * @return Nested maps, mapping release date (day only, at midnight), then plugin ID to plugin release.
     *
     * @throws IOException when an exception contacting the artifacts repository occurs
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

    class ArtifactMetadata {
        public String sha1;
        public String sha256;

        /**
         * Epoch seconds (Unix timestamp)
         *
         */
        public long timestamp;
        /**
         * File size in bytes
         *
         */
        public long size;
    }
}
