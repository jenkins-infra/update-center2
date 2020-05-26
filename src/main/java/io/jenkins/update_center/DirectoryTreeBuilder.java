package io.jenkins.update_center;

import hudson.util.VersionNumber;
import org.apache.commons.io.IOUtils;
import org.kohsuke.args4j.Option;

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DirectoryTreeBuilder {
    private static final Logger LOGGER = Logger.getLogger(DirectoryTreeBuilder.class.getName());

    /**
     * Directory at https://updates.jenkins.io/latest/ containing index.html and .htaccess to latest releases of all components.
     */
    @Option(name = "--latest-links-directory", usage = "Directory to contain links to latest releases and .htaccess redirects")
    public File latest;

    /**
     * Write a directory tree to the specified directory tree that contains all core (war) and plugin (hpi) releases.
     *
     * TODO: it also currently produces war/ directory that we aren't actually using. Maybe remove?
     */
    @Option(name = "--downloads-directory", usage = "Build mirrors.jenkins-ci.org layout (containing .war and .hpi files)")
    public File download = null;

    /**
     * Build the https://updates.jenkins.io/download/ directory structure that only contains index.html files.
     */
    @Option(name = "--download-links-directory", usage = "Build downloads web index files")
    public File wwwDownload = null;


    public void build(MavenRepository repo) throws IOException {

        try (LatestLinkBuilder latestLinks = prepareLatestLinkBuilder()) {

            /* Process plugins */
            for (Plugin plugin : repo.listJenkinsPlugins()) {
                if (latestLinks != null) {
                    latestLinks.add(plugin.getArtifactId() + ".hpi", plugin.getLatest().getDownloadUrl().getPath());
                }

                final TreeMap<VersionNumber, HPI> artifacts = plugin.getArtifacts();

                if (download != null) {
                    for (HPI v : artifacts.values()) {
                        stage(v, new File(download, "plugins/" + plugin.getArtifactId() + "/" + v.version + "/" + plugin.getArtifactId() + ".hpi"));
                    }
                    if (!artifacts.isEmpty()) {
                        createLatestSymlink(plugin);
                    }
                }

                if (wwwDownload != null) {
                    String permalink = String.format("/latest/%s.hpi", plugin.getArtifactId());
                    buildIndex(new File(wwwDownload, "plugins/" + plugin.getArtifactId()), plugin.getArtifactId(), artifacts.values(), permalink);
                }
            }

            /* Process Jenkins core */
            final TreeMap<VersionNumber, JenkinsWar> jenkinsWars = repo.getJenkinsWarsByVersionNumber();

            if (!jenkinsWars.isEmpty()) {
                if (latestLinks != null) {
                    latestLinks.add("jenkins.war", jenkinsWars.firstEntry().getValue().getDownloadUrl().getPath());
                }

                if (download != null) {
                    for (JenkinsWar w : jenkinsWars.values()) {
                        stage(w, new File(download, "war/" + w.version + "/" + w.getFileName()));
                    }
                }

                if (wwwDownload != null) {
                    buildIndex(new File(wwwDownload, "war/"), "jenkins.war", jenkinsWars.values(), "/latest/jenkins.war");
                }
            }
        }
    }

    @CheckForNull
    private LatestLinkBuilder prepareLatestLinkBuilder() throws IOException {
        if (latest == null) {
            return null;
        }
        if (!latest.mkdirs() && !latest.isDirectory()) {
            throw new IOException("Failed to created 'latest' directory at " + latest);
        }
        return new LatestLinkBuilder(latest);
    }

    /**
     * Generates symlink to the latest version.
     */
    private void createLatestSymlink(Plugin hpi) throws IOException {
        File dir = new File(download, "plugins/" + hpi.getArtifactId());
        final File latest = new File(dir, "latest");
        if (latest.exists() && !latest.delete()) {
            throw new IOException("Failed to delete " + latest);
        }

        ProcessBuilder pb = new ProcessBuilder();
        pb.command("ln", "-s", hpi.getLatest().version, "latest");
        pb.directory(dir);
        try {
            int r = pb.start().waitFor();
            if (r != 0) {
                throw new IOException("ln failed: " + r); // TODO better logging
            }
        } catch (InterruptedException ex) {
            LOGGER.log(Level.WARNING, "Failed to link ");
        }
    }

    /**
     * Stages an artifact into the specified location.
     */
    protected void stage(MavenArtifact a, File dst) throws IOException {
        File src = a.resolve();
        if (dst.exists() && dst.lastModified() == src.lastModified() && dst.length() == src.length()) {
            LOGGER.log(Level.FINEST, () -> "Destination file " + dst + " for artifact " + a + " already exists");
            return;   // already up to date
        }

        // TODO: directory and the war file should have the release timestamp
        final File parentFile = dst.getParentFile();
        if (!parentFile.mkdirs() && !parentFile.isDirectory()) {
            throw new IOException("Failed to create " + parentFile);
        }

        ProcessBuilder pb = new ProcessBuilder();
        pb.command("ln", "-f", src.getAbsolutePath(), dst.getAbsolutePath());
        Process p = pb.start();
        try {
            if (p.waitFor() != 0) {
                throw new IOException("'ln -f " + src.getAbsolutePath() + " " + dst.getAbsolutePath() +
                        "' failed with code " + p.exitValue() + "\nError: " + IOUtils.toString(p.getErrorStream()) + "\nOutput: " + IOUtils.toString(p.getInputStream()));
            } else {
                LOGGER.log(Level.INFO, "Created new download file " + dst + " from " + src);
            }
        } catch (InterruptedException ex) {
            LOGGER.log(Level.WARNING, "Interrupted creating " + dst + " from " + src, ex);
        }

    }

    private void buildIndex(File dir, String title, Collection<? extends MavenArtifact> versions, String permalink) throws IOException {
        List<MavenArtifact> list = new ArrayList<>(versions);
        list.sort((o1, o2) -> -o1.getVersion().compareTo(o2.getVersion()));

        try (IndexHtmlBuilder index = new IndexHtmlBuilder(dir, title)) {
            index.add(permalink, "permalink to the latest");
            for (MavenArtifact a : list) {
                index.add(a);
            }
        }
    }
}
