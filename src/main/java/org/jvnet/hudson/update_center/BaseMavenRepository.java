package org.jvnet.hudson.update_center;

import hudson.util.VersionNumber;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import static org.jvnet.hudson.update_center.JenkinsWar.HUDSON_CUT_OFF;

/**
 * A collection of artifacts from which we build index.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class BaseMavenRepository implements MavenRepository {

    protected static final Properties IGNORE = new Properties();

    static {
        try {
            IGNORE.load(Files.newInputStream(new File(Main.resourcesDir, "artifact-ignores.properties").toPath()));
        } catch (IOException e) {
            throw new Error(e);
        }
    }
    public Collection<Plugin> listHudsonPlugins() throws IOException {

        Map<String, Plugin> plugins =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        Set<String> excluded = new HashSet<>();
        final Collection<ArtifactCoordinates> results = listAllPlugins();
        ARTIFACTS: for (ArtifactCoordinates artifactCoordinates : results) {
            if (artifactCoordinates.version.contains("SNAPSHOT"))     continue;       // ignore snapshots
            if (artifactCoordinates.version.contains("JENKINS"))      continue;       // non-public releases for addressing specific bug fixes
            // Don't add blacklisted artifacts
            if (IGNORE.containsKey(artifactCoordinates.artifactId)) {
                if (excluded.add(artifactCoordinates.artifactId)) {
                    System.out.println("=> Ignoring " + artifactCoordinates.artifactId + " because this artifact is blacklisted");
                }
                continue;
            }
            if (IGNORE.containsKey(artifactCoordinates.artifactId + "-" + artifactCoordinates.version)) {
                System.out.println("=> Ignoring " + artifactCoordinates.artifactId + ", version " + artifactCoordinates.version + " because this version is blacklisted");
                continue;
            }

            Plugin plugin = plugins.get(artifactCoordinates.artifactId);
            if (plugin == null) {
                plugin = new Plugin(artifactCoordinates.artifactId);
                plugins.put(artifactCoordinates.artifactId, plugin);
            }
            HPI hpi = new HPI(this, artifactCoordinates, plugin);

            for (PluginFilter pluginFilter : pluginFilters) {
                if (pluginFilter.shouldIgnore(hpi)) {
                    continue ARTIFACTS;
                }
            }

            plugin.addArtifact(hpi);
            plugin.groupId.add(artifactCoordinates.groupId);
        }
        return plugins.values();
    }

    /**
     * Adds a plugin filter.
     * @param filter Filter to be added.
     */
    public void addPluginFilter(@Nonnull PluginFilter filter) {
        pluginFilters.add(filter);
    }

    public void resetPluginFilters() {
        this.pluginFilters.clear();
    }

    private List<PluginFilter> pluginFilters = new ArrayList<>();

    /**
     * Discover all hudson.war versions. Map must be sorted by version number, descending.
     */
    public TreeMap<VersionNumber, JenkinsWar> getHudsonWar() throws IOException {
        TreeMap<VersionNumber, JenkinsWar> r = new TreeMap<>(VersionNumber.DESCENDING);
        listWar(r, "org.jenkins-ci.main", null);
        listWar(r, "org.jvnet.hudson.main", HUDSON_CUT_OFF);
        return r;
    }

    protected abstract Set<ArtifactCoordinates> listAllJenkinsWars(String groupId) throws IOException;

    public JenkinsWar createHudsonWarArtifact(ArtifactCoordinates a) {
        return new JenkinsWar(this,a);
    }

    @Override
    public void listWar(TreeMap<VersionNumber, JenkinsWar> r, String groupId, VersionNumber cap) throws IOException {
        final Set<ArtifactCoordinates> results = listAllJenkinsWars(groupId);
        for (ArtifactCoordinates a : results) {
            if (a.version.contains("SNAPSHOT"))     continue;       // ignore snapshots
            if (a.version.contains("JENKINS"))      continue;       // non-public releases for addressing specific bug fixes
            if (!a.artifactId.equals("jenkins-war")
                    && !a.artifactId.equals("hudson-war"))  continue;      // somehow using this as a query results in 0 hits.
            if (a.classifier!=null)  continue;          // just pick up the main war
            if (IGNORE.containsKey(a.artifactId + "-" + a.version)) {
                System.out.println("=> Ignoring " + a.artifactId + ", version " + a.version + " because this version is blacklisted");
                continue;
            }
            if (cap!=null && new VersionNumber(a.version).compareTo(cap)>0) continue;

            VersionNumber v = new VersionNumber(a.version);
            r.put(v, createHudsonWarArtifact(a));
        }
    }

    /**
     * find the HPI for the specified plugin
     * @return the found HPI or null
     */
    public HPI findPlugin(String groupId, String artifactId, String version) throws IOException {
        Collection<Plugin> all = listHudsonPlugins();

        for (Plugin p : all) {
            for (HPI h : p.artifacts.values()) {
                if (h.isEqualsTo(groupId, artifactId, version))
                  return h;
            }
        }
        return null;
    }
}
