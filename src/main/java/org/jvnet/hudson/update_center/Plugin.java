/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc.
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
package org.jvnet.hudson.update_center;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An entry of a plugin in the update center metadata.
 *
 * @author Kohsuke Kawaguchi
 */
public class Plugin {
    /**
     * Plugin artifact ID.
     */
    public final String artifactId;
    /**
     * Latest version of this plugin.
     */
    public final HPI latest;
    /**
     * Previous version of this plugin.
     */
    public final HPI previous;
    
    /**
     * Plugin labels (categories)
     */
    private String[] labels;
    private boolean labelsRead = false;

    /**
     * Deprecated plugins should not be included in update center.
     */
    private boolean deprecated = false;

    private final SAXReader xmlReader;

    /**
     * POM parsed as a DOM.
     */
    private final Document pom;

    public Plugin(String artifactId, HPI latest, HPI previous) throws IOException {
        this.artifactId = artifactId;
        this.latest = latest;
        this.previous = previous;
        this.xmlReader = createXmlReader();
        this.pom = readPOM();
    }

    public Plugin(PluginHistory hpi) throws IOException {
        this.artifactId = hpi.artifactId;
        List<HPI> versions = new ArrayList<HPI>();
        for (HPI h : hpi.artifacts.values()) {
            try {
                h.getManifest();
                versions.add(h);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to resolve "+h+". Dropping this version.",e);
            }
        }

        this.latest = versions.get(0);
        this.previous = versions.size()>1 ? versions.get(1) : null;

        // Doublecheck that latest-by-version is also latest-by-date:
        checkLatestDate(versions, latest);

        this.xmlReader = createXmlReader();
        this.pom = readPOM();
    }

    public Plugin(HPI hpi) throws IOException {
        this(hpi.artifact.artifactId, hpi,  null);
    }

    private SAXReader createXmlReader() {
        DocumentFactory factory = new DocumentFactory();
        factory.setXPathNamespaceURIs(
                Collections.singletonMap("m", "http://maven.apache.org/POM/4.0.0"));
        return new SAXReader(factory);
    }

    private void checkLatestDate(Collection<HPI> artifacts, HPI latestByVersion) throws IOException {
        TreeMap<Long,HPI> artifactsByDate = new TreeMap<Long,HPI>();
        for (HPI h : artifacts)
            artifactsByDate.put(h.getTimestamp(), h);
        HPI latestByDate = artifactsByDate.get(artifactsByDate.lastKey());
        if (latestByDate != latestByVersion)
            System.out.println(
                "** Latest-by-version (" + latestByVersion.version + ','
                + latestByVersion.getTimestampAsString() + ") doesn't match latest-by-date ("
                + latestByDate.version + ',' + latestByDate.getTimestampAsString() + ')');
    }

    private Document readPOM() throws IOException {
        try {
            return xmlReader.read(latest.resolvePOM());
        } catch (DocumentException e) {
            System.err.println("** Can't parse POM for "+artifactId);
            e.printStackTrace();
            return null;
        }
    }

    /** @return The URL as specified in the POM, or the overrides file. */
    public String getPluginUrl() {
        // Check whether the wiki URL should be overridden
        String url = URL_OVERRIDES.getProperty(artifactId);

        // Otherwise read the wiki URL from the POM, if any
        if (url == null && pom != null) {
            url = selectSingleValue(pom, "/project/url");
        }
        return url;
    }

    private static Node selectSingleNode(Document pom, String path) {
        Node result = pom.selectSingleNode(path);
        if (result == null)
            result = pom.selectSingleNode(path.replaceAll("/", "/m:"));
        return result;
    }

    private static String selectSingleValue(Document dom, String path) {
        Node node = selectSingleNode(dom, path);
        return node != null ? ((Element)node).getTextTrim() : null;
    }

    private static final Pattern HOSTNAME_PATTERN =
        Pattern.compile("(?:://|scm:git:(?!\\w+://))(?:\\w*@)?([\\w.-]+)[/:]");

