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
import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Artifact from a Maven repository and its metadata.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenArtifact {
    /**
     * Where did this plugin come from?
     */
    public final MavenRepository repository;
    public final ArtifactInfo artifact;
    public final String version;
    private File hpi;

    // lazily computed
    private long timestamp;
    private Manifest manifest;

    public MavenArtifact(MavenRepository repository, ArtifactInfo artifact) {
        this.artifact = artifact;
        this.repository = repository;
        version = artifact.version;
    }

    public File resolve() throws IOException {
        try {
            if (hpi==null)
                hpi = repository.resolve(artifact);
            return hpi;
        } catch (IllegalArgumentException e) {
            /*
                Exception in thread "main" java.lang.IllegalArgumentException: Invalid uri 'http://maven.glassfish.org/content/groups/public//${parent/groupId}/startup-trigger-plugin/1.1/startup-trigger-plugin-1.1.hpi': escaped absolute path not valid
                    at org.apache.commons.httpclient.HttpMethodBase.<init>(HttpMethodBase.java:222)
                    at org.apache.commons.httpclient.methods.GetMethod.<init>(GetMethod.java:89)
                    at org.apache.maven.wagon.shared.http.AbstractHttpClientWagon.fillInputData(AbstractHttpClientWagon.java:465)
                    at org.apache.maven.wagon.StreamWagon.getInputStream(StreamWagon.java:116)
                    at org.apache.maven.wagon.StreamWagon.getIfNewer(StreamWagon.java:88)
                    at org.apache.maven.wagon.StreamWagon.get(StreamWagon.java:61)
                    at org.apache.maven.artifact.manager.DefaultWagonManager.getRemoteFile(DefaultWagonManager.java:597)
                    at org.apache.maven.artifact.manager.DefaultWagonManager.getArtifact(DefaultWagonManager.java:476)
                    at org.apache.maven.artifact.manager.DefaultWagonManager.getArtifact(DefaultWagonManager.java:354)
                    at org.apache.maven.artifact.resolver.DefaultArtifactResolver.resolve(DefaultArtifactResolver.java:167)
                    at org.apache.maven.artifact.resolver.DefaultArtifactResolver.resolve(DefaultArtifactResolver.java:82)
                    at org.jvnet.hudson.update_center.MavenRepositoryImpl.resolve(MavenRepositoryImpl.java:161)
                    at org.jvnet.hudson.update_center.MavenRepository.resolve(MavenRepository.java:57)
                    at org.jvnet.hudson.update_center.MavenArtifact.resolve(MavenArtifact.java:70)
                    at org.jvnet.hudson.update_center.MavenArtifact.getManifest(MavenArtifact.java:134)
                    at org.jvnet.hudson.update_center.MavenArtifact.getTimestamp(MavenArtifact.java:128)
                    at org.jvnet.hudson.update_center.Main.checkLatestDate(Main.java:301)
                    at org.jvnet.hudson.update_center.Main.buildPlugins(Main.java:269)
             */
            throw (IOException)new IOException("Failed to resolve artifact "+artifact).initCause(e);
        } catch (AbstractArtifactResolutionException e) {
            throw (IOException)new IOException("Failed to resolve artifact "+artifact).initCause(e);
        }
    }

    public File resolvePOM() throws IOException {
        try {
            return repository.resolve(artifact,"pom", null);
        } catch (AbstractArtifactResolutionException e) {
            throw (IOException)new IOException("Failed to resolve artifact "+artifact).initCause(e);
        }
    }

    public File resolveSources() throws IOException {
        try {
            return repository.resolve(artifact,"jar","sources");
        } catch (AbstractArtifactResolutionException e) {
            throw (IOException)new IOException("Failed to resolve artifact "+artifact).initCause(e);
        }
    }

    /**
     * Computes the SHA1 signature of the file.
     */
    public String getDigest() throws IOException {        
        try (FileInputStream fin = new FileInputStream(resolve())) {
            MessageDigest sig = MessageDigest.getInstance("SHA1");            
            byte[] buf = new byte[2048];
            int len;
            while ((len=fin.read(buf,0,buf.length))>=0)
                sig.update(buf,0,len);

            return new String(Base64.encodeBase64(sig.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public JSONObject toJSON(String name) throws IOException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("version", version);

        o.put("url", getURL().toExternalForm());
        o.put("buildDate", getTimestampAsString());
        o.put("sha1",getDigest());

        return o;
    }

    public VersionNumber getVersion() {
        return new VersionNumber(version);
    }

    public boolean isAlphaOrBeta() {
        String s = version.toLowerCase(Locale.ENGLISH);
        return s.contains("alpha") || s.contains("beta");
    }

    public String getTimestampAsString() throws IOException {
        long lastModified = getTimestamp();
        SimpleDateFormat bdf = getDateFormat();

        return bdf.format(lastModified);
    }

    public Date getTimestampAsDate() throws IOException {
        long lastModified = getTimestamp();
        SimpleDateFormat bdf = getDateFormat();

        Date tsDate;
        
        try {
            tsDate = bdf.parse(bdf.format(new Date(lastModified)));
        } catch (ParseException pe) {
            throw new IOException(pe.getMessage());
        }

        return tsDate;
    }
    
    public static SimpleDateFormat getDateFormat() {
        return new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    }
        
    public long getTimestamp() throws IOException {
        if (timestamp==0)
            getManifest();
        return timestamp;
    }

    public Manifest getManifest() throws IOException {
        if (manifest==null) {
            File f = resolve();
            try {
                JarFile jar = new JarFile(f);
                ZipEntry e = jar.getEntry("META-INF/MANIFEST.MF");
                timestamp = e.getTime();
                manifest = jar.getManifest();
                jar.close();
            } catch (IOException x) {
                throw (IOException)new IOException("Failed to open "+f).initCause(x);
            }
        }
        return manifest;
    }

    public Attributes getManifestAttributes() throws IOException {
        return getManifest().getMainAttributes();
    }

    /**
     * Where to download from?
     */
    public URL getURL() throws MalformedURLException {
        return new URL("repo.jenkins-ci.org/public/"+artifact.groupId.replace('.','/')+"/"+artifact.artifactId+"/"+artifact.version+"/"+artifact.artifactId+"-"+artifact.version+"."+artifact.packaging);
    }

    @Override
    public String toString() {
        return artifact.toString();
    }

    public String getGavId() {
        return artifact.groupId+':'+artifact.artifactId+':'+artifact.version;
    }
}
