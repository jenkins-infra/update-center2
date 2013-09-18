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
import java.util.Iterator;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Filter down to alpha/beta releases of plugins (or the negation of it.)
 *
 * @author Kohsuke Kawaguchi
 */
public class AlphaBetaOnlyRepository extends MavenRepository {
    private final MavenRepository base;

    /**
     * If true, negate the logic and only find non-alpha/beta releases.
     */
    private boolean negative;

    public AlphaBetaOnlyRepository(MavenRepository base, boolean negative) {
        this.base = base;
        this.negative = negative;
    }

    /**
     * Core is pass-through.
     */
    @Override
    public TreeMap<VersionNumber, HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException {
        return base.getHudsonWar();
    }

    @Override
    public Collection<PluginHistory> listHudsonPlugins() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
        Collection<PluginHistory> r = base.listHudsonPlugins();
        for (Iterator<PluginHistory> jtr = r.iterator(); jtr.hasNext();) {
            PluginHistory h = jtr.next();

            for (Iterator<Entry<VersionNumber, HPI>> itr = h.artifacts.entrySet().iterator(); itr.hasNext();) {
                Entry<VersionNumber, HPI> e =  itr.next();
                if (isAlphaOrBeta(e.getKey())^negative)
                    continue;
                itr.remove();
            }

            if (h.artifacts.isEmpty())
                jtr.remove();
        }

        return r;
    }

    private boolean isAlphaOrBeta(VersionNumber v) {
        String s = v.toString().toLowerCase(Locale.ENGLISH);
        return s.contains("alpha") || s.contains("beta");
    }


    @Override
    public File resolve(ArtifactInfo a, String type, String classifier) throws AbstractArtifactResolutionException {
        return base.resolve(a, type, classifier);
    }
}
