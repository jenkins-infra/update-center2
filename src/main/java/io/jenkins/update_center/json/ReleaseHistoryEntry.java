package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;
import io.jenkins.update_center.HPI;

import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

class ReleaseHistoryEntry {
    @JSONField
    public final String title;
    @JSONField
    public final String wiki; // historical name for the plugin documentation URL field
    @JSONField
    public final String gav;
    @JSONField
    public final String version;
    @JSONField
    public final long timestamp;
    @JSONField
    public final String url;
    @JSONField
    public Boolean latestRelease;
    @JSONField
    public Boolean firstRelease;

    private static final Calendar DATE_CUTOFF = new GregorianCalendar();

    static {
        DATE_CUTOFF.add(Calendar.DAY_OF_MONTH, -31);
    }

    ReleaseHistoryEntry(HPI hpi) throws IOException {
        if (hpi.getTimestampAsDate().after(DATE_CUTOFF.getTime())) {
            title = hpi.getName();
            wiki = hpi.getPluginUrl();
        } else {
            title = null;
            wiki = null;
        }
        if (hpi.getPlugin().getLatest() == hpi) {
            latestRelease = true;
        }
        if (hpi.getPlugin().getFirst() == hpi) {
            firstRelease = true;
        }
        version = hpi.version;
        this.gav = hpi.artifact.getGav();
        timestamp = hpi.repository.getMetadata(hpi).timestamp;
        url = "https://plugins.jenkins.io/" + hpi.artifact.artifactId;
    }
}
