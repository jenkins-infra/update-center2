package org.jvnet.hudson.update_center;

import hudson.util.VersionNumber;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

/**
 * A collection of artifacts from which we build index.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class MavenRepository {

    protected MavenRepository base;

    /**
     * Discover all plugins from this Maven repository.
     */
    public abstract Collection<PluginHistory> listHudsonPlugins() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException;

    /**
     * Discover all plugins from this Maven repository in order released, not using PluginHistory.
     */
    public Map<Date,Map<String,HPI>> listHudsonPluginsByReleaseDate() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
        Collection<PluginHistory> all = listHudsonPlugins();

        Map<Date, Map<String,HPI>> plugins = new TreeMap<Date, Map<String,HPI>>();

        for (PluginHistory p : all) {
            for (HPI h : p.artifacts.values()) {
                try {
                    Date releaseDate = h.getTimestampAsDate();
                    System.out.println("adding " + h.artifact.artifactId + ":" + h.version);
                    Map<String,HPI> pluginsOnDate = plugins.get(releaseDate);
                    if (pluginsOnDate==null) {
                        pluginsOnDate = new TreeMap<String,HPI>();
                        plugins.put(releaseDate, pluginsOnDate);
                    }
                    pluginsOnDate.put(p.artifactId,h);
                } catch (IOException e) {
                    // if we fail to resolve artifact, move on
                    e.printStackTrace();
                }
            }
        }

        return plugins;
    }

    /**
     * find the HPI for the specified plugin
     * @return the found HPI or null
     */
    public HPI findPlugin(String groupId, String artifactId, String version) throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
        Collection<PluginHistory> all = listHudsonPlugins();

        for (PluginHistory p : all) {
            for (HPI h : p.artifacts.values()) {
                if (h.isEqualsTo(groupId, artifactId, version))
                  return h;
            }
        }
        return null;
    }


    /**
     * Discover all hudson.war versions. Map must be sorted by version number, descending.
     */
    public abstract TreeMap<VersionNumber,HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException;

    protected File resolve(ArtifactInfo a) throws AbstractArtifactResolutionException {
        return resolve(a,a.packaging, null);
    }

    protected abstract File resolve(ArtifactInfo a, String type, String classifier) throws AbstractArtifactResolutionException;

    /** Should be called by subclasses who are decorating an existing MavenRepository instance. */
    protected void setBaseRepository(MavenRepository base) {
        this.base = base;
    }

    /** @return The base instance that this repository is wrapping; or {@code null} if this is the base instance. */
    public MavenRepository getBaseRepository() {
        return base;
    }

}
