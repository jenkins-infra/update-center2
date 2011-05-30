/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
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
    public final String artifactId;

    /**
     * All discovered versions, by the version numbers, newer versions first.
     */
    public final TreeMap<VersionNumber,IHPI> artifacts = new TreeMap<VersionNumber, IHPI>(VersionNumber.DESCENDING);

    final Set<String> groupId = new TreeSet<String>();

    public PluginHistory(String shortName) {
        this.artifactId = shortName;
    }

    public IHPI latest() {
        return artifacts.get(artifacts.firstKey());
    }

    /**
     * Adding a plugin carefully.
     *
     * <p>
     * If a plugin is renamed to jenkins-ci.org, we want to stop picking up newer changes elsewhere.
     */
    public void addArtifact(IHPI hpi) {
        VersionNumber v;
        try {
            v = hpi.getVersion().getNumber();
        } catch (NumberFormatException e) {
            System.out.println("Failed to parse version number for "+hpi);
            return;
        }

        IHPI existing = artifacts.get(v);
        if (existing==null || PRIORITY.compare(existing,hpi)<=0)
            artifacts.put(v,hpi);


        // if we have any authentic Jenkins artifact, we don't want to pick up non-authentic versions that are newer than that
        // drop entries so that this constraint is satisfied
        Map.Entry<VersionNumber,IHPI> tippingPoint = findYoungestJenkinsArtifact();
        if (tippingPoint!=null) {
            Iterator<Map.Entry<VersionNumber,IHPI>> itr = artifacts.headMap(tippingPoint.getKey()).entrySet().iterator();
            while (itr.hasNext()) {
                Entry<VersionNumber, IHPI> e = itr.next();
                if (!e.getValue().isAuthenticJenkinsArtifact())
                    itr.remove();
            }
        }
    }

    /**
     * Returns the youngest version of the artifact that's authentic Jenkins artifact.
     */
    public Map.Entry<VersionNumber,IHPI> findYoungestJenkinsArtifact() {
        for (Map.Entry<VersionNumber,IHPI> e : artifacts.descendingMap().entrySet()) {
            if (e.getValue().isAuthenticJenkinsArtifact())
                return e;
        }
        return null;
    }

    private static final Comparator<IHPI> PRIORITY = new Comparator<IHPI>() {
        public int compare(IHPI a, IHPI b) {
            return priority(a)-priority(b);
        }

        private int priority(IHPI h) {
            return h.isAuthenticJenkinsArtifact() ? 1 : 0;
        }
    };
}
