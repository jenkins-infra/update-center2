package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;
import io.jenkins.update_center.MavenRepository;
import io.jenkins.update_center.Plugin;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class PluginVersionsRoot extends WithSignature {
    @JSONField
    public final String updateCenterVersion;
    private final MavenRepository repository;

    private Map<String, PluginVersions> plugins;

    public PluginVersionsRoot(String updateCenterVersion, MavenRepository repository) {
        this.updateCenterVersion = updateCenterVersion;
        this.repository = repository;
    }

    @JSONField
    public Map<String, PluginVersions> getPlugins() throws IOException {
        if (plugins == null) {
            plugins = new TreeMap<>(repository.listJenkinsPlugins().stream().collect(Collectors.toMap(Plugin::getArtifactId, plugin -> new PluginVersions(plugin.getArtifacts()))));
        }
        return plugins;
    }
}
