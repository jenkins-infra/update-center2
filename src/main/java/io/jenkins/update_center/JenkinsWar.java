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

import java.net.URL;
import java.net.MalformedURLException;

/**
 * @author Kohsuke Kawaguchi
 */
public class JenkinsWar extends MavenArtifact {
    public JenkinsWar(BaseMavenRepository repository, ArtifactCoordinates artifact) {
        super(repository, artifact);
    }

    @Override
    public URL getDownloadUrl() throws MalformedURLException {
        return new URL("http://updates.jenkins-ci.org/download/war/"+version+"/"+ getFileName());
    }

    public String getFileName() {
        String fileName;
        if (new VersionNumber(version).compareTo(HUDSON_CUT_OFF)<=0) {
            fileName = "hudson.war";
        } else {
            fileName = "jenkins.war";
        }
        return fileName;
    }

    /**
     * Hudson to Jenkins cut-over version.
     */
    public static final VersionNumber HUDSON_CUT_OFF = new VersionNumber("1.395");
}
