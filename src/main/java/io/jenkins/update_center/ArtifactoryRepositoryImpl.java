package io.jenkins.update_center;

import com.alibaba.fastjson.JSON;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.update_center.util.Environment;
import io.jenkins.update_center.util.HttpHelper;
import java.util.Iterator;
import java.util.function.Predicate;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ArtifactoryRepositoryImpl extends BaseMavenRepository {
    private static final Logger LOGGER = Logger.getLogger(ArtifactoryRepositoryImpl.class.getName());

    private static final String ARTIFACTORY_URL = Environment.getString("ARTIFACTORY_URL", "https://repo.jenkins-ci.org/");
    private static final String ARTIFACTORY_API_URL = Environment.getString("ARTIFACTORY_API_URL", "https://repo.jenkins-ci.org/api/");
    private static final String ARTIFACTORY_REPOSITORY = Environment.getString("ARTIFACTORY_REPOSITORY", "releases");

    private static final String ARTIFACTORY_AQL_URL = ARTIFACTORY_API_URL + "search/aql";
    private static final String ARTIFACTORY_MANIFEST_URL = ARTIFACTORY_URL + "%s/%s!/META-INF/MANIFEST.MF";
    private static final String ARTIFACTORY_ZIP_ENTRY_URL = ARTIFACTORY_URL + "%s/%s!%s";
    private static final String ARTIFACTORY_FILE_URL = ARTIFACTORY_URL + "%s/%s";

    private static final String AQL_QUERY = "items.find({\"repo\":{\"$eq\":\"" + ARTIFACTORY_REPOSITORY + "\"},\"$or\":[{\"name\":{\"$match\":\"*.hpi\"}},{\"name\":{\"$match\":\"*.jpi\"}},{\"name\":{\"$match\":\"*.war\"}},{\"name\":{\"$match\":\"*.pom\"}}]}).include(\"repo\", \"path\", \"name\", \"modified\", \"created\", \"sha256\", \"actual_sha1\", \"size\")";

    private final String username;
    private final String password;

    private File cacheDirectory = new File(Environment.getString("ARTIFACTORY_CACHEDIR", "caches/artifactory"));

    private boolean initialized = false;

    private Map<String, JsonFile> files = new HashMap<>();
    private Set<ArtifactCoordinates> plugins;
    private Set<ArtifactCoordinates> wars;
    private Set<ArtifactCoordinates> poms;

    public ArtifactoryRepositoryImpl(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    protected Set<ArtifactCoordinates> listAllJenkinsWars(String groupId) throws IOException {
        ensureInitialized();
        return wars;
    }

    private static boolean containsIllegalChars(String test) {
        return !test.chars().allMatch(c ->
                c >= '0' && c <= '9'
                || c >= 'A' && c <= 'Z'
                || c >= 'a' && c <= 'z'
                || c == '+' || c == '-' || c == '.' || c == '/' || c == '_'
        );
    }

    private static ArtifactCoordinates toGav(JsonFile f) {
        String fileName = f.name;
        String path = f.path;

        if (containsIllegalChars(fileName) || containsIllegalChars(path)) {
            LOGGER.log(Level.INFO, "Characters outside allowed set: " + f.path + " / " + f.name);
            return null;
        }

        int gaToV = path.lastIndexOf('/');
        if (gaToV <= 0) {
            LOGGER.log(Level.INFO, "Unexpected path/name: " + f.path + " / " + f.name);
            return null;
        }
        String version = path.substring(gaToV + 1);
        String ga = path.substring(0, gaToV);

        int gToA = ga.lastIndexOf('/');
        if (gToA <= 0) {
            LOGGER.log(Level.INFO, "Unexpected path/name: " + f.path + " / " + f.name);
            return null;
        }
        String artifactId = ga.substring(gToA + 1);
        String groupId = ga.substring(0, gToA).replace('/', '.');

        final int baseNameToExtension = fileName.lastIndexOf('.');
        String extension = fileName.substring(baseNameToExtension + 1);
        String baseName = fileName.substring(0, baseNameToExtension);

        final String expectedFileName = artifactId + "-" + version + "." + extension;
        if (!fileName.equals(expectedFileName)) {
            LOGGER.log(Level.INFO, "File name: " + fileName + " does not match expected file name: " + expectedFileName);
            return null;
        }

        final int classifierBeginIndex = artifactId.length() + 1 + version.length() + 1;
        if (classifierBeginIndex < baseName.length()) {
            LOGGER.log(Level.INFO, "Unexpectedly have classifier for path: " + path + " name: " + fileName);
            return null;
        }
        return new ArtifactCoordinates(groupId, artifactId, version, extension);
    }

    @Override
    public Collection<ArtifactCoordinates> listAllPlugins() throws IOException {
        ensureInitialized();
        return plugins;
    }

    private static class JsonFile {
        public String path; // example: org/acme/whatever/1.0
        public String name; // example: whatever-1.0.jar
        public String actual_sha1; // base64
        public String sha256; // base64
        public Date modified;
        // TODO record 'created' date and warn about large discrepancies
        public long size; // bytes
    }

    private static class JsonResponse {
        public List<JsonFile> results;
    }

    private Map<String, String> cache = new HashMap<>();

    private static final int CACHE_ENTRY_MAX_LENGTH = 1024 * 64;

    private void initialize() throws IOException {
        if (initialized) {
            throw new IllegalStateException("re-initialized");
        }
        LOGGER.log(Level.INFO, "Initializing " + this.getClass().getName());

        OkHttpClient client = new OkHttpClient.Builder().build();
        Request request = new Request.Builder().url(ARTIFACTORY_AQL_URL).addHeader("Authorization", Credentials.basic(username, password)).post(RequestBody.create(AQL_QUERY, MediaType.parse("text/plain; charset=utf-8"))).build();
        try (final ResponseBody body = HttpHelper.body(client.newCall(request).execute())) {
            final MediaType mediaType = body.contentType();
            JsonResponse json = JSON.parseObject(body.byteStream(), mediaType == null ? StandardCharsets.UTF_8 : mediaType.charset(), JsonResponse.class);
            json.results.forEach(it -> this.files.put("/" + it.path + "/" + it.name, it));
        }
        this.poms = this.files.values().stream().filter(it -> it.name.endsWith(".pom")).map(ArtifactoryRepositoryImpl::toGav).filter(Objects::nonNull).collect(Collectors.toSet());
        this.plugins = this.files.values().stream().filter(it -> it.name.endsWith(".hpi") || it.name.endsWith(".jpi")).map(ArtifactoryRepositoryImpl::toGav).filter(Objects::nonNull).collect(Collectors.toSet());
        removeIf(this.plugins, it -> !this.poms.contains(new ArtifactCoordinates(it.groupId, it.artifactId, it.version, "pom")));
        this.wars = this.files.values().stream().filter(it -> it.name.endsWith(".war")).map(ArtifactoryRepositoryImpl::toGav).collect(Collectors.toSet());
        removeIf(this.wars, it -> !this.poms.contains(new ArtifactCoordinates(it.groupId, it.artifactId, it.version, "pom")));
        LOGGER.log(Level.INFO, "Initialized " + this.getClass().getName());
    }

    // We cannot use Collection#removeIf because we want to log this
    private static <E> void removeIf(Collection<E> collection, Predicate<? super E> filter) {
        final Iterator<E> each = collection.iterator();
        while (each.hasNext()) {
            final E current = each.next();
            if (filter.test(current)) {
                each.remove();
                LOGGER.log(Level.INFO, "Removing artifact file without corresponding pom file: " + current);
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
    @SuppressFBWarnings(value="DCN_NULLPOINTER_EXCEPTION",
                        justification="Catching NPE is safer than trying to guard all cases")
    public ArtifactMetadata getMetadata(MavenArtifact artifact) throws IOException {
        ensureInitialized();
        ArtifactMetadata ret = new ArtifactMetadata();
        final JsonFile jsonFile = files.get("/" + getUri(artifact.artifact));
        try {
            ret.sha1 = hexToBase64(jsonFile.actual_sha1);
        } catch (NullPointerException e) {
            LOGGER.log(Level.WARNING, "No artifact: " + artifact.toString());
            return null;
        }
        String hexSha256 = jsonFile.sha256;
        if (hexSha256 != null) {
            ret.sha256 = hexToBase64(hexSha256);
        } else {
            LOGGER.log(Level.WARNING, "No SHA-256: " + artifact.toString());
            return null;
        }
        ret.timestamp = jsonFile.modified.getTime();
        ret.size = jsonFile.size;
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
        String filename = basename + "." + a.packaging;
        return a.groupId.replace(".", "/") + "/" + a.artifactId + "/" + a.version + "/" + filename;
    }

    @Override
    public Manifest getManifest(MavenArtifact artifact) throws IOException {
        try (InputStream is = getFileContent(String.format(ARTIFACTORY_MANIFEST_URL, ARTIFACTORY_REPOSITORY, getUri(artifact.artifact)))) {
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

    private File getFile(final String url) throws IOException {
        final String path = new URL(url).getPath();
        final String sha256 = DigestUtils.sha256Hex(path);
        final String sha256prefix = sha256.substring(0, 2); // to limit number of files in top-level directory
        final File cachePrefixDir = new File(cacheDirectory, sha256prefix);
        if (!cachePrefixDir.exists() && !cachePrefixDir.mkdirs()) {
            LOGGER.log(Level.WARNING, "Failed to create cache prefix directory " + cachePrefixDir);
        }
        File cacheFile = new File(cachePrefixDir, sha256);

        if (!cacheFile.exists()) {
            // High log level, but during regular operation this will indicate when an artifact is newly picked up, so useful to know.
            LOGGER.log(Level.INFO, "Downloading : " + url + " (not found in cache) to " + cacheFile.getName());

            final File parentFile = cacheFile.getParentFile();
            if (!parentFile.mkdirs() && !parentFile.isDirectory()) {
                throw new IllegalStateException("Failed to create non-existing directory " + parentFile);
            }
            try {
                OkHttpClient.Builder builder = new OkHttpClient.Builder();
                OkHttpClient client = builder.build();
                Request request = new Request.Builder().addHeader("Authorization", Credentials.basic(username, password)).url(url).get().build();
                final Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    try (final ResponseBody body = HttpHelper.body(response)) {
                        try (InputStream inputStream = body.byteStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream(); FileOutputStream fos = new FileOutputStream(cacheFile); TeeOutputStream tos = new TeeOutputStream(fos, baos)) {
                            IOUtils.copy(inputStream, tos);
                            if (baos.size() <= CACHE_ENTRY_MAX_LENGTH) {
                                final String value = baos.toString("UTF-8");
                                LOGGER.log(Level.FINE, () -> "Caching in memory: " + url + " with content: " + value);
                                this.cache.put(url, value);
                            }
                        }
                    }
                } else {
                    LOGGER.log(Level.INFO, "Received HTTP error response: " + response.code() + " for URL: " + url);
                    if (!cacheFile.mkdir()) {
                        LOGGER.log(Level.WARNING, "Failed to create cache 'not found' directory" + cacheFile);
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
        return getFileContent(String.format(ARTIFACTORY_ZIP_ENTRY_URL, ARTIFACTORY_REPOSITORY, getUri(artifact.artifact), StringUtils.prependIfMissing(path, "/")));
    }

    @Override
    public File resolve(ArtifactCoordinates artifact) throws IOException {
        /* Support loading files from local Maven repository to reduce redundancy */
        final String uri = getUri(artifact);
        final File localFile = new File(LOCAL_REPO, uri);
        if (localFile.exists()) {
            return localFile;
        }
        return getFile(String.format(ARTIFACTORY_FILE_URL, ARTIFACTORY_REPOSITORY, uri));
    }

    private static final File LOCAL_REPO = new File(new File(System.getProperty("user.home")), ".m2/repository");
}
