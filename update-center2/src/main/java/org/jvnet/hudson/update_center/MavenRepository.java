package org.jvnet.hudson.update_center;

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
public abstract class MavenRepository implements IArtifactProvider {
    /**
     * Discover all plugins from this Maven repository.
     */
    public abstract Collection<PluginHistory> listHudsonPlugins() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException;

    /**
     * Discover all plugins from this Maven repository in order released, not using PluginHistory.
     */
    public Map<Date,Map<String,IHPI>> listHudsonPluginsByReleaseDate() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
        Collection<PluginHistory> all = listHudsonPlugins();

        Map<Date, Map<String,IHPI>> plugins = new TreeMap<Date, Map<String,IHPI>>();

        for (PluginHistory p : all) {
            for (IHPI h : p.artifacts.values()) {
                try {
                    Date releaseDate = h.getTimestamp().getTimestampAsDate();
                    System.out.println("adding " + h.getArtifact().artifactId + ":" + h.getVersion());
                    Map<String,IHPI> pluginsOnDate = plugins.get(releaseDate);
                    if (pluginsOnDate==null) {
                        pluginsOnDate = new TreeMap<String,IHPI>();
                        plugins.put(releaseDate, pluginsOnDate);
                    }
                    pluginsOnDate.put(p.artifactId,h);
                } catch (Exception e) {
                    // if we fail to resolve artifact, move on
                    e.printStackTrace();
                }
            }
        }

        return plugins;
    }

    /**
     * Discover all hudson.war versions.
     */
    public abstract TreeMap<VersionNumber,HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException;

    protected File resolve(ArtifactInfo a) throws AbstractArtifactResolutionException {
        return resolve(a,a.packaging);
    }

    protected abstract File resolve(ArtifactInfo a, String type) throws AbstractArtifactResolutionException;
}
