package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;
import hudson.util.VersionNumber;
import io.jenkins.update_center.HPI;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.logging.Level.INFO;

public class PluginVersions {
    private static final Logger LOGGER = Logger.getLogger(PluginVersions.class.getName());

    @JSONField(unwrapped = true)
    public Map<String, PluginVersionsEntry> releases = new LinkedHashMap<>();

    PluginVersions(Map<VersionNumber, HPI> artifacts) {
        for (VersionNumber versionNumber : artifacts.keySet().stream().sorted().collect(Collectors.toList())) {
            try {
                if (releases.put(versionNumber.toString(), new PluginVersionsEntry(artifacts.get(versionNumber))) != null) {
                    throw new IllegalStateException("Duplicate key");
                }
            } catch (IOException ex) {
                LOGGER.log(INFO, "Failed to add " + artifacts.get(versionNumber).artifact.getGav() + " to plugin versions", ex);
            }
        }
    }
}
