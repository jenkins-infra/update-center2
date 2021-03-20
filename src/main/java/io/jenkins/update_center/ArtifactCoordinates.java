package io.jenkins.update_center;

import java.util.Objects;

public class ArtifactCoordinates {

    public final String groupId;
    public final String artifactId;
    public final String version;
    public final String packaging;
    public final String classifier;

    public ArtifactCoordinates(String groupId, String artifactId, String version, String packaging, String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packaging = packaging;
        this.classifier = classifier;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArtifactCoordinates that = (ArtifactCoordinates) o;
        return Objects.equals(groupId, that.groupId) &&
                Objects.equals(artifactId, that.artifactId) &&
                Objects.equals(version, that.version) &&
                Objects.equals(packaging, that.packaging) &&
                Objects.equals(classifier, that.classifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, packaging, classifier);
    }
}
