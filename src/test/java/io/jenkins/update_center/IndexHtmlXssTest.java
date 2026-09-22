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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class IndexHtmlXssTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private MockRepository repo;
    private String template;

    @Before
    public void setUp() throws Exception {
        repo = new MockRepository();
        template = Files.readString(
                new File(Main.resourcesDir, "index-template.html").toPath(), StandardCharsets.UTF_8);
    }

    @Test
    public void pomNameWithScriptTagProducesXssInGeneratedHtml() throws Exception {
        File outputDir = tmp.newFolder("xss-output");
        HPI hpi = createHpiFromResources("xss-plugin", "1.0");

        String name = hpi.getName();
        // DOM4J decodes the XML entities, so we get raw HTML/script
        assertEquals("</title><script>alert(1)</script>", name);

        // Now simulate what DirectoryTreeBuilder.buildIndex does: pass the name to IndexHtmlBuilder
        try (IndexHtmlBuilder builder = new IndexHtmlBuilder(outputDir, name, template)) {
            builder.add(hpi);
        }

        String html = Files.readString(new File(outputDir, "index.html").toPath(), StandardCharsets.UTF_8);

        assertThat(html, containsString("&lt;script&gt;alert"));
        assertThat(html, not(containsString("<script>alert")));
    }

    @Test
    public void pomNameWithQuotesIsEncoded() throws Exception {
        File outputDir = tmp.newFolder("quotes-output");
        HPI hpi = createHpiFromResources("quotes-plugin", "1.0");

        String name = hpi.getName();
        // DOM4J decodes &quot; to literal quote, simplifyPluginName strips "Jenkins" prefix and "Plugin" suffix
        assertEquals("Foo \"Bar\"", name);

        try (IndexHtmlBuilder builder = new IndexHtmlBuilder(outputDir, name, template)) {
            builder.add(hpi);
        }

        String html = Files.readString(new File(outputDir, "index.html").toPath(), StandardCharsets.UTF_8);

        assertThat(html, containsString("<title>Foo &quot;Bar&quot;</title>"));
        assertThat(html, containsString("Foo &quot;Bar&quot;</h1>"));

        assertThat(html, not(containsString("content='Foo \"Bar\"'")));
        assertThat(html, containsString("content='Foo &quot;Bar&quot;'"));
    }

    @Test
    public void pomNameWithSingleQuotesCannotBreakAttribute() throws Exception {
        File outputDir = tmp.newFolder("squote-output");
        HPI hpi = createHpiFromResources("squote-plugin", "1.0");

        String name = hpi.getName();
        // DOM4J decodes &apos; to literal single quote
        assertEquals("Foo' onmouseover='alert(1)", name);

        try (IndexHtmlBuilder builder = new IndexHtmlBuilder(outputDir, name, template)) {
            builder.add(hpi);
        }

        String html = Files.readString(new File(outputDir, "index.html").toPath(), StandardCharsets.UTF_8);

        assertThat(html, not(containsString("content='Foo' onmouseover='alert(1)'")));
        assertThat(html, not(containsString("content='Foo'")));
    }

    @Test
    public void urlInHrefAttributeIsEncoded() throws Exception {
        File outputDir = tmp.newFolder("url-href-output");

        String maliciousUrl = "https://example.com/plugins/foo/1.0' onclick='alert(1)/foo.hpi";

        try (IndexHtmlBuilder builder = new IndexHtmlBuilder(outputDir, "Test Plugin", template)) {
            builder.add(maliciousUrl, "1.0");
        }

        String html = Files.readString(new File(outputDir, "index.html").toPath(), StandardCharsets.UTF_8);

        // The single quote must not break out of href='...'
        assertThat(html, containsString("onclick=&apos;"));
        assertThat(html, not(containsString("href='https://example.com/plugins/foo/1.0' onclick")));
    }

    @Test
    public void simplifyPluginNameDoesNotSanitize() {
        assertEquals("</title><script>alert(1)</script>", HPI.simplifyPluginName("</title><script>alert(1)</script>"));
        assertEquals("<img src=x onerror=alert(1)>", HPI.simplifyPluginName("<img src=x onerror=alert(1)>"));
        assertEquals("\" onmouseover=\"alert(1)", HPI.simplifyPluginName("\" onmouseover=\"alert(1)"));
    }

    private HPI createHpiFromResources(String artifactId, String version) throws IOException {
        String resourceBase = "mock-repo/" + artifactId + "/" + version + "/";
        File hpiFile = buildHpi(artifactId, version, resourceBase);
        File pomFile = copyResourceToTemp(resourceBase + "pom.xml", artifactId + "-" + version + ".pom");

        ArtifactCoordinates hpiCoords = new ArtifactCoordinates("io.jenkins.plugins", artifactId, version, "hpi");
        ArtifactCoordinates pomCoords = new ArtifactCoordinates("io.jenkins.plugins", artifactId, version, "pom");
        ArtifactCoordinates jarCoords = new ArtifactCoordinates("io.jenkins.plugins", artifactId, version, "jar");
        repo.addFile(hpiCoords, hpiFile);
        repo.addFile(pomCoords, pomFile);
        repo.addFile(jarCoords, hpiFile);

        Plugin plugin = new Plugin(artifactId);
        HPI hpi = new HPI(repo, hpiCoords, plugin);
        plugin.addArtifact(hpi);
        return hpi;
    }

    private File buildHpi(String artifactId, String version, String resourceBase) throws IOException {
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
