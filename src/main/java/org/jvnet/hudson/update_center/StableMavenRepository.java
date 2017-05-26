package org.jvnet.hudson.update_center;

import hudson.util.VersionNumber;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Delegating {@link MavenRepository} to limit core releases to those with LTS version numbers.
 *
 * @author Daniel Beck
 */
public class StableMavenRepository extends MavenRepository {

    public StableMavenRepository(MavenRepository base) {
        setBaseRepository(base);
    }

    @Override
    public TreeMap<VersionNumber, HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException {
        TreeMap<VersionNumber, HudsonWar> releases = base.getHudsonWar();

        releases.keySet().retainAll(Arrays.asList(releases.keySet().stream().filter(it -> it.getDigitAt(2) != -1).toArray()));

        return releases;
    }

    @Override
    protected File resolve(ArtifactInfo a, String type, String classifier) throws AbstractArtifactResolutionException {
        return base.resolve(a, type, classifier);
    }

    @Override
    public Collection<PluginHistory> listHudsonPlugins() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
        return base.listHudsonPlugins();
    }

    private boolean isStableRelease() {
        return false;
    }
}
