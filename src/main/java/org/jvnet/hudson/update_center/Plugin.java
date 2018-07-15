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

import com.google.common.annotations.VisibleForTesting;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    
    private final SAXReader xmlReader;

    /**
     * POM parsed as a DOM.
     */
    private Document pom;

    public Plugin(String artifactId, HPI latest, HPI previous) throws IOException {
        this.artifactId = artifactId;
        this.latest = latest;
        this.previous = previous;
        this.xmlReader = createXmlReader();
    }

    public Plugin(PluginHistory hpi) throws IOException {
        this.artifactId = hpi.artifactId;
        HPI previous = null, latest = null;

        Iterator<HPI> it = hpi.artifacts.values().iterator();

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

        this.xmlReader = createXmlReader();
    }

    public Plugin(HPI hpi) throws IOException {
        this(hpi.artifact.artifactId, hpi,  null);
    }

    private Document getPom() throws IOException {
        if (pom == null) {
            pom = readPOM();
        }
        return pom;
    }

    private SAXReader createXmlReader() {
        DocumentFactory factory = new DocumentFactory();
        factory.setXPathNamespaceURIs(
                Collections.singletonMap("m", "http://maven.apache.org/POM/4.0.0"));
        return new SAXReader(factory);
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
    public String getPluginUrl() throws IOException {
        // Check whether the wiki URL should be overridden
        String url = URL_OVERRIDES.getProperty(artifactId);

        // Otherwise read the wiki URL from the POM, if any
        if (url == null) {
            url = selectSingleValue(getPom(), "/project/url");
        }

        String originalUrl = url;

        if (url != null) {
            url = url.replace("wiki.hudson-ci.org/display/HUDSON/", "wiki.jenkins-ci.org/display/JENKINS/");
            url = url.replace("http://wiki.jenkins-ci.org", "https://wiki.jenkins-ci.org");
        }

        if (url != null && !url.equals(originalUrl)) {
            LOGGER.info("Rewrote URL for plugin " + artifactId + " from " + originalUrl + " to " + url);
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

    private String filterKnownObsoleteUrls(String scm) {
        if (scm == null) {
            // couldn't be determined from /project/scm/url in pom or parent pom
            return null;
        }
        if (scm.contains("fisheye.jenkins-ci.org")) {
            // well known historical URL that won't help
            return null;
        }
        if (scm.contains("svn.jenkins-ci.org")) {
            // well known historical URL that won't help
            return null;
        }
        if (scm.contains("svn.java.net")) {
            // well known historical URL that won't help
            return null;
        }
        if (scm.contains("svn.dev.java.net")) {
            // well known historical URL that won't help
            return null;
        }
        if (scm.contains("hudson.dev.java.net")) {
            // well known historical URL that won't help
            return null;
        }
        return scm;
    }

    private String getScmUrl(Document pom) {
        if (pom != null) {
            String scm = selectSingleValue(pom, "/project/scm/url");
            // Try parent pom
            if (scm == null) {
                System.out.println("** No SCM URL found in POM");
                Element parent = (Element) selectSingleNode(pom, "/project/parent");
                if (parent != null) {
                    try {
                        Document parentPom = xmlReader.read(
                                latest.repository.resolve(
                                        new ArtifactInfo("",
                                                parent.element("groupId").getTextTrim(),
                                                parent.element("artifactId").getTextTrim(),
                                                parent.element("version").getTextTrim(),
                                                ""), "pom", null));
                        scm = selectSingleValue(parentPom, "/project/scm/url");
                        if (scm == null) {
                            System.out.println("** No SCM URL found in parent POM");
                            // grandparent is pointless, no additional hits
                        }
                    } catch (Exception ex) {
                        System.out.println("** Failed to read parent pom");
                        ex.printStackTrace();
                    }
                }
            }
            if (scm == null) {
                return null;
            }
            if (filterKnownObsoleteUrls(scm) == null) {
                System.out.println("** Filtered obsolete URL in SCM URL");
                return null;
            }
            return scm;
        }
        return null;
    }

    private String getScmUrlFromDeveloperConnection(Document pom) {
        if (pom != null) {
            String scm = selectSingleValue(pom, "/project/scm/developerConnection");
            // Try parent pom
            if (scm == null) {
                System.out.println("** No SCM developerConnection found in POM");
                Element parent = (Element) selectSingleNode(pom, "/project/parent");
                if (parent != null) {
                    try {
                        Document parentPom = xmlReader.read(
                                latest.repository.resolve(
                                        new ArtifactInfo("",
                                                parent.element("groupId").getTextTrim(),
                                                parent.element("artifactId").getTextTrim(),
                                                parent.element("version").getTextTrim(),
                                                ""), "pom", null));
                        scm = selectSingleValue(parentPom, "/project/scm/developerConnection");
                        if (scm == null) {
                            System.out.println("** No SCM developerConnection found in parent POM");
                        }
                    } catch (Exception ex) {
                        System.out.println("** Failed to read parent pom");
                        ex.printStackTrace();
                    }
                }
            }
            if (scm == null) {
                return null;
            }
            if (filterKnownObsoleteUrls(scm) == null) {
                System.out.println("** Filtered obsolete URL in SCM developerConnection");
                return null;
            }
            return scm;
        }
        return null;
    }

    private String interpolateProjectName(String str) {
        if (str == null) {
            return null;
        }
        str = str.replace("${project.artifactId}", artifactId);
        str = str.replace("${artifactId}", artifactId);
        return str;
    }

    private String requireHttpsGitHubJenkinsciUrl(String url) {
        if (url == null) {
            return null;
        }
        if (url.contains("github.com:jenkinsci/") || url.contains("github.com/jenkinsci/")) {
            // We're only doing weird thing for GitHub URLs that map somewhat cleanly from developerConnection to browsable URL.
            // Also limit to jenkinsci because that's what people should be using anyway.
            String githubUrl = url.substring(url.indexOf("github.com"));
            githubUrl = githubUrl.replace(":", "/");
            if (githubUrl.endsWith(".git")) {
                // all should, but not all do
                githubUrl = githubUrl.substring(0, githubUrl.lastIndexOf(".git"));
            }
            return "https://" + githubUrl;
        }
        return null;
    }

    private String requireGitHubRepoExistence(String url) {
        GitHubSource gh = GitHubSource.getInstance();
        String shortenedUrl = StringUtils.removeEndIgnoreCase(url, "-plugin");
        return gh.isRepoExisting(url) ? url : (gh.isRepoExisting(shortenedUrl) ? shortenedUrl : null);
    }

    /**
     * Get hostname of SCM specified in POM of latest release, or null.
     * Used to determine if source lives in github or svn.
     */
    public String getScmUrl() throws IOException {
        if (getPom() != null) {
            String scm = getScmUrl(getPom());
            if (scm == null) {
                scm = getScmUrlFromDeveloperConnection(getPom());
            }
            if (scm == null) {
                System.out.println("** Failed to determine SCM URL from POM or parent POM of " + artifactId);
            }
            scm = interpolateProjectName(scm);
            String originalScm = scm;
            scm = requireHttpsGitHubJenkinsciUrl(scm);
            if (originalScm != null && scm == null) {
                System.out.println("** Rejecting URL outside GitHub.com/jenkinsci for " + artifactId + ": " + originalScm);
            }

            if (scm == null) {
                // Last resort: check whether a ${artifactId}-plugin repo in jenkinsci exists, if so, use that
                scm = "https://github.com/jenkinsci/" + artifactId + "-plugin";
                System.out.println("** Falling back to default repo for " + artifactId + ": " + scm);

                String checkedScm = scm;
                // Check whether the fallback repo actually exists, if not, don't publish the repo name
                scm = requireGitHubRepoExistence(scm);
                if (scm == null) {
                    System.out.println("** Repository does not actually exist: " + checkedScm);
                }
            }

            return scm;
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

    /** @return The plugin name defined in the POM &lt;name> modified by simplication rules (no 'Jenkins', no 'Plugin'); then artifact ID. */
    public String getName() throws IOException {
        String title = selectSingleValue(getPom(), "/project/name");
        if (title == null) {
            title = artifactId;
        } else {
            title = simplifyPluginName(title);
        }
        return title;
    }

    @VisibleForTesting
    public static String simplifyPluginName(String name) {
        name = StringUtils.removeStart(name, "Jenkins ");
        name = StringUtils.removeStart(name, "Hudson ");
        name = StringUtils.removeEndIgnoreCase(name, " for Jenkins");
        name = StringUtils.removeEndIgnoreCase(name, " Jenkins Plugin");
        name = StringUtils.removeEndIgnoreCase(name, " Plugin");
        name = StringUtils.removeEndIgnoreCase(name, " Plug-In");
        name = name.replaceAll("[- .!]+$", ""); // remove trailing punctuation e.g. for 'Acme Foo - Jenkins Plugin'
        return name;
    }

    public JSONObject toJSON() throws IOException {
        JSONObject json = latest.toJSON(artifactId);

        SimpleDateFormat fisheyeDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'.00Z'", Locale.US);
        json.put("releaseTimestamp", fisheyeDateFormatter.format(latest.getTimestamp()));
        if (previous!=null) {
            json.put("previousVersion", previous.version);
            json.put("previousTimestamp", fisheyeDateFormatter.format(previous.getTimestamp()));
        }

        json.put("title", getName());
        String scm = getScmUrl();
        if (scm!=null) {
            json.put("scm", scm);
        }

        json.put("wiki", "https://plugins.jenkins.io/" + artifactId);

        json.put("labels", getLabels());

        String description = plainText2html(selectSingleValue(getPom(), "/project/description"));
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
