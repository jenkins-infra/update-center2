package io.jenkins.update_center.wrappers;

import hudson.util.VersionNumber;
import io.jenkins.update_center.JenkinsWar;
import io.jenkins.update_center.BaseMavenRepository;
import io.jenkins.update_center.HPI;
import io.jenkins.update_center.Plugin;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * Delegating {@link BaseMavenRepository} to limit the data to the subset compatible with the specific version.
 */
public class VersionCappedMavenRepository extends MavenRepositoryWrapper {

    /**
     * Version number to cap. We only report plugins that are compatible with this core version.
     */
    @CheckForNull
    private final VersionNumber capPlugin;

    /**
     * Version number to cap core. We only report core versions as high as this.
     */
    @CheckForNull
    private final VersionNumber capCore;

    public VersionCappedMavenRepository(@CheckForNull VersionNumber capPlugin, @CheckForNull VersionNumber capCore) {
        this.capPlugin = capPlugin;
        this.capCore = capCore;
    }

    @Override
    public TreeMap<VersionNumber, JenkinsWar> getJenkinsWarsByVersionNumber() throws IOException {
        final TreeMap<VersionNumber, JenkinsWar> allWars = base.getJenkinsWarsByVersionNumber();
        if (capCore == null) {
            return allWars;
        }
        return new TreeMap<>(allWars.tailMap(capCore,true));
    }

    @Override
    public Collection<Plugin> listJenkinsPlugins() throws IOException {
        Collection<Plugin> r = base.listJenkinsPlugins();

        for (Iterator<Plugin> jtr = r.iterator(); jtr.hasNext();) {
            Plugin h = jtr.next();


            Map<VersionNumber, HPI> versionNumberHPIMap = new TreeMap<>(VersionNumber.DESCENDING);

            for (Entry<VersionNumber, HPI> e : h.getArtifacts().entrySet()) {
                if (capPlugin == null) {
                    // no cap
                    versionNumberHPIMap.put(e.getKey(), e.getValue());
                    if (versionNumberHPIMap.size() >= 2) {
                        break;
                    }
                    continue;
                }
                try {
                    VersionNumber v = new VersionNumber(e.getValue().getRequiredJenkinsVersion());
                    if (v.compareTo(capPlugin) <= 0) {
                        versionNumberHPIMap.put(e.getKey(), e.getValue());
                        if (versionNumberHPIMap.size() >= 2) {
                            break;
                        }
                    }
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, "Failed to filter plugin for plugin: " + h.getArtifactId(), x);
                }
            }

            h.getArtifacts().entrySet().retainAll(versionNumberHPIMap.entrySet());

            if (h.getArtifacts().isEmpty())
                jtr.remove();
        }

        return r;
    }
}
