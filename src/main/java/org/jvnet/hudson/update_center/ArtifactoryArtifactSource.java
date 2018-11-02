package org.jvnet.hudson.update_center;

import com.google.gson.Gson;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.filters.StringInputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

public class ArtifactoryArtifactSource extends ArtifactSource {
    private static final String ARTIFACTORY_URL = "https://repo.jenkins-ci.org/";
    private static final String ARTIFACTORY_API_URL = "https://repo.jenkins-ci.org/api/";
    private static final String ARTIFACTORY_LIST_URL = ARTIFACTORY_API_URL + "storage/releases/?list&deep=1";
    private static final String ARTIFACTORY_MANIFEST_URL = ARTIFACTORY_URL + "%s/%s!/META-INF/MANIFEST.MF";
    private static final String ARTIFACTORY_ZIP_ENTRY_URL = ARTIFACTORY_URL + "%s/%s!%s";

    private final String username;
    private final String password;

    private static ArtifactoryArtifactSource instance;

    private File cacheDirectory = new File("artifactoryFileCache");

    private boolean initialized = false;

    // the key is the URI within the repo, with leading /
    // example: /args4j/args4j/2.0.21/args4j-2.0.21-javadoc.jar
    private Map<String, GsonFile> files = new HashMap<>();

    public ArtifactoryArtifactSource(String username, String password) {
        this.username = username;
        this.password = password;
    }

    private static class GsonFile {
        public String uri;
        public String sha1;
        public String sha2;
    }

    private static class GsonResponse {
        public String uri;
        public Date created;
        public List<GsonFile> files;
    }

    private Map<String, String> cache = new HashMap<>();

    private static final int CACHE_ENTRY_MAX_LENGTH = 1024 * 50;

    private void initialize() throws IOException {
        if (initialized) {
            throw new IllegalStateException("re-initialized");
        }
        HttpClient client = new HttpClient();
        GetMethod get = new GetMethod(ARTIFACTORY_LIST_URL);
        get.addRequestHeader("Authorization", "Basic " + Base64.encodeBase64String((username + ":" + password).getBytes()));
        client.executeMethod(get);
        InputStream body = get.getResponseBodyAsStream();
        Gson gson = new Gson();
        GsonResponse json = gson.fromJson(new InputStreamReader(body), GsonResponse.class);
        for (GsonFile file : json.files) {
            String uri = file.uri;
            if (uri.endsWith(".hpi") || uri.endsWith(".jpi") || uri.endsWith(".war")) { // we only care about HPI (plugin) and WAR (core) files
                this.files.put(uri, file);
            }
        }
    }

    private String hexToBase64(String hex) throws IOException {
        try {
            byte[] decodedHex = Hex.decodeHex(hex);
            return Base64.encodeBase64String(decodedHex);
        } catch (DecoderException e) {
            throw new IOException("failed to convert hex to base64", e);
        }
    }

    @Override
    public Digests getDigests(MavenArtifact artifact) throws IOException {
        ensureInitialized();
        Digests ret = new Digests();
        try {
            ret.sha1 = hexToBase64(files.get("/" + getUri(artifact)).sha1);
        } catch (NullPointerException e) {
            System.out.println("No artifact: " + artifact.toString());
            return null;
        }
        String hexSha256 = files.get("/" + getUri(artifact)).sha2;
        if (hexSha256 != null) {
            ret.sha256 = hexToBase64(hexSha256);
        } else {
            System.out.println("No SHA-256: " + artifact.toString());
            return null;
        }
        return ret;
    }

    private void ensureInitialized() throws IOException {
        if (!initialized) {
            initialize();
            initialized = true;
        }
    }

    private String getUri(MavenArtifact a) {
        String basename = a.artifact.artifactId + "-" + a.artifact.version;
        String filename;
        if (a.artifact.classifier != null) {
            filename = basename + "-" + a.artifact.classifier + "." + a.artifact.packaging;
        } else {
            filename = basename + "." + a.artifact.packaging;
        }
        String ret = a.artifact.groupId.replace(".", "/") + "/" + a.artifact.artifactId + "/" + a.version + "/" + filename;
        return ret;
    }

    @Override
    public Manifest getManifest(MavenArtifact artifact) throws IOException {
        try (InputStream is = getFileContent(String.format(ARTIFACTORY_MANIFEST_URL, "releases", getUri(artifact)))) {
            return new Manifest(is);
        }
    }

    private InputStream getFileContent(String url) throws IOException {
        if (this.cache.containsKey(url)) {
            String entry = this.cache.get(url);
            if (entry == null) {
                throw new IOException("Failed to retrieve content of " + url + " (cached)");
            }
            return new StringInputStream(entry);
        }
        String urlBase64 = Base64.encodeBase64String(new URL(url).getPath().getBytes());
        File cacheFile = new File(cacheDirectory, urlBase64);
        if (!cacheFile.exists()) {
            cacheFile.getParentFile().mkdirs();
            try {
                HttpClient client = new HttpClient();
                GetMethod get = new GetMethod(url);
                client.executeMethod(get);
                if (get.getStatusCode() >= 400) {
                    cacheFile.mkdirs();
                    throw new IOException("Failed to retrieve content of " + url + ", got " + get.getStatusCode());
                }
                try (InputStream stream = get.getResponseBodyAsStream()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    IOUtils.copy(stream, new TeeOutputStream(new FileOutputStream(cacheFile), baos));
                    if (baos.size() <= CACHE_ENTRY_MAX_LENGTH) {
                        this.cache.put(url, baos.toString("UTF-8"));
                    }
                }
            } catch (RuntimeException e) {
                throw new IOException(e);
            }
        } else {
            if (cacheFile.isDirectory()) {
                // indicator that this is a cached error
                this.cache.put(url, null);
                throw new IOException("Failed to retrieve content of " + url + " (cached)");
            } else {
                // read from cached file
                if (cacheFile.length() <= CACHE_ENTRY_MAX_LENGTH) {
                    this.cache.put(url, FileUtils.readFileToString(cacheFile, StandardCharsets.UTF_8));
                }
            }
        }
        return new FileInputStream(cacheFile);
    }

    @Override
    public InputStream getZipFileEntry(MavenArtifact artifact, String path) throws IOException {
        return getFileContent(String.format(ARTIFACTORY_ZIP_ENTRY_URL, "releases", getUri(artifact), StringUtils.prependIfMissing(path, "/")));
    }
}
