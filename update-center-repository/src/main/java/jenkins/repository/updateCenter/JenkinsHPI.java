package jenkins.repository.updateCenter;

import com.sun.tools.javac.resources.version;
import hudson.maven.MavenBuild;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.Hudson;
import net.sf.json.JSONObject;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.dom4j.DocumentException;
import hudson.maven.reporters.MavenArtifact;
import org.jvnet.hudson.update_center.*;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

public class JenkinsHPI extends HPI implements IHPI {

    private MavenArtifactRecord artifacts;
    private MavenBuild build;

    private Manifest manifest;
    private String name;

    public JenkinsHPI(PluginHistory history, String name, MavenBuild build, MavenArtifactRecord artifacts) throws AbstractArtifactResolutionException {
        super(history);
        this.artifacts = artifacts;
        this.build = build;
        this.name = name;
    }

     private Manifest getManifest() throws IOException {
        if (manifest==null) {
            File f = getFile(artifacts.mainArtifact);
            try {
                JarFile jar = new JarFile(f);
                ZipEntry e = jar.getEntry("META-INF/MANIFEST.MF");
                manifest = jar.getManifest();
                jar.close();
            } catch (IOException x) {
                throw (IOException)new IOException("Failed to open "+f).initCause(x);
            }
        }
        return manifest;
    }

    @Override
    public Attributes getManifestAttributes() throws IOException {
        return getManifest().getMainAttributes();  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isAuthenticJenkinsArtifact() {
        return true;
    }

    public ArtifactInfo getArtifact() throws IOException {
        return new ArtifactInfo("jenkinsCI",
                artifacts.mainArtifact.groupId,
                artifacts.mainArtifact.artifactId,
                artifacts.mainArtifact.version,
                artifacts.mainArtifact.classifier);
    }

    public Pom getPom() throws IOException, DocumentException {
        return new Pom( new FileInputStream(getFile(artifacts.pomArtifact)) );
    }

    public File getFile(MavenArtifact artifact)
    {
        File fPath = new File(new File(new File(build.getArtifactsDir(), artifact.groupId), artifact.artifactId), artifact.version);
        File fArtifact;

        fArtifact = new File(fPath, artifact.canonicalName);
        if( fArtifact.exists() )
            return fArtifact;


        fArtifact = new File(fPath, artifact.fileName);
        if( fArtifact.exists() )
            return fArtifact;

        throw new IllegalStateException("Maven artifact cannot be found with name or canonicalName - " + artifact);
    }

    public Timestamp getTimestamp() throws IOException {
        return new Timestamp( getFile( artifacts.pomArtifact ).lastModified() );
    }

    public JSONObject toJSON(String name) throws IOException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("version", artifacts.mainArtifact.version);

        o.put("url", getURL().toExternalForm());
        o.put("buildDate", getTimestamp().getTimestampAsString());

        return o;
    }

    public Version getVersion() {
        return new Version(artifacts.mainArtifact.version);  //To change body of implemented methods use File | Settings | File Templates.
    }

    public URL getURL() throws MalformedURLException {
        String root = Hudson.getInstance().getRootUrl() + "plugin/repository/project/" + name + "/Build/"
                    + build.getNumber() + "/repository/" + getArtifactPath(artifacts.mainArtifact);

        return new URL(root);
    }

    public String getArtifactPath(MavenArtifact artifact)
    {
        return artifact.groupId.replace('.','/') + "/" + artifact.artifactId + '/' + artifact.version + "/" + artifact.canonicalName;
    }

}