    /**
     * Get hostname of SCM specified in POM of latest release, or null.
     * Used to determine if source lives in github or svn.
     */
    public String getScmHost() {
        if (pom != null) {
            String scm = selectSingleValue(pom, "/project/scm/connection");
            if (scm == null) {
                // Try parent pom
                Element parent = (Element)selectSingleNode(pom, "/project/parent");
                if (parent != null) try {
                    Document parentPom = xmlReader.read(
                            latest.repository.resolve(
                                    new ArtifactInfo("",
                                            parent.element("groupId").getTextTrim(),
                                            parent.element("artifactId").getTextTrim(),
                                            parent.element("version").getTextTrim(),
                                            ""), "pom", null));
                    scm = selectSingleValue(parentPom, "/project/scm/connection");
                } catch (Exception ex) {
                    System.out.println("** Failed to read parent pom");
                    ex.printStackTrace();
                }
            }
            if (scm != null) {
                Matcher m = HOSTNAME_PATTERN.matcher(scm);
                if (m.find())
                    return m.group(1);
                else System.out.println("** Unable to parse scm/connection: " + scm);
            }
            else System.out.println("** No scm/connection found in pom");
        }
        return null;
    }

    public String[] getLabels() {
        Object ret = LABEL_DEFINITIONS.get(artifactId);
        if (ret == null) {
            // handle missing entry in properties file
            return new String[0];
        }
        String labels = ret.toString();
        if (labels.trim().length() == 0) {
            // handle empty entry in properties file
            return new String[0];
        }
        return labels.split("\\s+");
    }

    /** @return The plugin name defined in the POM &lt;name>; falls back to the wiki page title, then artifact ID. */
    public String getName() {                                                                                                                                                   
        String title = selectSingleValue(pom, "/project/name");
        if (title == null) {
            title = artifactId;
        }
        return title;
    }

    public JSONObject toJSON() throws IOException {
        JSONObject json = latest.toJSON(artifactId);

        SimpleDateFormat fisheyeDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'.00Z'", Locale.US);
        fisheyeDateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        json.put("releaseTimestamp", fisheyeDateFormatter.format(latest.getTimestamp()));
        if (previous!=null) {
            json.put("previousVersion", previous.version);
            json.put("previousTimestamp", fisheyeDateFormatter.format(previous.getTimestamp()));
        }

        json.put("title", getName());
        String scm = getScmHost();
        if (scm!=null) {
            json.put("scm", scm);
        }

        json.put("wiki", "https://plugins.jenkins.io/" + artifactId);

        json.put("labels", getLabels());

        String description = plainText2html(selectSingleValue(pom, "/project/description"));
        if (latest.isAlphaOrBeta()) {
            description = "<b>(This version is experimental and may change in backward-incompatible ways)</b>" + (description == null ? "" : ("<br><br>" + description));
        }
        if (description!=null) {
            json.put("excerpt",description);
        }

        HPI hpi = latest;
        json.put("requiredCore", hpi.getRequiredJenkinsVersion());

        if (hpi.getCompatibleSinceVersion() != null) {
            json.put("compatibleSinceVersion",hpi.getCompatibleSinceVersion());
        }
        if (hpi.getSandboxStatus() != null) {
            json.put("sandboxStatus",hpi.getSandboxStatus());
        }

        JSONArray deps = new JSONArray();
        for (HPI.Dependency d : hpi.getDependencies())
            deps.add(d.toJSON());
        json.put("dependencies",deps);

        JSONArray devs = new JSONArray();
        List<HPI.Developer> devList = hpi.getDevelopers();
        if (!devList.isEmpty()) {
            for (HPI.Developer dev : devList)
                devs.add(dev.toJSON());
        } else {
            String builtBy = latest.getBuiltBy();
            if (builtBy!=null)
                devs.add(new HPI.Developer("", builtBy, "").toJSON());
        }
        json.put("developers", devs);
        json.put("gav", hpi.getGavId());

        return json;
    }

    private String plainText2html(String plainText) {
        if (plainText == null || plainText.length() == 0) {
            return "";
        }
        return plainText.replace("&","&amp;").replace("<","&lt;");
    }

    private static final Properties URL_OVERRIDES = new Properties();
    private static final Properties LABEL_DEFINITIONS = new Properties();

    static {
        try {
            URL_OVERRIDES.load(Plugin.class.getClassLoader().getResourceAsStream("wiki-overrides.properties"));
            LABEL_DEFINITIONS.load(Plugin.class.getClassLoader().getResourceAsStream("label-definitions.properties"));
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Plugin.class.getName());
}
