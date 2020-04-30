package io.jenkins.update_center;

import hudson.util.VersionNumber;
import org.apache.commons.io.IOUtils;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MetadataWriter {

    public static final Logger LOGGER = Logger.getLogger(MetadataWriter.class.getName());
    @Option(name="-pluginCount.txt",usage="Report the number of plugins published by the update site")
    public File pluginCountTxt;

    @Option(name="-latestCore.txt",usage="The core version offered by this update site")
    public File latestCoreTxt;
    private MavenRepository repository;

    public void writeMetadataFiles(MavenRepository repository) throws IOException {
        if (latestCoreTxt != null) {
            if (!latestCoreTxt.getParentFile().mkdirs() && !latestCoreTxt.getParentFile().isDirectory()) {
                LOGGER.log(Level.WARNING, () -> "Failed to create parent directory for  " + latestCoreTxt);
            } else {
                final TreeMap<VersionNumber, JenkinsWar> wars = repository.getJenkinsWarsByVersionNumber();
                if (wars.isEmpty()) {
                    LOGGER.log(Level.WARNING, () -> "Cannot write " + latestCoreTxt + " because there are no core versions in this update site");
                } else {
                    IOUtils.write(wars.firstKey().toString(), new FileOutputStream(latestCoreTxt), StandardCharsets.UTF_8);
                }
            }
        }
        if (pluginCountTxt != null) {
            if (!pluginCountTxt.getParentFile().mkdirs() && !pluginCountTxt.getParentFile().isDirectory()) {
                LOGGER.log(Level.WARNING, () -> "Failed to create parent directory for  " + pluginCountTxt);
            } else {
                IOUtils.write(Integer.toString(repository.listJenkinsPlugins().size()), new FileOutputStream(pluginCountTxt), StandardCharsets.UTF_8);
            }
        }
    }
}
