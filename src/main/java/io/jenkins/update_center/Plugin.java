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
package io.jenkins.update_center;

import hudson.util.VersionNumber;

import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Information about a Jenkins plugin and its release history, discovered from Maven repository.
 *
 * This includes 'canonical' information about a plugin such as its URL or labels that is version independent.
 * TODO the above is aspirational
 */
public final class Plugin {
    private static final Logger LOGGER = Logger.getLogger(Plugin.class.getName());
    private final String artifactId;

    private final TreeMap<VersionNumber,HPI> artifacts = new TreeMap<>(VersionNumber.DESCENDING);

    private final Set<VersionNumber> duplicateVersions = new TreeSet<>();

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
     * Adding a plugin release carefully.
     *
     * <p>
     *     If another release exists with an equivalent version number (1.0 vs. 1.0.0), remove both from distribution due to nondeterminism.
     * </p>
     *
     * @param hpi the plugin HPI
     */
    public void addArtifact(HPI hpi) {
        VersionNumber v;
        try {
            v = new VersionNumber(hpi.version);
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Failed to parse version number " + hpi.version + " for " + hpi);
            return;
        }

        HPI existing = artifacts.get(v);

        if (duplicateVersions.contains(v)) {
            LOGGER.log(Level.INFO, "Found another duplicate artifact " + hpi.artifact.getGav() + " considered identical due to non-determinism. Neither will be published.");
            // This is the third or more ambiguous version for this
            return;
        }

        if (existing == null) {
            artifacts.put(v, hpi);
        } else {
            LOGGER.log(Level.INFO, "Found a duplicate artifact " + hpi.artifact.getGav() + " considered identical to " + existing.artifact.getGav() + " due to non-determinism. Neither will be published.");
            artifacts.remove(v);
            duplicateVersions.add(v);
        }
    }

    /**
     * ArtifactID equals short name.
     *
     * @return the artifact ID (short name)
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * All discovered versions, by the version numbers, newer versions first.
     *
     * @return a map from version number to HPI
     */
    public TreeMap<VersionNumber, HPI> getArtifacts() {
        return artifacts;
    }
}
