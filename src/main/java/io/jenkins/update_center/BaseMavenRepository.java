package io.jenkins.update_center;

import hudson.util.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * A collection of artifacts from which we build index.
 */
public abstract class BaseMavenRepository implements MavenRepository {

    private static final Properties IGNORE = new Properties();

    static {
        try (InputStream stream = Files.newInputStream(new File(Main.resourcesDir,
                "artifact-ignores.properties").toPath())) {
            IGNORE.load(stream);
        } catch (IOException e) {
            throw new Error(e);
        }
    }
    public Collection<Plugin> listJenkinsPlugins() throws IOException {

        Map<String, Plugin> plugins =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        Set<String> excluded = new HashSet<>();
        final Collection<ArtifactCoordinates> results = listAllPlugins();

        for (ArtifactCoordinates artifactCoordinates : results) {
            if (artifactCoordinates.version.contains("SNAPSHOT"))     continue;       // ignore snapshots
            if (artifactCoordinates.version.contains("JENKINS"))      continue;       // non-public releases for addressing specific bug fixes
            // Don't add blacklisted artifacts
            if (IGNORE.containsKey(artifactCoordinates.artifactId)) {
                if (excluded.add(artifactCoordinates.artifactId)) {
                    LOGGER.log(Level.CONFIG, "Ignoring " + artifactCoordinates.artifactId + " because this artifact is blacklisted");
                }
                continue;
            }
            if (IGNORE.containsKey(artifactCoordinates.artifactId + "-" + artifactCoordinates.version)) {
                LOGGER.log(Level.CONFIG, "Ignoring " + artifactCoordinates.artifactId + ", version " + artifactCoordinates.version + " because this version is blacklisted");
                continue;
            }

            Plugin plugin = plugins.get(artifactCoordinates.artifactId);
            if (plugin == null) {
                plugin = new Plugin(artifactCoordinates.artifactId);
                plugins.put(artifactCoordinates.artifactId, plugin);
            }
            HPI hpi = new HPI(this, artifactCoordinates, plugin);

            plugin.addArtifact(hpi);
        }
        final TreeMap<String, Plugin> ret = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        ret.putAll(plugins);
        return ret.values();
    }

    /**
     * Discover all hudson.war versions. Map must be sorted by version number, descending.
     */
    public TreeMap<VersionNumber, JenkinsWar> getJenkinsWarsByVersionNumber() throws IOException {
        TreeMap<VersionNumber, JenkinsWar> r = new TreeMap<>(VersionNumber.DESCENDING);
        addWarsInGroupIdToMap(r, "org.jenkins-ci.main", null);
        addWarsInGroupIdToMap(r, "org.jvnet.hudson.main", JenkinsWar.HUDSON_CUT_OFF);
        return r;
    }

    protected abstract Set<ArtifactCoordinates> listAllJenkinsWars(String groupId) throws IOException;

    public void addWarsInGroupIdToMap(Map<VersionNumber, JenkinsWar> releases, String groupId, VersionNumber cap) throws IOException {
        final Set<ArtifactCoordinates> results = listAllJenkinsWars(groupId);
        for (ArtifactCoordinates artifactCoordinates : results) {
            if (artifactCoordinates.version.contains("SNAPSHOT"))     continue;       // ignore snapshots
            if (artifactCoordinates.version.contains("JENKINS"))      continue;       // non-public releases for addressing specific bug fixes
            if (!artifactCoordinates.artifactId.equals("jenkins-war")
                    && !artifactCoordinates.artifactId.equals("hudson-war"))  continue;      // somehow using this as a query results in 0 hits.
            if (IGNORE.containsKey(artifactCoordinates.artifactId + "-" + artifactCoordinates.version)) {
                LOGGER.log(Level.CONFIG, "Ignoring " + artifactCoordinates.artifactId + ", version " + artifactCoordinates.version + " because this version is blacklisted");
                continue;
            }
            if (cap != null && new VersionNumber(artifactCoordinates.version).compareTo(cap) > 0) continue;

            VersionNumber version = new VersionNumber(artifactCoordinates.version);
            releases.put(version, new JenkinsWar(this, artifactCoordinates));
        }
    }
}
