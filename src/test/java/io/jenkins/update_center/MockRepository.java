package io.jenkins.update_center;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.output.NullOutputStream;

/**
 * Mock repository implementation.
 * All files need to manually be created, i.e., for each release .pom and .war/.hpi files.
 */
public class MockRepository extends BaseMavenRepository {

    private Map<ArtifactCoordinates, File> filesByCoordinates = new HashMap<>();

    public void addFile(ArtifactCoordinates coordinates, File file) {
        this.filesByCoordinates.put(coordinates, file);
    }

    @Override
    protected Set<ArtifactCoordinates> listAllJenkinsWars(String groupId) {
        return filesByCoordinates.keySet().stream()
                .filter(c -> "war".equals(c.packaging))
                .filter(c -> c.groupId.equals(groupId))
                .collect(Collectors.toSet());
    }

    @Override
    public Collection<ArtifactCoordinates> listAllPlugins() {
        return filesByCoordinates.keySet().stream()
                .filter(c -> "hpi".equals(c.packaging))
                .collect(Collectors.toSet());
    }

    @Override
    public ArtifactMetadata getMetadata(MavenArtifact artifact) throws IOException {
        ArtifactMetadata metadata = new ArtifactMetadata();
        final File resolvedFile = resolve(artifact.artifact);
        metadata.timestamp = resolvedFile.lastModified();
        metadata.size = resolvedFile.length();
        try {
            var sha1 = MessageDigest.getInstance("SHA-1");
            var sha256 = MessageDigest.getInstance("SHA-256");

            try (DigestOutputStream sha1Stream = new DigestOutputStream(NullOutputStream.INSTANCE, sha1); DigestOutputStream sha256Stream = new DigestOutputStream(sha1Stream, sha256)) {
                Files.copy(resolvedFile.toPath(), sha256Stream);
            }

            metadata.sha1 = Base64.getEncoder().encodeToString(sha1.digest());
            metadata.sha256 = Base64.getEncoder().encodeToString(sha256.digest());
        } catch (NoSuchAlgorithmException ex) {
            // impossible
            throw new IOException(ex);
        }
        return metadata;
    }

    @Override
    public Manifest getManifest(MavenArtifact artifact) throws IOException {
        try (InputStream entry = getZipFileEntry(artifact, "META-INF/MANIFEST.MF")) {
            return new Manifest(entry);
        }
    }

    @Override
    public InputStream getZipFileEntry(MavenArtifact artifact, String path) throws IOException {
        // Strip leading slashes
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        File manifestFile = resolve(artifact.artifact);
        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(manifestFile));
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (entry.getName().equals(path)) {
                final byte[] bytes = zipInputStream.readAllBytes();
                return new ByteArrayInputStream(bytes);
            }
        }
        throw new IOException("Entry not found: " + path);
    }

    @Override
    public File resolve(ArtifactCoordinates artifact) throws IOException {
        File file = filesByCoordinates.get(artifact);
        if (file == null) {
            throw new IOException("Artifact not found in mock repository: " + artifact);
        }
        return file;
    }
}
