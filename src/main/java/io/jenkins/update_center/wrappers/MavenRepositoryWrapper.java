package io.jenkins.update_center.wrappers;

import hudson.util.VersionNumber;
import io.jenkins.update_center.MavenRepository;
import io.jenkins.update_center.ArtifactCoordinates;
import io.jenkins.update_center.JenkinsWar;
import io.jenkins.update_center.MavenArtifact;
import io.jenkins.update_center.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Manifest;

public class MavenRepositoryWrapper implements MavenRepository {

    MavenRepository base;

    public MavenRepositoryWrapper withBaseRepository(MavenRepository base) {
        this.base = base;
        return this;
    }

    @Override
    public TreeMap<VersionNumber, JenkinsWar> getJenkinsWarsByVersionNumber() throws IOException {
        return base.getJenkinsWarsByVersionNumber();
    }

    @Override
    public void addWarsInGroupIdToMap(Map<VersionNumber, JenkinsWar> r, String groupId, VersionNumber cap) throws IOException {
        base.addWarsInGroupIdToMap(r, groupId, cap);
    }

    @Override
    public Collection<ArtifactCoordinates> listAllPlugins() throws IOException {
        return base.listAllPlugins();
    }

    @Override
    public ArtifactMetadata getMetadata(MavenArtifact artifact) throws IOException {
        return base.getMetadata(artifact);
    }

    @Override
    public Manifest getManifest(MavenArtifact artifact) throws IOException {
        return base.getManifest(artifact);
    }

    @Override
    public InputStream getZipFileEntry(MavenArtifact artifact, String path) throws IOException {
        return base.getZipFileEntry(artifact, path);
    }

    @Override
    public File resolve(ArtifactCoordinates artifact) throws IOException {
        return base.resolve(artifact);
    }

    @Override
    public Collection<Plugin> listJenkinsPlugins() throws IOException {
        return base.listJenkinsPlugins();
    }
}
