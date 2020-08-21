package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;
import io.jenkins.update_center.HPI;
import io.jenkins.update_center.MavenRepository;
import io.jenkins.update_center.Plugin;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RecentReleasesRoot extends WithoutSignature {
    @JSONField
    public List<RecentReleasesEntry> releases = new ArrayList<>();

    public RecentReleasesRoot(MavenRepository repository) throws IOException {
        for (Plugin plugin : repository.listJenkinsPlugins()) {
            for (HPI release : plugin.getArtifacts().values()) {
                if (Instant.ofEpochMilli(release.getTimestamp()).isBefore(Instant.now().minus(MAX_AGE))) {
                    // too old, ignore
                    continue;
                }
                releases.add(new RecentReleasesEntry(release));
            }
        }
    }

    private static final Duration MAX_AGE = Duration.ofHours(24);
}
