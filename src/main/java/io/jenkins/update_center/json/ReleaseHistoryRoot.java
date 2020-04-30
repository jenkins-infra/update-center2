package io.jenkins.update_center.json;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.serializer.SerializerFeature;
import io.jenkins.update_center.HPI;
import io.jenkins.update_center.MavenRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ReleaseHistoryRoot {
    @JSONField
    public final List<ReleaseHistoryDate> releaseHistory;

    public ReleaseHistoryRoot(MavenRepository repository) throws IOException {
        List<ReleaseHistoryDate> list = new ArrayList<>();
        for (Map.Entry<Date, Map<String, HPI>> entry : repository.listPluginsByReleaseDate().entrySet()) {
            ReleaseHistoryDate releaseHistoryDate = new ReleaseHistoryDate(entry.getKey(), entry.getValue());
            list.add(releaseHistoryDate);
        }
        this.releaseHistory = list;
    }

    public void writeToFile(File file, boolean pretty) throws IOException {
        if (pretty) {
            JSON.writeJSONString(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8), this, SerializerFeature.DisableCircularReferenceDetect, SerializerFeature.PrettyFormat);
        } else {
            JSON.writeJSONString(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8), this, SerializerFeature.DisableCircularReferenceDetect);
        }
    }
}
