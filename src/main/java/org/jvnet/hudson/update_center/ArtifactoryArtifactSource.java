package org.jvnet.hudson.update_center;

import com.google.gson.Gson;
import net.sf.json.JSONException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
        try {
            ret.sha256 = hexToBase64(files.get("/" + getUri(artifact)).sha2);
        } catch (JSONException e) {
            // not all files have sha256
            System.out.println("No SHA-256: " + artifact.toString());
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
        String urlBase64 = Base64.encodeBase64String(url.getBytes());
        File cache = new File(cacheDirectory, urlBase64);
        if (!cache.exists()) {
            cache.getParentFile().mkdirs();
            HttpClient client = new HttpClient();
            GetMethod get = new GetMethod(url);
            client.executeMethod(get);
            InputStream stream = get.getResponseBodyAsStream();
            IOUtils.copy(stream, new FileOutputStream(cache));
            stream.close();
        }
        return new FileInputStream(cache);
    }
}
