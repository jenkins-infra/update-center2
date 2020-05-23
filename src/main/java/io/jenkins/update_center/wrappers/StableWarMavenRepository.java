package io.jenkins.update_center.wrappers;

import hudson.util.VersionNumber;
import io.jenkins.update_center.JenkinsWar;

import java.io.IOException;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Delegating {@link MavenRepositoryWrapper} to limit core releases to those with LTS version numbers.
 */
public class StableWarMavenRepository extends MavenRepositoryWrapper {

    @Override
    public TreeMap<VersionNumber, JenkinsWar> getJenkinsWarsByVersionNumber() throws IOException {
        TreeMap<VersionNumber, JenkinsWar> releases = base.getJenkinsWarsByVersionNumber();

        releases.keySet().retainAll(releases.keySet().stream().filter(it -> it.getDigitAt(2) != -1).collect(Collectors.toSet()));

        return releases;
    }
}
