package org.jvnet.hudson.update_center;

import hudson.util.VersionNumber;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

/**
 * {@link MavenRepository} that limits the # of plugins that it reports.
 *
 * This is primary for monkey-testing with subset of data.
 *
 * @author Kohsuke Kawaguchi
 */
public class TruncatedMavenRepository extends MavenRepository {
    private final int cap;

    public TruncatedMavenRepository(MavenRepository base, int cap) {
        setBaseRepository(base);
        this.cap = cap;
    }

    @Override
    public TreeMap<VersionNumber, HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException {
        return base.getHudsonWar();
    }

    @Override
    public File resolve(ArtifactInfo a, String type, String classifier) throws AbstractArtifactResolutionException {
        return base.resolve(a, type, classifier);
    }

    @Override
    public Collection<PluginHistory> listHudsonPlugins() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
        List<PluginHistory> result = new ArrayList<PluginHistory>(base.listHudsonPlugins());
        return result.subList(0, Math.min(cap,result.size()));
    }
}
