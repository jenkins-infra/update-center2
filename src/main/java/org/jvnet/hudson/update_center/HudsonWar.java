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

import hudson.util.VersionNumber;
import org.sonatype.nexus.index.ArtifactInfo;

import java.net.URL;
import java.net.MalformedURLException;

/**
 * @author Kohsuke Kawaguchi
 */
public class HudsonWar extends MavenArtifact {
    public HudsonWar(MavenRepository repository, ArtifactInfo artifact) {
        super(repository, artifact);
    }

    @Override
    public URL getURL() throws MalformedURLException {
        return getURL(null);
    }

    @Override
    public URL getURL(String connectionCheckUrl) throws MalformedURLException {
        return new URL((connectionCheckUrl!=null ? connectionCheckUrl : "http://updates.jenkins-ci.org") + "/download/war/"+version+"/"+ getFileName());
    }

    /**
     * Returns the Maven artifact representing the corresponding core jar file.
     */
    public MavenArtifact getCoreArtifact() {
        return new MavenArtifact(repository,new ArtifactInfo(
                artifact.repository,
                artifact.groupId,
                artifact.artifactId.replace("war","core"),
                artifact.version,
                artifact.classifier
        ));
    }

    public String getFileName() {
        String fileName;
        if (new VersionNumber(version).compareTo(MavenRepositoryImpl.CUT_OFF)<=0)
            fileName = "hudson.war";
        else
            fileName = "jenkins.war";
        return fileName;
    }
}
