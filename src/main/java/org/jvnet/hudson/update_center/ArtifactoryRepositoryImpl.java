package org.jvnet.hudson.update_center;

import com.google.gson.Gson;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class ArtifactoryRepositoryImpl extends BaseMavenRepository {

    private static final String ARTIFACTORY_URL = "https://repo.jenkins-ci.org/";
    private static final String ARTIFACTORY_API_URL = "https://repo.jenkins-ci.org/api/";
    private static final String ARTIFACTORY_AQL_URL = ARTIFACTORY_API_URL + "search/aql";
    private static final String ARTIFACTORY_MANIFEST_URL = ARTIFACTORY_URL + "%s/%s!/META-INF/MANIFEST.MF";
    private static final String ARTIFACTORY_ZIP_ENTRY_URL = ARTIFACTORY_URL + "%s/%s!%s";
    private static final String ARTIFACTORY_FILE_URL = ARTIFACTORY_URL + "%s/%s";

    private static final String AQL_QUERY = "items.find({\"repo\":{\"$eq\":\"releases\"},\"$or\":[{\"name\":{\"$match\":\"*.hpi\"}},{\"name\":{\"$match\":\"*.jpi\"}},{\"name\":{\"$match\":\"*.war\"}}]}).include(\"repo\", \"path\", \"name\", \"modified\", \"created\", \"sha256\", \"actual_sha1\")";

    private final String username;
    private final String password;

    private File cacheDirectory = new File("artifactoryFileCache");

    private boolean initialized = false;

    private Map<String, GsonFile> files = new HashMap<>();
    private Set<ArtifactCoordinates> plugins;
    private Set<ArtifactCoordinates> wars;

    public ArtifactoryRepositoryImpl(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    protected Set<ArtifactCoordinates> listAllJenkinsWars(String groupId) throws IOException {
        ensureInitialized();
        return wars;
    }

    private static boolean isPrintableAscii(String test) {
        return test.chars().allMatch(c -> c >= 0x20 && c < 0x7F);
    }

    private static ArtifactCoordinates coordinatesFromGsonFile(GsonFile f) {
        String fileName = f.name;
        String path = f.path;

        if (!isPrintableAscii(fileName) || !isPrintableAscii(path)) {
            System.out.println("coordinatesFromGsonFile: Not only printable ascii: " + f.path + " / " + f.name);
            return null;
        }

        int gaToV = path.lastIndexOf('/');
        if (gaToV <= 0) {
            System.out.println("coordinatesFromGsonFile: Unexpected path/name: " + f.path + " / " + f.name);
            return null;
        }
        String version = path.substring(gaToV + 1);
        String ga = path.substring(0, gaToV);

        int gToA = ga.lastIndexOf('/');
        if (gToA <= 0) {
            System.out.println("coordinatesFromGsonFile: Unexpected path/name: " + f.path + " / " + f.name);
            return null;
        }
        String artifactId = ga.substring(gToA + 1);
        String groupId = ga.substring(0, gToA).replace('/', '.');

        final int baseNameToExtension = fileName.lastIndexOf('.');
        String extension = fileName.substring(baseNameToExtension + 1);
        String baseName = fileName.substring(0, baseNameToExtension);

        final String expectedFileName = artifactId + "-" + version + "." + extension;
        if (!fileName.equals(expectedFileName)) {
            System.out.println("coordinatesFromGsonFile: File name: " + fileName + " does not match expected file name: " + expectedFileName);
            return null;
        }

        final int classifierBeginIndex = artifactId.length() + 1 + version.length() + 1;
        String classifier = null;
        if (classifierBeginIndex < baseName.length()) {
            classifier = baseName.substring(classifierBeginIndex);
            System.out.println("coordinatesFromGsonFile: Unexpectedly have classifier for path: " + path + " name: " + fileName);
            return null;
        }
        return new ArtifactCoordinates(groupId, artifactId, version, extension, classifier, f.modified.getTime());
    }

    @Override
    public Collection<ArtifactCoordinates> listAllPlugins() throws IOException {
        ensureInitialized();
        return plugins;
    }

    private static class GsonFile {
        public String path; // example: org/acme/whatever/1.0
        public String name; // example: whatever-1.0.jar
        public String actual_sha1; // base64
        public String sha256; // base64
        public Date modified;
        public Date created;
    }

    private static class GsonResponse {
        public List<GsonFile> results;
    }

    private Map<String, String> cache = new HashMap<>();

    private static final int CACHE_ENTRY_MAX_LENGTH = 1024 * 50;

    private void initialize() throws IOException {
        if (initialized) {
            throw new IllegalStateException("re-initialized");
        }
        System.err.println("Initializing " + this.getClass().getName());

        OkHttpClient client = new OkHttpClient.Builder().build();
        Request request = new Request.Builder().url(ARTIFACTORY_AQL_URL).addHeader("Authorization", Credentials.basic(username, password)).post(RequestBody.create(AQL_QUERY, MediaType.parse("text/plain; charset=utf-8"))).build();
        Gson gson = new Gson();
        GsonResponse json = gson.fromJson(new InputStreamReader(client.newCall(request).execute().body().byteStream()), GsonResponse.class);
        json.results.stream().forEach(it -> this.files.put("/" + it.path + "/" + it.name, it));
        this.plugins = this.files.values().stream().filter(it -> it.name.endsWith(".hpi") || it.name.endsWith(".jpi")).map(ArtifactoryRepositoryImpl::coordinatesFromGsonFile).filter(Objects::nonNull).collect(Collectors.toSet());
        this.wars = this.files.values().stream().filter(it -> it.name.endsWith(".war")).map(ArtifactoryRepositoryImpl::coordinatesFromGsonFile).collect(Collectors.toSet());
        System.err.println("Initialized " + this.getClass().getName());
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
            ret.sha1 = hexToBase64(files.get("/" + getUri(artifact.artifact)).actual_sha1);
        } catch (NullPointerException e) {
            System.out.println("No artifact: " + artifact.toString());
            return null;
        }
        String hexSha256 = files.get("/" + getUri(artifact.artifact)).sha256;
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

    private String getUri(ArtifactCoordinates a) {
        String basename = a.artifactId + "-" + a.version;
        String filename;
        if (a.classifier != null) {
            filename = basename + "-" + a.classifier + "." + a.packaging;
        } else {
            filename = basename + "." + a.packaging;
        }
        String ret = a.groupId.replace(".", "/") + "/" + a.artifactId + "/" + a.version + "/" + filename;
        return ret;
    }

    @Override
    public Manifest getManifest(MavenArtifact artifact) throws IOException {
        try (InputStream is = getFileContent(String.format(ARTIFACTORY_MANIFEST_URL, "releases", getUri(artifact.artifact)))) {
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
        File cacheFile = getFile(url);
        return new FileInputStream(cacheFile);
    }

    private File getFile(String url) throws IOException {
        String urlBase64 = Base64.encodeBase64String(new URL(url).getPath().getBytes());
        File cacheFile = new File(cacheDirectory, urlBase64);
        if (!cacheFile.exists()) {
            System.err.println("Downloading: " + url);
            cacheFile.getParentFile().mkdirs();
            try {
                OkHttpClient.Builder builder = new OkHttpClient.Builder();
                OkHttpClient client = builder.build();
                Request request = new Request.Builder().url(url).get().build();
                try (InputStream stream = client.newCall(request).execute().body().byteStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream(); FileOutputStream fos = new FileOutputStream(cacheFile); TeeOutputStream tos = new TeeOutputStream(fos, baos)) {
                    IOUtils.copy(stream, tos);
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
        return cacheFile;
    }

    @Override
    public InputStream getZipFileEntry(MavenArtifact artifact, String path) throws IOException {
        return getFileContent(String.format(ARTIFACTORY_ZIP_ENTRY_URL, "releases", getUri(artifact.artifact), StringUtils.prependIfMissing(path, "/")));
    }

    @Override
    public File resolve(ArtifactCoordinates artifact) throws IOException {
        /* Support loading files from local Maven repository to reduce redundancy */
        final String uri = getUri(artifact);
        final File localFile = new File(LOCAL_REPO, uri);
        if (localFile.exists()) {
            return localFile;
        }
        return getFile(String.format(ARTIFACTORY_FILE_URL, "releases", uri));
    }

    private static final File LOCAL_REPO = new File(new File(System.getProperty("user.home")), ".m2/repository");
}
