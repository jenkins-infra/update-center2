package org.jvnet.hudson.update_center;

import com.google.common.io.Files;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

public class ArtifactoryRedirector {
    private final File directory;

    private Map<String, MavenArtifact> redirects = new TreeMap<>();

    public ArtifactoryRedirector(File directory) {
        this.directory = directory;
    }

    private String getUri(MavenArtifact a) {
        String basename = a.artifact.artifactId + "-" + a.artifact.version;
        String filename;
        if (a.artifact.classifier != null) {
            filename = basename + "-" + a.artifact.classifier + "." + a.artifact.packaging;
        } else {
            filename = basename + "." + a.artifact.packaging;
        }
        String ret = "http://repo.jenkins-ci.org/releases/" + a.artifact.groupId.replace(".", "/") + "/" + a.artifact.artifactId + "/" + a.version + "/" + filename;
        return ret;
    }

    public void recordRedirect(MavenArtifact a, String path) {
        this.redirects.put(path, a);
    }

    private String regexEscape(String path) {
        // TODO implement RCRE pattern escaping
        return path;
    }

    public void writeRedirects() throws IOException {
        directory.mkdirs();
        BufferedWriter writer = Files.newWriter(new File(directory, ".htaccess"), StandardCharsets.UTF_8);
        for (Map.Entry<String, MavenArtifact> entry : redirects.entrySet()) {
            writer.write(String.format("Redirect \"/%s\" \"%s\"\n", regexEscape(entry.getKey()), getUri(entry.getValue())));
        }
        writer.close();
    }
}
