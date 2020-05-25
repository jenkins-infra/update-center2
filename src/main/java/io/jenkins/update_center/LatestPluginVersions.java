package io.jenkins.update_center;

import hudson.util.VersionNumber;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility class for including the latest published version of a plugin in update site metadata.
 *
 * <p>Limitations in update site tiering deliberately result in older releases being offered, and this additional
 * metadata allows informing users that the update site does not offer the very latest releases.</p>
 *
 * <p>Despite the name, unrelated to {@code latest/} directories created by {@link LatestLinkBuilder}.</p>
 */
public class LatestPluginVersions {
    private static LatestPluginVersions instance;

    private final MavenRepository repository;
    private final Map<String, VersionNumber> latestVersions;

    private LatestPluginVersions(@Nonnull MavenRepository repository) throws IOException {
        this.repository = repository;
        this.latestVersions = repository.listJenkinsPlugins().stream().collect(Collectors.toMap(p -> p.getArtifactId(), p -> p.getLatest().getVersion()));
    }

    public static void initialize(@Nonnull MavenRepository repository) throws IOException {
        instance = new LatestPluginVersions(repository);
    }

    @Nonnull
    public static LatestPluginVersions getInstance() {
        Objects.requireNonNull(instance, "instance");
        return instance;
    }

    public VersionNumber getLatestVersion(String pluginId) {
        return latestVersions.get(pluginId);
    }
}
