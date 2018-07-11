package org.jvnet.hudson.update_center;

import java.io.IOException;

public abstract class ChecksumSource {
    abstract public Digests getDigests(MavenArtifact artifact) throws IOException;

    private static String ARTIFACTORY_API_USERNAME = System.getenv("ARTIFACTORY_USERNAME");
    private static String ARTIFACTORY_API_PASSWORD = System.getenv("ARTIFACTORY_PASSWORD");

    private static ChecksumSource instance;

    public static ChecksumSource getInstance() {
        if (instance == null) {
            if (ARTIFACTORY_API_PASSWORD != null && ARTIFACTORY_API_USERNAME != null) {
                instance = new ArtifactoryChecksumSource(ARTIFACTORY_API_USERNAME, ARTIFACTORY_API_PASSWORD);
            } else {
                instance = new MavenChecksumSource();
            }
        }
        return instance;
    }

    public class Digests {
        public String sha1;
        public String sha256;
    }
}
