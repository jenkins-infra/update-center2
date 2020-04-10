package org.jvnet.hudson.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;
import org.jvnet.hudson.update_center.HPI;

import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

class ReleaseHistoryEntry {
    @JSONField
    public final String title;
    @JSONField
    public final String wiki;
    @JSONField
    public final String gav;
    @JSONField
    public final String version;
    @JSONField
    public final long timestamp;
    @JSONField
    public final String url;

    private static final Calendar DATE_CUTOFF = new GregorianCalendar();

    {
        DATE_CUTOFF.add(Calendar.DAY_OF_MONTH, -31);
    }

    ReleaseHistoryEntry(HPI hpi) throws IOException {
        if (DATE_CUTOFF.before(hpi.getTimestampAsDate())) {
            title = hpi.getName();
            wiki = hpi.getPluginUrl();
        } else {
            title = null;
            wiki = null;
        }
        version = hpi.version;
        this.gav = hpi.artifact.getGav();
        timestamp = hpi.artifact.timestamp;
        url = hpi.getDownloadUrl().toString();
    }
}
