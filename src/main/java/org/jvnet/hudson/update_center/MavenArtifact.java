package org.jvnet.hudson.update_center;

import net.sf.json.JSONObject;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
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
        } catch (AbstractArtifactResolutionException e) {
            throw (IOException)new IOException("Failed to resolve artifact "+artifact).initCause(e);
        }
    }

    public File resolvePOM() throws IOException {
        try {
            return repository.resolve(artifact,"pom");
        } catch (AbstractArtifactResolutionException e) {
            throw (IOException)new IOException("Failed to resolve artifact "+artifact).initCause(e);
        }
    }

    public JSONObject toJSON(String name) throws IOException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("version", version);

        long lastModified = getTimestamp();

        o.put("url", getURL().toExternalForm());
        SimpleDateFormat buildDateFormatter = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        o.put("buildDate", buildDateFormatter.format(lastModified));

        return o;
    }

    public long getTimestamp() throws IOException {
        if (timestamp==0)
            getManifest();
        return timestamp;
    }

    private Manifest getManifest() throws IOException {
        if (manifest==null) {
            JarFile jar = new JarFile(resolve());
            ZipEntry e = jar.getEntry("META-INF/MANIFEST.MF");
            timestamp = e.getTime();
            manifest = jar.getManifest();
            jar.close();
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
        return new URL("http://hudson-ci.org/maven-repository/"+artifact.groupId.replace('.','/')+"/"+artifact.artifactId+"/"+artifact.version+"/"+artifact.artifactId+"-"+artifact.version+"."+artifact.packaging);
    }
}
