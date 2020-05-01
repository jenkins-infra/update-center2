package io.jenkins.update_center.wrappers;

import io.jenkins.update_center.MavenRepository;
import io.jenkins.update_center.Plugin;

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

    public TruncatedMavenRepository(int cap) {
        this.cap = cap;
    }

    @Override
    public Collection<Plugin> listJenkinsPlugins() throws IOException {
        List<Plugin> result = new ArrayList<>(base.listJenkinsPlugins());
        return result.subList(0, Math.min(cap,result.size()));
    }
}
