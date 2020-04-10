package org.jvnet.hudson.update_center.wrappers;

import hudson.util.VersionNumber;
import org.jvnet.hudson.update_center.HPI;
import org.jvnet.hudson.update_center.MavenRepository;
import org.jvnet.hudson.update_center.Plugin;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * Filter down to alpha/beta releases of plugins (or the negation of it.)
 *
 * @author Kohsuke Kawaguchi
 */
public class AlphaBetaOnlyRepository extends MavenRepositoryWrapper {

    /**
     * If true, negate the logic and only find non-alpha/beta releases.
     */
    private boolean negative;

    public AlphaBetaOnlyRepository(MavenRepository base, boolean negative) {
        setBaseRepository(base);
        this.negative = negative;
    }

    @Override
    public Collection<Plugin> listHudsonPlugins() throws IOException {
        Collection<Plugin> r = base.listHudsonPlugins();
        for (Iterator<Plugin> jtr = r.iterator(); jtr.hasNext();) {
            Plugin h = jtr.next();

            for (Iterator<Entry<VersionNumber, HPI>> itr = h.getArtifacts().entrySet().iterator(); itr.hasNext();) {
                Entry<VersionNumber, HPI> e =  itr.next();
                if (e.getValue().isAlphaOrBeta()^negative)
                    continue;
                itr.remove();
            }

            if (h.getArtifacts().isEmpty())
                jtr.remove();
        }

        return r;
    }
}
