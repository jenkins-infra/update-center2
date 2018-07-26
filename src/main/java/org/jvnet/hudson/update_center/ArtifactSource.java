package org.jvnet.hudson.update_center;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Manifest;

public abstract class ArtifactSource {
    abstract public Digests getDigests(MavenArtifact artifact) throws IOException;

    abstract public Manifest getManifest(MavenArtifact artifact) throws IOException;

    abstract public InputStream getZipFileEntry(MavenArtifact artifact, String path) throws IOException;

    private static String ARTIFACTORY_API_USERNAME = System.getenv("ARTIFACTORY_USERNAME");
    private static String ARTIFACTORY_API_PASSWORD = System.getenv("ARTIFACTORY_PASSWORD");

    private static ArtifactSource instance;

    public static ArtifactSource getInstance() {
        if (instance == null) {
            if (ARTIFACTORY_API_PASSWORD != null && ARTIFACTORY_API_USERNAME != null) {
                instance = new ArtifactoryArtifactSource(ARTIFACTORY_API_USERNAME, ARTIFACTORY_API_PASSWORD);
            } else {
                instance = new MavenArtifactSource();
            }
            System.err.println("Using ArtifactSource: " + instance.getClass().getSimpleName());
        }
        return instance;
    }

    public class Digests {
        public String sha1;
        public String sha256;
    }
}
