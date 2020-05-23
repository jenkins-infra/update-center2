package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;
import io.jenkins.update_center.MavenRepository;
import io.jenkins.update_center.Plugin;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public class PluginDocumentationUrlsRoot extends WithoutSignature {

    @JSONField(unwrapped = true)
    public final Map<String, Entry> pluginToEntry = new TreeMap<>();

    public PluginDocumentationUrlsRoot(MavenRepository repo) throws IOException {
        for (Plugin plugin : repo.listJenkinsPlugins()) {
            pluginToEntry.put(plugin.getArtifactId(), new Entry(plugin.getLatest().getPluginUrl()));
        }
    }

    public static class Entry {
        private final String url;

        private Entry(String url) {
            this.url = url;
        }

        public String getUrl() {
            return url;
        }
    }
}
