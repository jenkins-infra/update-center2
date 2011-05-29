/*
 * The MIT License
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

import com.sun.tools.javac.resources.version;
import net.sf.json.JSONObject;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.dom4j.DocumentException;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.Attributes;

public class MavenArtifactHPI extends HPI
{
     MavenArtifact artifact;

     public MavenArtifactHPI(MavenRepository repository, PluginHistory history, ArtifactInfo artifact) throws AbstractArtifactResolutionException {
         super(history);
         this.artifact = new MavenArtifact(repository, artifact);
     }

    /**
     * Download a plugin via more intuitive URL. This also helps us track download counts.
     */
    public URL getURL() throws MalformedURLException {
        return new URL("http://updates.jenkins-ci.org/download/plugins/"+artifact.artifact.artifactId+"/"+ artifact.version +"/"+artifact.artifact.artifactId+".hpi");
    }

    public ArtifactInfo getArtifact() throws IOException {
        return artifact.getArtifact();
    }

    public Pom getPom() throws IOException, DocumentException {
        return artifact.getPom();
    }

    public Timestamp getTimestamp() throws IOException {
        return artifact.getTimestamp();
    }

    public JSONObject toJSON(String artifactId) throws IOException {
        return artifact.toJSON(artifactId);
    }

    public Version getVersion() {
        return artifact.getVersion();
    }

    @Override
    public Attributes getManifestAttributes() throws IOException {
        return artifact.getManifestAttributes();
    }

    /**
     * Does this artifact come from the jenkins community?
     */
    public boolean isAuthenticJenkinsArtifact() {
        // mayebe it should be startWith("org.jenkins")?
        return artifact.artifact.groupId.contains("jenkins");
    }
}
