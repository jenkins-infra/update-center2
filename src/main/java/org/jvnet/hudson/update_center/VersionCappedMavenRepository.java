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
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Delegating {@link MavenRepository} to limit the data to the subset compatible with the specific version.
 *
 * @author Kohsuke Kawaguchi
 */
public class VersionCappedMavenRepository extends MavenRepository {

    /**
     * Version number to cap. We only report plugins that are compatible with this core version.
     */
    private final VersionNumber capPlugin;

    /**
     * Version number to cap core. We only report core versions as high as this.
     */
    private final VersionNumber capCore;

    public VersionCappedMavenRepository(MavenRepository base, VersionNumber capPlugin, VersionNumber capCore) {
        setBaseRepository(base);
        this.capPlugin = capPlugin;
        this.capCore = capCore;
    }

    public VersionCappedMavenRepository(MavenRepository base, VersionNumber cap) {
        this(base,cap,cap);
    }

    @Override
    public TreeMap<VersionNumber, HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException {
        return new TreeMap<VersionNumber, HudsonWar>(base.getHudsonWar().tailMap(capCore,true));
    }

    @Override
    public Collection<PluginHistory> listHudsonPlugins() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
        Collection<PluginHistory> r = base.listHudsonPlugins();

        for (Iterator<PluginHistory> jtr = r.iterator(); jtr.hasNext();) {
            PluginHistory h = jtr.next();


            Map<VersionNumber, HPI> versionNumberHPIMap = new TreeMap<>(VersionNumber.DESCENDING);

            for (Iterator<Entry<VersionNumber, HPI>> itr = h.artifacts.entrySet().iterator(); itr.hasNext();) {
                Entry<VersionNumber, HPI> e =  itr.next();
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
                    if (v.compareTo(capPlugin)<=0) {
                        versionNumberHPIMap.put(e.getKey(), e.getValue());
                        if (versionNumberHPIMap.size() >= 2) {
                            break;
                        }
                        continue;
                    }
                } catch (IOException x) {
                    x.printStackTrace();
                }
            }

            h.artifacts.entrySet().retainAll(versionNumberHPIMap.entrySet());

            if (h.artifacts.isEmpty())
                jtr.remove();
        }

        return r;
    }


    @Override
    public File resolve(ArtifactInfo a, String type, String classifier) throws AbstractArtifactResolutionException {
        return base.resolve(a, type, classifier);
    }
}
