package org.jvnet.hudson.update_center.json;

import org.jvnet.hudson.update_center.HPI;
import org.jvnet.hudson.update_center.MavenArtifact;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReleaseHistoryDate {
    private String date;
    private List<ReleaseHistoryEntry> releases;

    public ReleaseHistoryDate(Date date, Map<String, HPI> pluginsById) {
        this.date = MavenArtifact.getDateFormat().format(date);
        this.releases = pluginsById.entrySet().stream().map(entry -> new ReleaseHistoryEntry(entry.getValue())).collect(Collectors.toList());
    }
}
