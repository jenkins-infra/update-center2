package org.jvnet.hudson.update_center;

import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Delegating {@link MavenRepository} to limit the data to the subset compatible with the specific version.
 *
 * @author Kohsuke Kawaguchi
 */
public class VersionCappedMavenRepository extends MavenRepository {
    private final MavenRepository base;

    /**
     * Version number to cap. We only report the portion of data that's compatible with this version.
     */
    private final VersionNumber cap;

    public VersionCappedMavenRepository(MavenRepository base, VersionNumber cap) {
        this.base = base;
        this.cap = cap;
    }

    @Override
    public TreeMap<VersionNumber, HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException {
        return new TreeMap<VersionNumber, HudsonWar>(base.getHudsonWar().tailMap(cap,true));
    }

    @Override
    public Collection<PluginHistory> listHudsonPlugins() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
        Collection<PluginHistory> r = base.listHudsonPlugins();
        for (Iterator<PluginHistory> jtr = r.iterator(); jtr.hasNext();) {
            PluginHistory h = jtr.next();

            for (Iterator<Entry<VersionNumber, IHPI>> itr = h.artifacts.entrySet().iterator(); itr.hasNext();) {
                Entry<VersionNumber, IHPI> e =  itr.next();
                try {
                    VersionNumber v = e.getValue().getRequiredJenkinsVersion().getNumber();
                    if (v.compareTo(cap)<=0)
                        continue;
                } catch (Exception x) {
                    x.printStackTrace();
                }
                itr.remove();
            }

            if (h.artifacts.isEmpty())
                jtr.remove();
        }

        return r;
    }

    @Override
    public File resolve(ArtifactInfo a, String type) throws AbstractArtifactResolutionException {
        return base.resolve(a, type);
    }
}
