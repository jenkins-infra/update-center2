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

import net.sf.json.JSONObject;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * A particular version of a plugin and its metadata.
 *
 * @author Kohsuke Kawaguchi
 */
public class HPI extends MavenArtifact {
    /**
     * Which of the lineage did this come from?
     */
    public final PluginHistory history;

    public HPI(MavenRepositoryImpl repository, PluginHistory history, ArtifactInfo artifact) throws AbstractArtifactResolutionException {
        super(repository, artifact);
        this.history = history;
    }

    /**
     * Download a plugin via more intuitive URL. This also helps us track download counts.
     */
    public URL getURL() throws MalformedURLException {
        return new URL("http://updates.hudson-labs.org/download/plugins/"+artifact.artifactId+"/"+version+"/"+artifact.artifactId+".hpi");
    }

    /**
     * Who built this release?
     */
    public String getBuiltBy() throws IOException {
        return getManifestAttributes().getValue("Built-By");
    }

    public String getRequiredHudsonVersion() throws IOException {
        return getManifestAttributes().getValue("Hudson-Version");
    }

    public String getCompatibleSinceVersion() throws IOException {
        return getManifestAttributes().getValue("Compatible-Since-Version");
    }

    public String getDisplayName() throws IOException {
        return getManifestAttributes().getValue("Long-Name");
    }

    public String getSandboxStatus() throws IOException {
        return getManifestAttributes().getValue("Sandbox-Status");
    }

    public List<Dependency> getDependencies() throws IOException {
        String deps = getManifestAttributes().getValue("Plugin-Dependencies");
        if(deps==null)  return Collections.emptyList();

        List<Dependency> r = new ArrayList<Dependency>();
        for(String token : deps.split(","))
            r.add(new Dependency(token));
        return r;
    }

    public List<Developer> getDevelopers() throws IOException {
        String devs = getManifestAttributes().getValue("Plugin-Developers");
        if (devs == null || devs.trim().length()==0) return Collections.emptyList();

        List<Developer> r = new ArrayList<Developer>();
        for (String token : devs.split(",")) {
            try {
                r.add(new Developer(token));
            } catch (ParseException e) {
                // ignore and move on
                System.err.println(e);
            }
        }
        return r;
    }

    public static class Dependency {
        public final String name;
        public final String version;
        public final boolean optional;

        Dependency(String token) {
            this.optional = token.endsWith(OPTIONAL);
            if(optional)
                token = token.substring(0, token.length()-OPTIONAL.length());

            String[] pieces = token.split(":");
            name = pieces[0];
            version = pieces[1];
        }

        private static final String OPTIONAL = ";resolution:=optional";

        public JSONObject toJSON() {
            JSONObject o = new JSONObject();
            o.put("name",name);
            o.put("version",version);
            o.put("optional",optional);
            return o;
        }
    }

    public static class Developer {
        public final String name;
        public final String developerId;
        public final String email;

        Developer(String token) throws ParseException {
            String[] pieces = token.split(":",-1);
            if (pieces.length!=3)
                throw new ParseException("Unexpected developer name: "+token,0);
            name = pieces[0];
            developerId = pieces[1];
            email = pieces[2];
        }

        public JSONObject toJSON() {
            JSONObject o = new JSONObject();
            if (!name.equals("") && !name.equals(" "))
                o.put("name", name);
            if (!developerId.equals(""))
                o.put("developerId", developerId);
            if (!email.equals("") && !email.equals(" "))
                o.put("email", email);

            if (!o.isEmpty()) {
                return o;
            } else {
                return null;
            }
        }

    }
}
