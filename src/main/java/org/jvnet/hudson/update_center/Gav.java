package org.jvnet.hudson.update_center;

public class Gav {
    public final String groupId;
    public final String artifactId;
    public final String version;

    public Gav(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }
}
