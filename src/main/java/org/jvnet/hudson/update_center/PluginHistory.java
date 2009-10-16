package org.jvnet.hudson.update_center;

import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Information about Hudson plugin and its release history, discovered from Maven repository.
 */
public final class PluginHistory {
    /**
     * ArtifactID equals short name.
     */
    final String artifactId;

    /**
     * All discovered versions, by the numbers.
     */
    final TreeMap<VersionNumber,HPI> artifacts = new TreeMap<VersionNumber, HPI>();

    final Set<String> groupId = new TreeSet<String>();

    public PluginHistory(String shortName) {
        this.artifactId = shortName;
    }
}
