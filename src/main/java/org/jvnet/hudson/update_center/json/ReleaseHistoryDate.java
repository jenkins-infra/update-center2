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

class ReleaseHistoryDate {

    private transient final SimpleDateFormat dateFormat = MavenArtifact.getDateFormat();

    @JSONField
    public final String date;

    @JSONField
    public final List<ReleaseHistoryEntry> releases;

    ReleaseHistoryDate(Date date, Map<String, HPI> pluginsById) throws IOException {
        this.date = dateFormat.format(date);
        List<ReleaseHistoryEntry> list = new ArrayList<>();
        for (HPI hpi : pluginsById.values()) {
            ReleaseHistoryEntry releaseHistoryEntry = new ReleaseHistoryEntry(hpi);
            list.add(releaseHistoryEntry);
        }
        this.releases = list;
    }
}
