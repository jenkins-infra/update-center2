/*
 * The MIT License
 *
 * Copyright (c) 2004-2020, Sun Microsystems, Inc. and other contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.update_center;

import hudson.util.VersionNumber;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Information about a Jenkins plugin and its release history, discovered from Maven repository.
 *
 * This includes 'canonical' information about a plugin such as its URL or labels that is version independent.
 * TODO the above is aspirational
 */
public final class Plugin {
    public static final Logger LOGGER = Logger.getLogger(Plugin.class.getName());
    private final String artifactId;

    private final TreeMap<VersionNumber,HPI> artifacts = new TreeMap<>(VersionNumber.DESCENDING);

    public Plugin(String shortName) {
        this.artifactId = shortName;
    }

    public HPI getLatest() {
        return artifacts.get(artifacts.firstKey());
    }

    public HPI getFirst() {
        return artifacts.get(artifacts.lastKey());
    }

    /**
     * Adding a plugin carefully.
     *
     * <p>
     * If a plugin is renamed to jenkins-ci.org, we want to stop picking up newer changes elsewhere.
     */
    public void addArtifact(HPI hpi) {
        VersionNumber v;
        try {
            v = new VersionNumber(hpi.version);
        } catch (NumberFormatException e) {
            System.out.println("Failed to parse version number "+hpi.version+" for "+hpi);
            return;
        }

        HPI existing = artifacts.get(v);
        if (existing == null) {
            artifacts.put(v, hpi);
        } else if(PRIORITY.compare(existing, hpi)<=0) {
            LOGGER.log(Level.INFO, "Found a higher priority artifact " + hpi.artifact.getGav() + " replacing " + existing.artifact.getGav());
            artifacts.put(v, hpi);
        } else {
            LOGGER.log(Level.INFO, "Found a duplicate artifact " + hpi.artifact.getGav() + " but will continue to use existing " + existing.artifact.getGav());
        }

        // if we have any authentic Jenkins artifact, we don't want to pick up non-authentic versions that are newer than that
        // drop entries so that this constraint is satisfied
        Map.Entry<VersionNumber,HPI> tippingPoint = findYoungestJenkinsArtifact();
        if (tippingPoint!=null) {
            if (artifacts.headMap(tippingPoint.getKey()).entrySet().removeIf(e -> !e.getValue().isAuthenticJenkinsArtifactWithLog())) {
                LOGGER.log(Level.INFO, () -> "Removed versions from " + getArtifactId() + " because of #isAuthenticJenkinsArtifact(). Versions left: " + artifacts.keySet().stream().map(Objects::toString).collect(Collectors.joining(", ")));
            }
        }
    }

    /**
     * Returns the youngest version of the artifact that's authentic Jenkins artifact.
     */
    @Deprecated
    private Map.Entry<VersionNumber,HPI> findYoungestJenkinsArtifact() {
        for (Map.Entry<VersionNumber,HPI> e : artifacts.descendingMap().entrySet()) {
            if (e.getValue().isAuthenticJenkinsArtifact())
                return e;
        }
        return null;
    }

    private static final Comparator<HPI> PRIORITY = new Comparator<HPI>() {
        public int compare(HPI a, HPI b) {
            return priority(a)-priority(b);
        }

        private int priority(HPI h) {
            return h.isAuthenticJenkinsArtifact() ? 1 : 0;
        }
    };

    /**
     * ArtifactID equals short name.
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * All discovered versions, by the version numbers, newer versions first.
     */
    public TreeMap<VersionNumber, HPI> getArtifacts() {
        return artifacts;
    }
}
