package io.jenkins.update_center;

public class ArtifactCoordinates {

    public final String groupId;
    public final String artifactId;
    public final String version;
    public final String packaging;
    public final String classifier;

    /**
     * Epoch seconds (Unix timestamp)
     *
     * TODO Rename this class, doesn't fit with this extra data
     */
    public final long timestamp;

    public ArtifactCoordinates(String groupId, String artifactId, String version, String packaging, String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packaging = packaging;
        this.classifier = classifier;
        this.timestamp = 0;
    }

    public ArtifactCoordinates(String groupId, String artifactId, String version, String packaging, String classifier, long timestamp) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packaging = packaging;
        this.classifier = classifier;
        this.timestamp = timestamp;
    }

    public String getGav() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public String toString() {
        if (classifier == null) {
            return groupId + ":" + artifactId + ":" + version + ":" + packaging;
        }
        return groupId + ":" + artifactId + ":" + version + ":" + classifier + ":" + packaging;
    }
}
