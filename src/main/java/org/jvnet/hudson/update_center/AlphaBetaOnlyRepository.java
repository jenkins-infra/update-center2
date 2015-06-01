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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/**
 * Filter down to alpha/beta releases of plugins (or the negation of it.)
 *
 * @author Kohsuke Kawaguchi
 */
public class AlphaBetaOnlyRepository extends MavenRepository {

    /**
     * If true, negate the logic and only find non-alpha/beta releases.
     */
    private boolean negative;

    public AlphaBetaOnlyRepository(MavenRepository base, boolean negative) {
        setBaseRepository(base);
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
                if (isAlphaOrBeta(e.getValue())^negative)
                    continue;
                itr.remove();
            }

            if (h.artifacts.isEmpty())
                jtr.remove();
        }

        return r;
    }

    private boolean isAlphaOrBeta(HPI v) {
        if (HISTORICALLY_BETA_ONLY.contains(v.artifact.artifactId))
            return false;
        return v.isAlphaOrBeta();
    }


    @Override
    public File resolve(ArtifactInfo a, String type, String classifier) throws AbstractArtifactResolutionException {
        return base.resolve(a, type, classifier);
    }

    /**
     * Historically these plugins have never released non-experimental versions,
     * so we always count them as releases even though they have alpha/beta in the version number
     */
    private static final Set<String> HISTORICALLY_BETA_ONLY = new HashSet<String>(Arrays.asList(
            "BlazeMeterJenkinsPlugin",
            "heroku-jenkins-plugin",
            "deployit-plugin"
            ));
}
