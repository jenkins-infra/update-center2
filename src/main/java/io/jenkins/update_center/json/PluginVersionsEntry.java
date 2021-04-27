package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;
import io.jenkins.update_center.MavenRepository;
import io.jenkins.update_center.HPI;

import java.io.IOException;
import java.util.List;

public class PluginVersionsEntry {
    @JSONField
    public final String buildDate;
    @JSONField
    public final String name;
    @JSONField
    public final String requiredCore;
    @JSONField
    public final String sha1;
    @JSONField
    public final String sha256;
    @JSONField
    public final String url;
    @JSONField
    public final String version;
    @JSONField
    public final String compatibleSinceVersion;

    @JSONField
    public final List<HPI.Dependency> dependencies;

    PluginVersionsEntry(HPI hpi) throws IOException {
        final MavenRepository.ArtifactMetadata artifactMetadata = hpi.getMetadata();
        name = hpi.artifact.artifactId;
        requiredCore = hpi.getRequiredJenkinsVersion();
        sha1 = artifactMetadata.sha1;
        sha256 = artifactMetadata.sha256;
        url = hpi.getDownloadUrl().toString();
        version = hpi.version;
        buildDate = hpi.getTimestampAsString();
        dependencies = hpi.getDependencies();
        compatibleSinceVersion = hpi.getCompatibleSinceVersion();
    }
}
