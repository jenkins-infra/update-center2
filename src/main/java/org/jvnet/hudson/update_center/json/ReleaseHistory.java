package org.jvnet.hudson.update_center.json;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import org.jvnet.hudson.update_center.HPI;
import org.jvnet.hudson.update_center.MavenRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ReleaseHistory {
    @JSONField
    public final List<ReleaseHistoryDate> releaseHistory;

    public ReleaseHistory(MavenRepository repository) throws IOException {
        List<ReleaseHistoryDate> list = new ArrayList<>();
        for (Map.Entry<Date, Map<String, HPI>> entry : repository.listPluginsByReleaseDate().entrySet()) {
            ReleaseHistoryDate releaseHistoryDate = new ReleaseHistoryDate(entry.getKey(), entry.getValue());
            list.add(releaseHistoryDate);
        }
        this.releaseHistory = list;
    }

    public void writeToFile(File file) throws IOException {
        JSON.writeJSONString(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8), this);
    }
}
