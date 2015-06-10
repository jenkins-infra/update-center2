/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.rpc.ServiceException;

import jenkins.plugins.confluence.soap.v2.ConfluenceSoapService;
import jenkins.plugins.confluence.soap.v2.ConfluenceSoapServiceServiceLocator;
import jenkins.plugins.confluence.soap.v2.RemoteLabel;
import jenkins.plugins.confluence.soap.v2.RemotePage;
import jenkins.plugins.confluence.soap.v2.RemotePageSummary;

import org.apache.axis.utils.StringUtils;
import org.apache.commons.io.FileUtils;

import com.sun.xml.bind.v2.util.EditDistance;

/**
 * List of plugins from confluence. Primarily serve as a cache.
 *
 * <p>
 * See http://confluence.atlassian.com/display/DOC/Remote+API+Specification for the confluence API.
 *
 * @author Kohsuke Kawaguchi
 */
public class ConfluenceV2PluginList {
    protected ConfluenceSoapService service;

    private final Map<String, RemotePageSummary> children = new HashMap<String, RemotePageSummary>();

    private String[] normalizedTitles;

    private String wikiSessionId;

    private final String WIKI_URL = "https://wiki.jenkins-ci.org/";

    private final String wikiBaseUrl;

    private final String wikiSpaceName;

    private final String wikiPageTitle;

    private final String wikiUser;

    private final String wikiPassword;

    private final Map<Long, String[]> labelCache = new HashMap<Long, String[]>();

    private final File cacheDir = new File(System.getProperty("user.home"), ".wiki.jenkins-cache");

    public ConfluenceV2PluginList() throws IOException, ServiceException {
        this(null, null, null, null, null);
    }

    public ConfluenceV2PluginList(String wikiBaseUrl, String wikiSpaceName, String wikiPageTitle, String wikiUser, String wikiPassword) throws IOException,
            ServiceException {
        if (wikiBaseUrl == null) {
            this.wikiBaseUrl = WIKI_URL;
        } else {
            this.wikiBaseUrl = wikiBaseUrl;
        }
        this.wikiSpaceName = wikiSpaceName;
        this.wikiPageTitle = wikiPageTitle;
        this.wikiUser = wikiUser;
        this.wikiPassword = wikiPassword;

        cacheDir.mkdirs();
    }

    public void initialize() throws IOException, ServiceException {
        if (!StringUtils.isEmpty(wikiBaseUrl)) {
            service = connectV2(new URL(wikiBaseUrl));
        } else {
            service = connectV2(new URL(WIKI_URL));
        }
        String token = "";
        if (!StringUtils.isEmpty(wikiUser) && !StringUtils.isEmpty(wikiPassword)) {
            token = service.login(wikiUser, wikiPassword);
        }
        String wikiSpaceNameLocal = "JENKINS";
        String wikiPageTitleLocal = "Plugins";
        if (!StringUtils.isEmpty(wikiSpaceName)) {
            wikiSpaceNameLocal = wikiSpaceName;
        }
        if (!StringUtils.isEmpty(wikiPageTitle)) {
            wikiPageTitleLocal = wikiPageTitle;
        }

        RemotePage page = service.getPage(token, wikiSpaceNameLocal, wikiPageTitleLocal);

        for (RemotePageSummary child : service.getChildren(token, page.getId())) {
            children.put(normalize(child.getTitle()), child);
        }
        normalizedTitles = children.keySet().toArray(new String[children.size()]);
    }

    /**
     * Connects to the confluence server.
     */
    public static ConfluenceSoapService connectV2(URL confluenceUrl) throws ServiceException, IOException {
        ConfluenceSoapServiceServiceLocator loc = new ConfluenceSoapServiceServiceLocator();
        return loc.getConfluenceserviceV2(new URL(confluenceUrl, "rpc/soap-axis/confluenceservice-v2"));
    }

    private void checkInitialized() {
        if (service == null) {
            throw new IllegalStateException("Variable 'service' is not initialized. Call 'initialize()' first.");
        }
        if (normalizedTitles == null) {
            throw new IllegalStateException("Variable 'normalizedTitles' is not initialized. Call 'initialize()' first.");
        }
    }

    /**
     * Make the page title as close to artifactId as possible.
     */
    private String normalize(String title) {
        title = title.toLowerCase().trim();
        if (title.endsWith("plugin")) {
            title = title.substring(0, title.length() - 6).trim();
        }
        return title.replace(" ", "-");
    }

    /**
     * Finds the closest match, if any. Otherwise null.
     */
    public WikiV2Page findNearest(String pluginArtifactId) throws IOException {
        checkInitialized();

        // comparison is case insensitive
        pluginArtifactId = pluginArtifactId.toLowerCase();

        String nearest = EditDistance.findNearest(pluginArtifactId, normalizedTitles);
        if (EditDistance.editDistance(nearest, pluginArtifactId) <= 1) {
            System.out.println("** No wiki page specified.. picking one with similar name."
                    + "\nUsing '" + nearest + "' for " + pluginArtifactId);
            return loadPage(children.get(nearest).getTitle());
        } else {
            return null; // too far
        }
    }

