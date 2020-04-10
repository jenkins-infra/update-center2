package org.jvnet.hudson.update_center;

import hudson.util.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Manifest;

public interface MavenRepository {
    Collection<Plugin> listHudsonPlugins() throws IOException;

    /**
     * Discover all hudson.war versions. Map must be sorted by version number, descending.
     */
    TreeMap<VersionNumber, JenkinsWar> getHudsonWar() throws IOException;

    void listWar(TreeMap<VersionNumber, JenkinsWar> r, String groupId, VersionNumber cap) throws IOException;

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
    default Map<Date,Map<String,HPI>> listHudsonPluginsByReleaseDate() throws IOException {
        Collection<Plugin> all = listHudsonPlugins();

        Map<Date, Map<String,HPI>> plugins = new TreeMap<>();

        for (Plugin plugin : all) {
            for (HPI hpi : plugin.artifacts.values()) {
                Date releaseDate = hpi.getTimestampAsDate();
                System.out.println("adding " + hpi.artifact.artifactId + ":" + hpi.version);
                Map<String, HPI> pluginsOnDate = plugins.computeIfAbsent(releaseDate, k -> new TreeMap<>());
                pluginsOnDate.put(plugin.artifactId,hpi);
            }
        }

        return plugins;
    }

    class Digests {
        public String sha1;
        public String sha256;
    }
}
