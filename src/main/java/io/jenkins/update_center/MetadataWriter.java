package io.jenkins.update_center;

import hudson.util.VersionNumber;
import org.apache.commons.io.IOUtils;
import org.kohsuke.args4j.Option;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MetadataWriter {
    private static final Logger LOGGER = Logger.getLogger(MetadataWriter.class.getName());
    private static final String LATEST_CORE_FILENAME = "latestCore.txt";
    private static final String PLUGIN_COUNT_FILENAME = "pluginCount.txt";

    @Option(name = "--write-plugin-count", usage = "Report the number of plugins published by the update site")
    public boolean generatePluginCount;

    @Option(name = "--write-latest-core", usage = "Generate a text file with the core version offered by this update site")
    public boolean generateLatestCore;

    public void writeMetadataFiles(@Nonnull MavenRepository repository, @CheckForNull File outputDirectory) throws IOException {
        Objects.requireNonNull(repository, "repository");

        if (!generateLatestCore && !generatePluginCount) {
            LOGGER.log(Level.INFO, "Skipping generation of metadata files");
            return;
        }

        if (outputDirectory == null) {
            throw new IOException("No output directory specified but generation of metadata files requested");
        }

        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new IOException("Failed to create " + outputDirectory);
        }

        if (generateLatestCore) {
            final TreeMap<VersionNumber, JenkinsWar> wars = repository.getJenkinsWarsByVersionNumber();
            if (wars.isEmpty()) {
                LOGGER.log(Level.WARNING, () -> "Cannot write " + LATEST_CORE_FILENAME + " because there are no core versions in this update site");
            } else {
                try (final FileOutputStream output = new FileOutputStream(new File(outputDirectory, LATEST_CORE_FILENAME))) {
                    IOUtils.write(wars.firstKey().toString(), output, StandardCharsets.UTF_8);
                }
            }
        }

        if (generatePluginCount) {
            try (final FileOutputStream output = new FileOutputStream(new File(outputDirectory, PLUGIN_COUNT_FILENAME))) {
                IOUtils.write(Integer.toString(repository.listJenkinsPlugins().size()), output, StandardCharsets.UTF_8);
            }
        }
    }
}
