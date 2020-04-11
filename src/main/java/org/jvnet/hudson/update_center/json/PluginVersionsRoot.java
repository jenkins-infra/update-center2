package org.jvnet.hudson.update_center.json;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.jvnet.hudson.update_center.MavenRepository;
import org.jvnet.hudson.update_center.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class PluginVersionsRoot {
    @JSONField
    public final String updateCenterVersion;
    private final MavenRepository repository;

    private JsonSignature signature;

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

    @JSONField
    public JsonSignature getSignature() {
        return signature;
    }

    public void writeToFile(File file) throws IOException {
        // TODO add support for creating a signature
        JSON.writeJSONString(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8), this, SerializerFeature.DisableCircularReferenceDetect);
    }
}
