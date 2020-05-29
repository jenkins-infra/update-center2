/*
 * The MIT License
 *
 * Copyright (c) 2020, Daniel Beck
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.jenkins.update_center;

import hudson.util.VersionNumber;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
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

    private final Map<String, VersionNumber> latestVersions;

    private LatestPluginVersions(@Nonnull MavenRepository repository) throws IOException {
        this.latestVersions = repository.listJenkinsPlugins().stream().collect(Collectors.toMap(Plugin::getArtifactId, p -> p.getLatest().getVersion()));
    }

    private LatestPluginVersions(@Nonnull Map<String, VersionNumber> latestVersions) {
        this.latestVersions = latestVersions;
    }

    public static void initialize(@Nonnull MavenRepository repository) throws IOException {
        instance = new LatestPluginVersions(repository);
    }

    public static void initializeEmpty() {
        instance = new LatestPluginVersions(Collections.emptyMap());
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
