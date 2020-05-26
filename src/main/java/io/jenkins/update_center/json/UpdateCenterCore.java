package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;
import hudson.util.VersionNumber;
import io.jenkins.update_center.JenkinsWar;
import io.jenkins.update_center.MavenRepository;

import java.io.IOException;
import java.util.TreeMap;

public class UpdateCenterCore {

    @JSONField
    public String buildDate;

    @JSONField
    public String name = "core";

    @JSONField
    public String sha1;

    @JSONField
    public String sha256;

    @JSONField
    public String url;

    @JSONField
    public String version;

    UpdateCenterCore(TreeMap<VersionNumber, JenkinsWar> jenkinsWarsByVersionNumber) throws IOException {
        if (jenkinsWarsByVersionNumber.isEmpty()) {
            return;
        }

        JenkinsWar war = jenkinsWarsByVersionNumber.get(jenkinsWarsByVersionNumber.firstKey());

        version = war.version;
        url = war.getDownloadUrl().toString();
        final MavenRepository.Digests digests = war.getDigests();
        sha1 = digests.sha1;
        sha256 = digests.sha256;
        buildDate = war.getTimestampAsString();
    }
}
