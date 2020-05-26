package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;
import io.jenkins.update_center.HPI;
import io.jenkins.update_center.MavenArtifact;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class ReleaseHistoryDate {
    private static final Logger LOGGER = Logger.getLogger(ReleaseHistoryDate.class.getName());

    @JSONField
    public final String date;

    @JSONField
    public final List<ReleaseHistoryEntry> releases;

    ReleaseHistoryDate(Date date, Map<String, HPI> pluginsById) {
        SimpleDateFormat dateFormat = MavenArtifact.getDateFormat();
        this.date = dateFormat.format(date);
        List<ReleaseHistoryEntry> list = new ArrayList<>();
        for (HPI hpi : pluginsById.values()) {
            try {
                ReleaseHistoryEntry releaseHistoryEntry = new ReleaseHistoryEntry(hpi);
                list.add(releaseHistoryEntry);
            } catch (Exception ex) {
                LOGGER.log(Level.INFO, "Failed to retrieve plugin info for " + hpi.artifact.artifactId, ex);
            }
        }
        this.releases = list;
    }
}
