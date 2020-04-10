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
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jvnet.hudson.update_center.util.JavaSpecificationVersion;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.*;
import java.net.URL;
import java.net.MalformedURLException;

import static org.jvnet.hudson.update_center.JenkinsWar.HUDSON_CUT_OFF;

/**
 * A particular version of a plugin and its metadata.
 *
 * For version independent metadata, see {@link Plugin}.
 */
public class HPI extends MavenArtifact {
    private final Pattern developersPattern = Pattern.compile("([^:]*):([^:]*):([^,]*),?");
    private final Plugin plugin;

    public HPI(BaseMavenRepository repository, ArtifactCoordinates artifact, Plugin plugin) {
        super(repository, artifact);
        this.plugin = plugin;
    }

    /**
     * Download a plugin via more intuitive URL. This also helps us track download counts.
     */
    public URL getDownloadUrl() throws MalformedURLException {
        return new URL("http://updates.jenkins-ci.org/download/plugins/"+artifact.artifactId+"/"+version+"/"+artifact.artifactId+".hpi");
    }

    /**
     * Who built this release?
     */
    public String getBuiltBy() throws IOException {
        return getManifestAttributes().getValue("Built-By");
    }

    public String getRequiredJenkinsVersion() throws IOException {
        String v = getManifestAttributes().getValue("Jenkins-Version");
        if (v!=null)        return v;

        v = getManifestAttributes().getValue("Hudson-Version");
        if (fixNull(v) != null) {
            try {
                VersionNumber n = new VersionNumber(v);
                if (n.compareTo(HUDSON_CUT_OFF)<=0)
                    return v;   // Hudson <= 1.395 is treated as Jenkins
                // TODO: Jenkins-Version started appearing from Jenkins 1.401 POM.
                // so maybe Hudson > 1.400 shouldn't be considered as a Jenkins plugin?
            } catch (IllegalArgumentException e) {
            }
        }

        // Parent versions 1.393 to 1.398 failed to record requiredCore.
        // If value is missing, let's default to 1.398 for now.
        return "1.398";
    }

    /**
     * Earlier versions of the maven-hpi-plugin put "null" string literal, so we need to treat it as real null.
     */
    private static String fixNull(String v) {
        if("null".equals(v))    return null;
        return v;
    }

    public String getCompatibleSinceVersion() throws IOException {
        return getManifestAttributes().getValue("Compatible-Since-Version");
    }

    /**
     * Gets Minimum Java Version required by the plugin.
     * This uses the value of the {@code Minimum-Java-Version} manifest entry
     * @return Minimum Java Version or {@code null} if it is unknown
     * @throws IOException Manifest read error
     */
    @CheckForNull
    public JavaSpecificationVersion getMinimumJavaVersion() throws IOException {
        String manifestEntry = getManifestAttributes().getValue("Minimum-Java-Version");
        if (StringUtils.isNotBlank(manifestEntry)) {
            return new JavaSpecificationVersion(manifestEntry);
        }

        return null;
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
        Matcher m = developersPattern.matcher(devs);
        int totalMatched = 0;
        while (m.find()) {
            r.add(new Developer(m.group(1).trim(), m.group(2).trim(), m.group(3).trim()));
            totalMatched += m.end() - m.start();
        }
        if (totalMatched < devs.length())
            // ignore and move on
            System.err.println("Unparsable developer info: '" + devs.substring(totalMatched)+"'");
        return r;
    }

    boolean isEqualsTo(String groupId, String artifactId, String version) {
        return artifact.artifactId.equals(artifactId)
            && artifact.groupId.equals(groupId)
            && artifact.version.equals(version);
    }

    public static class Dependency {
        public final String name;
        public final String version;
        public final boolean optional;

        public Dependency(String token) {
            this.optional = token.endsWith(OPTIONAL_RESOLUTION);
            if(optional)
                token = token.substring(0, token.length()-OPTIONAL_RESOLUTION.length());

            String[] pieces = token.split(":");
            name = pieces[0];
            version = pieces[1];
        }

        private static final String OPTIONAL_RESOLUTION = ";resolution:=optional";

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

        public Developer(String name, String developerId, String email) {
            this.name = name;
            this.developerId = developerId;
            this.email = email;
        }

        public JSONObject toJSON() {
            JSONObject o = new JSONObject();
            if (has(name))
                o.put("name", name);
            if (has(developerId))
                o.put("developerId", developerId);
            if (has(email))
                o.put("email", email);

            if (!o.isEmpty()) {
                return o;
            } else {
                return null;
            }
        }

        private boolean has(String s) {
            return s!=null && s.length()>0;
        }
    }

    /**
     * Does this artifact come from the jenkins community?
     */
    public boolean isAuthenticJenkinsArtifact() {
        // mayebe it should be startWith("org.jenkins")?
        return artifact.groupId.contains("jenkins");
    }
}
