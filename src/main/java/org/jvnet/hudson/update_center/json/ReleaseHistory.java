package org.jvnet.hudson.update_center.json;

import org.jvnet.hudson.update_center.MavenRepository;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class ReleaseHistory {
    public final List<ReleaseHistoryDate> releaseHistory;

    public ReleaseHistory(MavenRepository repository) throws IOException {
        this.releaseHistory = repository.listPluginsByReleaseDate().entrySet().stream().map(entry -> new ReleaseHistoryDate(entry.getKey(), entry.getValue())).collect(Collectors.toList());
    }

    public void writeToFile(File file) {

    }
}
