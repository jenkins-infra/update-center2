package org.jvnet.hudson.update_center.wrappers;

import org.jvnet.hudson.update_center.MavenRepository;
import org.jvnet.hudson.update_center.Plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@link MavenRepository} that limits the # of plugins that it reports.
 *
 * This is primary for monkey-testing with subset of data.
 */
public class TruncatedMavenRepository extends MavenRepositoryWrapper {
    private final int cap;

    public TruncatedMavenRepository(MavenRepository base, int cap) {
        setBaseRepository(base);
        this.cap = cap;
    }

    @Override
    public Collection<Plugin> listHudsonPlugins() throws IOException {
        List<Plugin> result = new ArrayList<Plugin>(base.listHudsonPlugins());
        return result.subList(0, Math.min(cap,result.size()));
    }
}
