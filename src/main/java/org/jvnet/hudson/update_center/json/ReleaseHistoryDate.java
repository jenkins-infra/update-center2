package org.jvnet.hudson.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;
import org.jvnet.hudson.update_center.HPI;
import org.jvnet.hudson.update_center.MavenArtifact;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class ReleaseHistoryDate {
    private static final Logger LOGGER = Logger.getLogger(ReleaseHistoryDate.class.getName());
    private transient final SimpleDateFormat dateFormat = MavenArtifact.getDateFormat();

    @JSONField
    public final String date;

    @JSONField
    public final List<ReleaseHistoryEntry> releases;

    ReleaseHistoryDate(Date date, Map<String, HPI> pluginsById) throws IOException {
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
