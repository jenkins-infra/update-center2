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
import io.jenkins.update_center.util.Environment;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Artifact from a Maven repository and its metadata.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenArtifact {
    protected static final String DOWNLOADS_ROOT_URL = Environment.getString("DOWNLOADS_ROOT_URL", "https://updates.jenkins.io/download");
    /**
     * Where did this plugin come from?
     */
    public final BaseMavenRepository repository;
    public final ArtifactCoordinates artifact;
    public final String version;
    private File hpi;

    private Manifest manifest;

    public MavenArtifact(@Nonnull BaseMavenRepository repository, @Nonnull ArtifactCoordinates artifact) {
        this.artifact = artifact;
        this.repository = repository;
        version = artifact.version;
    }

    public File resolve() throws IOException {
        try {
            if (hpi == null) {
                hpi = repository.resolve(artifact);
            }
            return hpi;
        } catch (IllegalArgumentException e) {
            throw new IOException("Failed to resolve artifact " + artifact, e);
        }
    }

    public File resolvePOM() throws IOException {
        return repository.resolve(artifact,"pom", null);
    }

    public MavenRepository.ArtifactMetadata getMetadata() throws IOException {
        return repository.getMetadata(this);
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
        

        Date lastModifiedDate = new Date(lastModified);
        Calendar cal = new GregorianCalendar();
        cal.setTime(lastModifiedDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
    
    public static SimpleDateFormat getDateFormat() {
        return new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    }

    public long getTimestamp() throws IOException {
        return repository.getMetadata(this).timestamp;
    }

    public Manifest getManifest() throws IOException {
        if (manifest==null) {
            manifest = repository.getManifest(this);
        }
        return manifest;
    }

    public Attributes getManifestAttributes() throws IOException {
        return getManifest().getMainAttributes();
    }

    /**
     * Where to download from?
     *
     * @return the URL that users whould be able to download from
     * @throws MalformedURLException if the resulting URL is invalid
     */
    public URL getDownloadUrl() throws MalformedURLException {
        return new URL("repo.jenkins-ci.org/public/"+artifact.groupId.replace('.','/')+"/"+artifact.artifactId+"/"+artifact.version+"/"+artifact.artifactId+"-"+artifact.version+"."+artifact.packaging);
    }

    @Override
    public String toString() {
        return artifact.toString(); // TODO this is actually useless
    }

    public String getGavId() {
        return artifact.groupId+':'+artifact.artifactId+':'+artifact.version;
    }


}
