package org.jvnet.hudson.update_center;

public class ArtifactCoordinates extends Gav {

    public final String packaging;
    public final String classifier;

    /**
     * Epoch seconds (Unix timestamp)
     *
     * TODO Rename this class, doesn't fit with this extra data
     */
    public final long timestamp;

    public ArtifactCoordinates(String groupId, String artifactId, String version, String packaging, String classifier) {
        super(groupId, artifactId, version);
        this.packaging = packaging;
        this.classifier = classifier;
        this.timestamp = 0;
    }

    public ArtifactCoordinates(String groupId, String artifactId, String version, String packaging, String classifier, long timestamp) {
        super(groupId, artifactId, version);
        this.packaging = packaging;
        this.classifier = classifier;
        this.timestamp = timestamp;
    }
}
