package org.jvnet.hudson.update_center;

import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;

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
                Date releaseDate = h.getTimestampAsDate();
                System.out.println("adding " + h.artifact.artifactId + ":" + h.version);
                Map<String,HPI> pluginsOnDate = plugins.get(releaseDate);
                if (pluginsOnDate==null) {
                    pluginsOnDate = new TreeMap<String,HPI>();
                    plugins.put(releaseDate, pluginsOnDate);
                }
                pluginsOnDate.put(p.artifactId,h);
            }
        }

        return plugins;
    }

    /**
     * Discover all hudson.war versions.
     */
    public abstract TreeMap<VersionNumber,HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException;
}
