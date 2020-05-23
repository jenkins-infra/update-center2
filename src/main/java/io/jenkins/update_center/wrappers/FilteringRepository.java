package io.jenkins.update_center.wrappers;

import hudson.util.VersionNumber;
import io.jenkins.update_center.PluginFilter;
import io.jenkins.update_center.HPI;
import io.jenkins.update_center.Plugin;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FilteringRepository extends MavenRepositoryWrapper {

    /**
     * Adds a plugin filter.
     * @param filter Filter to be added.
     */
    private void addPluginFilter(@Nonnull PluginFilter filter) {
        pluginFilters.add(filter);
    }

    private List<PluginFilter> pluginFilters = new ArrayList<>();

    @Override
    public Collection<Plugin> listJenkinsPlugins() throws IOException {
        Collection<Plugin> r = base.listJenkinsPlugins();
        for (Iterator<Plugin> jtr = r.iterator(); jtr.hasNext();) {
            Plugin h = jtr.next();

            for (Iterator<Map.Entry<VersionNumber, HPI>> itr = h.getArtifacts().entrySet().iterator(); itr.hasNext();) {
                Map.Entry<VersionNumber, HPI> e = itr.next();
                for (PluginFilter filter : pluginFilters) {
                    if (filter.shouldIgnore(e.getValue())) {
                        itr.remove();
                    }
                }
            }

            if (h.getArtifacts().isEmpty())
                jtr.remove();
        }

        return r;
    }

    public FilteringRepository withPluginFilter(PluginFilter pluginFilter) {
        addPluginFilter(pluginFilter);
        return this;
    }
}
