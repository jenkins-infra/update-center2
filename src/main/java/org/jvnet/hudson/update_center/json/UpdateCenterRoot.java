package org.jvnet.hudson.update_center.json;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import org.jvnet.hudson.update_center.MavenRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class UpdateCenterRoot extends WithSignature {
    @JSONField
    public final String updateCenterVersion = "1";

    @JSONField
    public String connectionCheckUrl = "http://www.google.com"; // TODO pass in command line arg

    @JSONField
    public String id = "default"; // TODO pass in command line arg

    private final MavenRepository repo;

    @JSONField
    public List<UpdateCenterWarning> warnings;

    public UpdateCenterRoot(MavenRepository repo, File warningsJsonFile) throws IOException {
        this.repo = repo;

        // load warnings
        final String warningsJsonText = Files.readAllLines(warningsJsonFile.toPath(), StandardCharsets.UTF_8).stream().collect(Collectors.joining());
        warnings = Arrays.asList(JSON.parseObject(warningsJsonText, UpdateCenterWarning[].class));

    }
}
