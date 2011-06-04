/*
 * The MIT License
 *
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

import net.sf.json.JSONObject;
import org.dom4j.DocumentException;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;


public interface IHPI extends IArtifact {

    boolean isAuthenticJenkinsArtifact();

    Version getRequiredJenkinsVersion() throws IOException;

    ArtifactInfo getArtifact() throws IOException;

    Pom getPom() throws IOException, DocumentException;

    Timestamp getTimestamp() throws IOException;

    Version getCompatibleSinceVersion() throws IOException;

    String getDisplayName() throws IOException;

    String getSandboxStatus() throws IOException;

    List<Dependency> getDependencies() throws IOException;

    List<Developer> getDevelopers() throws IOException;

    JSONObject toJSON(String artifactId) throws IOException;

    String getBuiltBy() throws IOException;
}
