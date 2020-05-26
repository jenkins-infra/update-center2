package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;
import io.jenkins.update_center.HPI;
import io.jenkins.update_center.MavenRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ReleaseHistoryRoot extends WithoutSignature {
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
}
