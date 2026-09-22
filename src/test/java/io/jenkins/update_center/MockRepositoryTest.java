package io.jenkins.update_center;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class MockRepositoryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private MockRepository repo;
    private File simplePlugin10Hpi;
    private File simplePlugin20Hpi;
    private File anotherPlugin15Hpi;
    private File jenkinsWar250File;
    private File simplePlugin10Pom;
    private File simplePlugin20Pom;
    private File anotherPlugin15Pom;
    private File jenkinsWar250Pom;

    private ArtifactCoordinates simplePlugin10Coords;
    private ArtifactCoordinates simplePlugin20Coords;
    private ArtifactCoordinates anotherPlugin15Coords;
    private ArtifactCoordinates jenkinsWar250Coords;

    @Before
    public void setUp() throws Exception {
        repo = new MockRepository();

        simplePlugin10Coords = new ArtifactCoordinates("io.jenkins.plugins", "simple-plugin", "1.0", "hpi");
        simplePlugin20Coords = new ArtifactCoordinates("io.jenkins.plugins", "simple-plugin", "2.0", "hpi");
        anotherPlugin15Coords = new ArtifactCoordinates("io.jenkins.plugins", "another-plugin", "1.5", "hpi");
        jenkinsWar250Coords = new ArtifactCoordinates("org.jenkins-ci.main", "jenkins-war", "2.450", "war");

        simplePlugin10Hpi = buildHpi("simple-plugin", "1.0");
        simplePlugin20Hpi = buildHpi("simple-plugin", "2.0");
        anotherPlugin15Hpi = buildHpi("another-plugin", "1.5");
        jenkinsWar250File = buildWar("jenkins-war", "2.450");

        simplePlugin10Pom = copyResourceToTemp("mock-repo/simple-plugin/1.0/pom.xml", "simple-plugin-1.0.pom");
        simplePlugin20Pom = copyResourceToTemp("mock-repo/simple-plugin/2.0/pom.xml", "simple-plugin-2.0.pom");
        anotherPlugin15Pom = copyResourceToTemp("mock-repo/another-plugin/1.5/pom.xml", "another-plugin-1.5.pom");
        jenkinsWar250Pom = copyResourceToTemp("mock-repo/jenkins-war/2.450/pom.xml", "jenkins-war-2.450.pom");

        repo.addFile(simplePlugin10Coords, simplePlugin10Hpi);
        repo.addFile(simplePlugin20Coords, simplePlugin20Hpi);
        repo.addFile(anotherPlugin15Coords, anotherPlugin15Hpi);
        repo.addFile(jenkinsWar250Coords, jenkinsWar250File);

        ArtifactCoordinates simplePlugin10PomCoords = new ArtifactCoordinates("io.jenkins.plugins", "simple-plugin", "1.0", "pom");
        ArtifactCoordinates simplePlugin20PomCoords = new ArtifactCoordinates("io.jenkins.plugins", "simple-plugin", "2.0", "pom");
        ArtifactCoordinates anotherPlugin15PomCoords = new ArtifactCoordinates("io.jenkins.plugins", "another-plugin", "1.5", "pom");
        ArtifactCoordinates jenkinsWar250PomCoords = new ArtifactCoordinates("org.jenkins-ci.main", "jenkins-war", "2.450", "pom");

        repo.addFile(simplePlugin10PomCoords, simplePlugin10Pom);
        repo.addFile(simplePlugin20PomCoords, simplePlugin20Pom);
        repo.addFile(anotherPlugin15PomCoords, anotherPlugin15Pom);
        repo.addFile(jenkinsWar250PomCoords, jenkinsWar250Pom);

        // HPI.getDescription() resolves index.jelly using "jar" packaging
        ArtifactCoordinates simplePlugin10JarCoords = new ArtifactCoordinates("io.jenkins.plugins", "simple-plugin", "1.0", "jar");
        ArtifactCoordinates simplePlugin20JarCoords = new ArtifactCoordinates("io.jenkins.plugins", "simple-plugin", "2.0", "jar");
        ArtifactCoordinates anotherPlugin15JarCoords = new ArtifactCoordinates("io.jenkins.plugins", "another-plugin", "1.5", "jar");
        repo.addFile(simplePlugin10JarCoords, simplePlugin10Hpi);
        repo.addFile(simplePlugin20JarCoords, simplePlugin20Hpi);
        repo.addFile(anotherPlugin15JarCoords, anotherPlugin15Hpi);
    }

    @Test
    public void listAllPluginsReturnsOnlyHpiArtifacts() {
        Collection<ArtifactCoordinates> plugins = repo.listAllPlugins();
        assertThat(plugins, hasSize(3));
        assertThat(plugins, hasItem(simplePlugin10Coords));
        assertThat(plugins, hasItem(simplePlugin20Coords));
        assertThat(plugins, hasItem(anotherPlugin15Coords));
        assertThat(plugins, not(hasItem(jenkinsWar250Coords)));
    }

    @Test
    public void listAllJenkinsWarsReturnsOnlyWarArtifacts() {
        Collection<ArtifactCoordinates> wars = repo.listAllJenkinsWars("org.jenkins-ci.main");
        assertThat(wars, hasSize(1));
        assertThat(wars, hasItem(jenkinsWar250Coords));
    }

    @Test
    public void listAllJenkinsWarsDoesNotReturnHpis() {
        Collection<ArtifactCoordinates> wars = repo.listAllJenkinsWars("io.jenkins.plugins");
        assertThat(wars, empty());
    }

    @Test
    public void resolveReturnsCorrectFile() throws IOException {
        assertEquals(simplePlugin10Hpi, repo.resolve(simplePlugin10Coords));
        assertEquals(jenkinsWar250File, repo.resolve(jenkinsWar250Coords));
    }

    @Test
    public void getManifestParsesCorrectly() throws IOException {
        MavenArtifact artifact = new MavenArtifact(repo, simplePlugin10Coords);
        Manifest manifest = repo.getManifest(artifact);
        assertNotNull(manifest);
        assertEquals("1.0", manifest.getMainAttributes().getValue("Plugin-Version"));
        assertEquals("2.401.1", manifest.getMainAttributes().getValue("Jenkins-Version"));
        assertEquals("simple-plugin", manifest.getMainAttributes().getValue("Short-Name"));
    }

    @Test
    public void getManifestForWar() throws IOException {
        MavenArtifact artifact = new MavenArtifact(repo, jenkinsWar250Coords);
        Manifest manifest = repo.getManifest(artifact);
        assertNotNull(manifest);
        assertEquals("2.450", manifest.getMainAttributes().getValue("Jenkins-Version"));
    }

    @Test
    public void getZipFileEntryReturnsIndexJelly() throws IOException {
        MavenArtifact artifact = new MavenArtifact(repo, simplePlugin10Coords);
        try (InputStream is = repo.getZipFileEntry(artifact, "index.jelly")) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content, containsString("simple test plugin that does nothing useful"));
        }
    }

    @Test
    public void getZipFileEntryStripsLeadingSlash() throws IOException {
        MavenArtifact artifact = new MavenArtifact(repo, simplePlugin10Coords);
        try (InputStream is = repo.getZipFileEntry(artifact, "/META-INF/MANIFEST.MF")) {
            Manifest m = new Manifest(is);
            assertEquals("simple-plugin", m.getMainAttributes().getValue("Short-Name"));
        }
    }

    @Test(expected = IOException.class)
    public void getZipFileEntryThrowsForMissingEntry() throws IOException {
        MavenArtifact artifact = new MavenArtifact(repo, simplePlugin10Coords);
        repo.getZipFileEntry(artifact, "nonexistent-file.txt");
    }

    @Test
    public void getMetadataComputesChecksums() throws IOException {
        MavenArtifact artifact = new MavenArtifact(repo, simplePlugin10Coords);
        MavenRepository.ArtifactMetadata metadata = repo.getMetadata(artifact);
        assertNotNull(metadata);
        assertNotNull(metadata.sha1);
        assertNotNull(metadata.sha256);
        assertTrue(metadata.sha1.length() > 0);
        assertTrue(metadata.sha256.length() > 0);
        assertTrue(metadata.size > 0);
        assertTrue(metadata.timestamp > 0);
    }

    @Test
    public void getMetadataChecksumsAreStableForSameFile() throws IOException {
        MavenArtifact artifact = new MavenArtifact(repo, simplePlugin10Coords);
        MavenRepository.ArtifactMetadata first = repo.getMetadata(artifact);
        MavenRepository.ArtifactMetadata second = repo.getMetadata(artifact);
        assertEquals(first.sha1, second.sha1);
        assertEquals(first.sha256, second.sha256);
    }

    @Test
    public void getMetadataChecksumsAreDifferentForDifferentFiles() throws IOException {
        MavenArtifact artifact1 = new MavenArtifact(repo, simplePlugin10Coords);
        MavenArtifact artifact2 = new MavenArtifact(repo, simplePlugin20Coords);
        MavenRepository.ArtifactMetadata meta1 = repo.getMetadata(artifact1);
        MavenRepository.ArtifactMetadata meta2 = repo.getMetadata(artifact2);
        assertNotEquals(meta1.sha256, meta2.sha256);
    }

    @Test
    public void listJenkinsPluginsGroupsByArtifactId() throws IOException {
        Collection<Plugin> plugins = repo.listJenkinsPlugins();
        assertThat(plugins, hasSize(2));
        boolean foundSimple = false;
        boolean foundAnother = false;
        for (Plugin p : plugins) {
            if ("simple-plugin".equals(p.getArtifactId())) {
                foundSimple = true;
                assertThat(p.getArtifacts().size(), is(2));
            } else if ("another-plugin".equals(p.getArtifactId())) {
                foundAnother = true;
                assertThat(p.getArtifacts().size(), is(1));
            }
        }
        assertTrue("simple-plugin found", foundSimple);
        assertTrue("another-plugin found", foundAnother);
    }

    @Test
    public void hpiGetNameFromPom() throws IOException {
        Collection<Plugin> plugins = repo.listJenkinsPlugins();
        Plugin simplePlugin = plugins.stream()
                .filter(p -> "simple-plugin".equals(p.getArtifactId()))
                .findFirst().orElseThrow();
        HPI latest = simplePlugin.getLatest();
        assertEquals("Simple", latest.getName());
    }

    @Test
    public void hpiGetRequiredJenkinsVersion() throws IOException {
        Collection<Plugin> plugins = repo.listJenkinsPlugins();
        Plugin simplePlugin = plugins.stream()
                .filter(p -> "simple-plugin".equals(p.getArtifactId()))
                .findFirst().orElseThrow();
        HPI latest = simplePlugin.getLatest();
        assertEquals("2.440.1", latest.getRequiredJenkinsVersion());
    }

    @Test
    public void hpiGetDependencies() throws IOException {
        Collection<Plugin> plugins = repo.listJenkinsPlugins();
        Plugin anotherPlugin = plugins.stream()
                .filter(p -> "another-plugin".equals(p.getArtifactId()))
                .findFirst().orElseThrow();
        HPI latest = anotherPlugin.getLatest();
        List<HPI.Dependency> deps = latest.getDependencies();
        assertThat(deps, hasSize(2));
        HPI.Dependency required = deps.stream().filter(d -> !d.optional).findFirst().orElseThrow();
        HPI.Dependency optional = deps.stream().filter(d -> d.optional).findFirst().orElseThrow();
        assertEquals("simple-plugin", required.name);
        assertEquals("1.0", required.version);
        assertEquals("credentials", optional.name);
        assertEquals("1337.v60b_d7b_c7b_c9f", optional.version);
    }

    @Test
    public void hpiGetDescription() throws IOException {
        Collection<Plugin> plugins = repo.listJenkinsPlugins();
        Plugin simplePlugin = plugins.stream()
                .filter(p -> "simple-plugin".equals(p.getArtifactId()))
                .findFirst().orElseThrow();
        HPI latest = simplePlugin.getLatest();
        String desc = latest.getDescription();
        assertThat(desc, containsString("improved"));
        assertThat(desc, containsString("<b>"));
    }

    @Test
    public void pluginLatestIsHighestVersion() throws IOException {
        Collection<Plugin> plugins = repo.listJenkinsPlugins();
        Plugin simplePlugin = plugins.stream()
                .filter(p -> "simple-plugin".equals(p.getArtifactId()))
                .findFirst().orElseThrow();
        assertEquals("2.0", simplePlugin.getLatest().version);
    }

    @Test
    public void pluginFirstIsLowestVersion() throws IOException {
        Collection<Plugin> plugins = repo.listJenkinsPlugins();
        Plugin simplePlugin = plugins.stream()
                .filter(p -> "simple-plugin".equals(p.getArtifactId()))
                .findFirst().orElseThrow();
        assertEquals("1.0", simplePlugin.getFirst().version);
    }

    @Test
    public void hpiGetNameFallsBackToArtifactId() throws IOException {
        File hpi = buildHpiWithManifestOnly("no-name-plugin", "1.0", "2.440.1");
        File pom = writePomWithoutName("no-name-plugin", "1.0");
        ArtifactCoordinates coords = new ArtifactCoordinates("io.jenkins.plugins", "no-name-plugin", "1.0", "hpi");
        ArtifactCoordinates pomCoords = new ArtifactCoordinates("io.jenkins.plugins", "no-name-plugin", "1.0", "pom");
        repo.addFile(coords, hpi);
        repo.addFile(pomCoords, pom);

        Plugin plugin = new Plugin("no-name-plugin");
        HPI hpiObj = new HPI(repo, coords, plugin);
        plugin.addArtifact(hpiObj);
        assertEquals("no-name-plugin", hpiObj.getName());
    }

    private File buildHpi(String artifactId, String version) throws IOException {
        String resourceBase = "mock-repo/" + artifactId + "/" + version + "/";
        String manifest = readResource(resourceBase + "MANIFEST.MF");
        String indexJelly = readResource(resourceBase + "index.jelly");

        File hpiFile = new File(tmp.getRoot(), artifactId + "-" + version + ".hpi");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(hpiFile))) {
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("index.jelly"));
            zos.write(indexJelly.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return hpiFile;
    }

    private File buildWar(String artifactId, String version) throws IOException {
        String resourceBase = "mock-repo/" + artifactId + "/" + version + "/";
        String manifest = readResource(resourceBase + "MANIFEST.MF");

        File warFile = new File(tmp.getRoot(), artifactId + "-" + version + ".war");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(warFile))) {
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return warFile;
    }

    private File buildHpiWithManifestOnly(String artifactId, String version, String jenkinsVersion) throws IOException {
        String manifest = "Manifest-Version: 1.0\n"
                + "Plugin-Version: " + version + "\n"
                + "Jenkins-Version: " + jenkinsVersion + "\n"
                + "Short-Name: " + artifactId + "\n"
                + "Long-Name: " + artifactId + "\n";

        File hpiFile = new File(tmp.getRoot(), artifactId + "-" + version + ".hpi");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(hpiFile))) {
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return hpiFile;
    }

    private File writePomWithoutName(String artifactId, String version) throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>io.jenkins.plugins</groupId>\n"
                + "    <artifactId>" + artifactId + "</artifactId>\n"
                + "    <version>" + version + "</version>\n"
                + "    <packaging>hpi</packaging>\n"
                + "</project>\n";
        File pomFile = new File(tmp.getRoot(), artifactId + "-" + version + ".pom");
        Files.writeString(pomFile.toPath(), pom, StandardCharsets.UTF_8);
        return pomFile;
    }

    private File copyResourceToTemp(String resourcePath, String fileName) throws IOException {
        File target = new File(tmp.getRoot(), fileName);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull("Resource " + resourcePath + " must exist", is);
            Files.copy(is, target.toPath());
        }
        return target;
    }

    private String readResource(String resourcePath) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull("Resource " + resourcePath + " must exist", is);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
