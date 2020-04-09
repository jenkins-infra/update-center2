package org.jvnet.hudson.update_center.wrappers;

import hudson.util.VersionNumber;
import org.jvnet.hudson.update_center.JenkinsWar;
import org.jvnet.hudson.update_center.MavenRepository;

import java.io.IOException;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Delegating {@link MavenRepositoryWrapper} to limit core releases to those with LTS version numbers.
 */
public class StableMavenRepository extends MavenRepositoryWrapper {

    public StableMavenRepository(MavenRepository base) {
        setBaseRepository(base);
    }

    @Override
    public TreeMap<VersionNumber, JenkinsWar> getHudsonWar() throws IOException {
        TreeMap<VersionNumber, JenkinsWar> releases = base.getHudsonWar();

        releases.keySet().retainAll(releases.keySet().stream().filter(it -> it.getDigitAt(2) != -1).collect(Collectors.toSet()));

        return releases;
    }
}
