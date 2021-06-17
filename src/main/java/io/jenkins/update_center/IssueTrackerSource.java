package io.jenkins.update_center;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.annotation.JSONField;
import io.jenkins.update_center.util.Environment;
import org.apache.commons.io.IOUtils;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IssueTrackerSource {
    private static final Logger LOGGER = Logger.getLogger(IssueTrackerSource.class.getName());

    private static final String DATA_URL = Environment.getString("ISSUE_TRACKER_JSON_URL", "https://reports.jenkins.io/issues.index.json");

    private HashMap<String, List<IssueTracker>> pluginToIssueTrackers;

    public static class IssueTracker {
        @JSONField
        public String type;
        @JSONField
        public String viewUrl;
        @JSONField
        public String reportUrl;
    }

    private static IssueTrackerSource instance;

    public static IssueTrackerSource getInstance() {
        if (instance == null) {
            IssueTrackerSource its = new IssueTrackerSource();
            its.init();
            instance = its;
        }
        return instance;
    }

    private void init() {
        try {
            final String jsonData = IOUtils.toString(new URL(DATA_URL), StandardCharsets.UTF_8);
            pluginToIssueTrackers = JSON.parseObject(jsonData, new TypeReference<HashMap<String, List<IssueTracker>>>(){}.getType());
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex.getMessage());
        }
    }

    public List<IssueTracker> getIssueTrackers(String plugin) {
        return pluginToIssueTrackers.computeIfAbsent(plugin, p -> null); // Don't advertise empty lists of issue trackers if there are none.
    }
}
