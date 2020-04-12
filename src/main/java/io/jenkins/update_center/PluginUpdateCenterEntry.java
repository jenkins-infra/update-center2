package io.jenkins.update_center;

import com.alibaba.fastjson.annotation.JSONField;
import hudson.util.VersionNumber;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import io.jenkins.update_center.util.JavaSpecificationVersion;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An entry of a plugin in the update center metadata.
 *
 */
public class PluginUpdateCenterEntry {
    /**
     * Plugin artifact ID.
     */
    @JSONField(name = "name")
    public final String artifactId;
    /**
     * Latest version of this plugin.
     */
    public transient final HPI latest;
    /**
     * Previous version of this plugin.
     */
    @CheckForNull
    public transient final HPI previous;

    private PluginUpdateCenterEntry(String artifactId, HPI latest, HPI previous) {
        this.artifactId = artifactId;
        this.latest = latest;
        this.previous = previous;
    }

    public PluginUpdateCenterEntry(Plugin plugin) {
        this.artifactId = plugin.getArtifactId();
        HPI previous = null, latest = null;

        Iterator<HPI> it = plugin.getArtifacts().values().iterator();

        while (latest == null && it.hasNext()) {
            HPI h = it.next();
            try {
                h.getManifest();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to resolve "+h+". Dropping this version.",e);
                continue;
            }
            latest = h;
        }

        while (previous == null && it.hasNext()) {
            HPI h = it.next();
            try {
                h.getManifest();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to resolve "+h+". Dropping this version.",e);
                continue;
            }
            previous = h;
        }

        this.latest = latest;
        this.previous = previous == latest ? null : previous;
    }

    public PluginUpdateCenterEntry(HPI hpi) {
        this(hpi.artifact.artifactId, hpi,  null);
    }

    @JSONField
    public String getWiki() {
        return "https://plugins.jenkins.io/" + artifactId;
    }

    String getPluginUrl() throws IOException {
        return latest.getPluginUrl();
    }

    @JSONField(name = "url")
    public URL getDownloadUrl() throws MalformedURLException {
        return latest.getDownloadUrl();
    }

    @JSONField(name = "title")
    public String getName() throws IOException {
        return latest.getName();
    }

    private static Map<ArtifactCoordinates, JSONObject> toJsonCache = new HashMap<>();

    /**
     * Converts the plugin definition to JSON.
     * @return Generated JSON
     * @throws Exception Generation error, e.g. Manifest read failure
     */
    public JSONObject toJSON() throws Exception {
        final JSONObject jsonObject = toJsonCache.computeIfAbsent(latest.artifact, unused -> computeJSON());
        if (jsonObject == null) {
            throw new IOException("Failed to serialize " + latest.artifact.artifactId);
        }
        return jsonObject;
    }

    private JSONObject computeJSON() {
        try {
            JSONObject json = latest.toJSON(artifactId);
            if (json == null) {
                return null;
            }

            SimpleDateFormat fisheyeDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'.00Z'", Locale.US);
            json.put("releaseTimestamp", fisheyeDateFormatter.format(latest.getTimestamp()));
            if (previous != null) {
                // TODO this isn't cleanly cachable -- e.g. an experimental update site may have a beta here but we cached an earlier non-beta release
                json.put("previousVersion", previous.version);
                json.put("previousTimestamp", fisheyeDateFormatter.format(previous.getTimestamp()));
            }

            json.put("title", getName());
            String scm = latest.getScmUrl();
            if (scm != null) {
                json.put("scm", scm);
            }

            json.put("wiki", "https://plugins.jenkins.io/" + artifactId);
            json.put("labels", latest.getLabels());

            String description = latest.getDescription();

            json.put("excerpt", description);

            HPI hpi = latest;
            json.put("requiredCore", hpi.getRequiredJenkinsVersion());

            if (hpi.getCompatibleSinceVersion() != null) {
                json.put("compatibleSinceVersion", hpi.getCompatibleSinceVersion());
            }

            VersionNumber minimumJavaVersion = hpi.getMinimumJavaVersion();
            if (minimumJavaVersion != null) {
                json.put("minimumJavaVersion", minimumJavaVersion.toString());
            }

            JSONArray deps = new JSONArray();
            for (HPI.Dependency d : hpi.getDependencies())
                deps.add(d.toJSON());
            json.put("dependencies", deps);

            JSONArray devs = new JSONArray();
            List<HPI.Developer> devList = hpi.getDevelopers();
            if (!devList.isEmpty()) {
                for (HPI.Developer dev : devList)
                    devs.add(dev.toJSON());
            } else {
                String builtBy = latest.getBuiltBy();
                if (fixEmptyAndTrim(builtBy) != null)
                    devs.add(new HPI.Developer("", builtBy, "").toJSON());
            }
            json.put("developers", devs);
            json.put("gav", hpi.getGavId());

            return json;
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to serialize " + artifactId, ex);
            return null;
        }
    }

    public String getVersion() {
        return latest.version;
    }

    public String getPreviousVersion() {
        return previous == null? null : previous.version;
    }

    public String getScm() throws IOException {
        return latest.getScmUrl();
    }

    public String getRequiredCore() throws IOException {
        return latest.getRequiredJenkinsVersion();
    }

    public String getCompatibleSinceVersion() throws IOException {
        return latest.getCompatibleSinceVersion();
    }

    public String getMinimumJavaVersion() throws IOException {
        final JavaSpecificationVersion minimumJavaVersion = latest.getMinimumJavaVersion();
        return minimumJavaVersion == null ? null : minimumJavaVersion.toString();
    }

    public String getBuildDate() {
        return latest.getTimestampAsString();
    }

    public List<String> getLabels() throws IOException {
        return latest.getLabels();
    }

    public List<HPI.Dependency> getDependencies() throws IOException {
        return latest.getDependencies();
    }

    public String getSha1() throws IOException {
        return latest.getDigests().sha1;
    }

    public String getSha256() throws IOException {
        return latest.getDigests().sha256;
    }

    public String getGav() {
        return latest.getGavId();
    }

    private static String fixEmptyAndTrim(String value) {
        if (value == null) {
            return null;
        }
        final String trim = value.trim();
        if (trim.length() == 0) {
            return null;
        }
        return trim;
    }

    public List<HPI.Developer> getDevelopers() throws IOException {
        final List<HPI.Developer> developers = latest.getDevelopers();
        final String builtBy = fixEmptyAndTrim(latest.getBuiltBy());
        if (developers.isEmpty() && builtBy != null) {
            return Collections.singletonList(new HPI.Developer(null, builtBy, null));
        }
        return developers;
    }

    public String getExcerpt() throws IOException {
        return latest.getDescription();
    }

    public String getReleaseTimestamp() {
        return TIMESTAMP_FORMATTER.format(latest.getTimestamp());
    }

    public String getPreviousTimestamp() {
        return previous == null ? null : TIMESTAMP_FORMATTER.format(previous.getTimestamp());
    }

    private static final SimpleDateFormat TIMESTAMP_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'.00Z'", Locale.US);

    private static final Logger LOGGER = Logger.getLogger(PluginUpdateCenterEntry.class.getName());
}