    public WikiV2Page getPage(String url) throws IOException {
        checkInitialized();

        Matcher tinylink = TINYLINK_PATTERN.matcher(url);
        if (tinylink.matches()) {
            String id = tinylink.group(1);

            File cache = new File(cacheDir, id + ".link");
            if (cache.exists()) {
                url = FileUtils.readFileToString(cache);
            } else {
                try {
                    // Avoid creating lots of sessions on wiki server.. get a session and reuse it.
                    if (wikiSessionId == null) {
                        wikiSessionId = initSession(WIKI_URL);
                    }
                    url = checkRedirect(
                            WIKI_URL + "pages/tinyurl.action?urlIdentifier=" + id,
                            wikiSessionId);
                    FileUtils.writeStringToFile(cache, url);
                } catch (IOException e) {
                    throw new RemoteException("Failed to lookup tinylink redirect", e);
                }
            }
        }

        for (String p : WIKI_PREFIXES) {
            if (!url.startsWith(p)) {
                continue;
            }

            String pageName = url.substring(p.length()).replace('+', ' '); // poor hack for URL escape

            // trim off the trailing '/'
            if (pageName.endsWith("/")) {
                pageName = pageName.substring(0, pageName.length() - 1);
            }

            return loadPage(pageName);
        }

        if (!StringUtils.isEmpty(wikiBaseUrl) && !StringUtils.isEmpty(wikiSpaceName)) {
            String customPrefix = wikiBaseUrl;
            if (customPrefix.endsWith("/")) {
                customPrefix = customPrefix + "display/" + wikiSpaceName + "/";
            } else {
                customPrefix = customPrefix + "/display/" + wikiSpaceName + "/";
            }
            if (url.startsWith(customPrefix)) {
                System.out.println("URL : " + url);
                System.out.println("PREFIX : " + customPrefix);
                String pageName = url.substring(customPrefix.length()).replace('+', ' '); // poor hack for URL escape

                // trim off the trailing '/'
                if (pageName.endsWith("/")) {
                    pageName = pageName.substring(0, pageName.length() - 1);
                }

                return loadPage(pageName);
            }
        }

        throw new IllegalArgumentException("** Failed to resolve " + url);
    }

    /**
     * Loads the page from Wiki after consulting with the cache.
     */
    private WikiV2Page loadPage(String title) throws IOException {
        File cache = new File(cacheDir, title + ".page");
        if (cache.exists() && cache.lastModified() >= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)) {
            // load from cache
            try {
                FileInputStream f = new FileInputStream(cache);
                try {
                    Object o = new ObjectInputStream(f).readObject();
                    if (o == null) {
                        return null;
                    }
                    if (o instanceof WikiV2Page) {
                        return (WikiV2Page) o;
                    }
                    // cache invalid. fall through to retrieve the page.
                } finally {
                    f.close();
                }
            } catch (ClassNotFoundException e) {
                throw (IOException) new IOException("Failed to retrieve from cache: " + cache).initCause(e);
            }
        }

        try {
            String token = "";
            if (!StringUtils.isEmpty(wikiUser) && !StringUtils.isEmpty(wikiPassword)) {
                token = service.login(wikiUser, wikiPassword);
            }
            String wikiSpaceNameLocal = "JENKINS";
            if (!StringUtils.isEmpty(wikiSpaceName)) {
                wikiSpaceNameLocal = wikiSpaceName;
            }
            System.out.println(title);
            RemotePage page = service.getPage(token, wikiSpaceNameLocal, title);
            RemoteLabel[] labels = service.getLabelsById(token, page.getId());
            WikiV2Page p = new WikiV2Page(page, labels);
            writeToCache(cache, p);
            return p;
        } catch (RemoteException e) {
            writeToCache(cache, null);
            throw e;
        }
    }

    /**
     * Writes an object to a cache file.
     *
     * In case another update center runs concurrently, write to a temporary file and then atomically rename it.
     */
    private void writeToCache(File cache, Object o) throws IOException {
        File tmp = new File(cache + ".tmp");
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tmp));
        try {
            oos.writeObject(o);
        } finally {
            oos.close();
        }
        cache.delete();
        tmp.renameTo(cache);
        tmp.delete();
    }

    private static String checkRedirect(String url, String sessionId) throws IOException {
        return connect(url, sessionId).getHeaderField("Location");
    }

    private static String initSession(String url) throws IOException {
        String cookie = connect(url, null).getHeaderField("Set-Cookie");
        return cookie.substring(0, cookie.indexOf(';')); // Remove ;Path=/
    }

    private static HttpURLConnection connect(String url, String sessionId) throws IOException {
        HttpURLConnection huc = (HttpURLConnection) new URL(url).openConnection();
        huc.setInstanceFollowRedirects(false);
        huc.setDoOutput(false);
        if (sessionId != null) {
            huc.addRequestProperty("Cookie", sessionId);
        }
        InputStream i = huc.getInputStream();
        while (i.read() >= 0) {
            ;
        } // Drain stream
        return huc;
    }

    public String[] getLabels(RemotePage page) throws RemoteException {
        checkInitialized();

        String token = "";
        if (!StringUtils.isEmpty(wikiUser) && !StringUtils.isEmpty(wikiPassword)) {
            token = service.login(wikiUser, wikiPassword);
        }

        String[] r = labelCache.get(page.getId());
        if (r == null) {
            RemoteLabel[] labels = service.getLabelsById(token, page.getId());
            if (labels == null) {
                return new String[0];
            }
            ArrayList<String> result = new ArrayList<String>(labels.length);
            for (RemoteLabel label : labels) {
                if (label.getName().startsWith("plugin-")) {
                    result.add(label.getName().substring(7));
                }
            }
            r = result.toArray(new String[result.size()]);
            labelCache.put(page.getId(), r);
        }
        return r;
    }

    private static final String[] WIKI_PREFIXES = {
            "https://wiki.jenkins-ci.org/display/JENKINS/",
            "http://wiki.jenkins-ci.org/display/JENKINS/",
            "http://wiki.hudson-ci.org/display/HUDSON/",
            "http://hudson.gotdns.com/wiki/display/HUDSON/",
    };

    private static final Pattern TINYLINK_PATTERN = Pattern.compile(".*/x/(\\w+)");
}
