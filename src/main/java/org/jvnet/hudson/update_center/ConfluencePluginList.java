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

import hudson.plugins.jira.soap.ConfluenceSoapService;
import hudson.plugins.jira.soap.RemoteLabel;
import hudson.plugins.jira.soap.RemotePage;
import hudson.plugins.jira.soap.RemotePageSummary;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.jvnet.hudson.confluence.Confluence;

import javax.xml.rpc.ServiceException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * List of plugins from confluence. Primarily serve as a cache.
 *
 * <p>
 * See http://confluence.atlassian.com/display/DOC/Remote+API+Specification
 * for the confluence API.
 *
 * @author Kohsuke Kawaguchi
 */
public class ConfluencePluginList {

    private static final Pattern TINYLINK_PATTERN = Pattern.compile(".*/x/(\\w+)");

    /** Base URL of the wiki. */
    private static final String WIKI_URL = "https://wiki.jenkins-ci.org/";

    /** List of old wiki base URLs, which plugins may still have in their POM. */
    private static final String[] OLD_URL_PREFIXES = {
            "http://wiki.jenkins-ci.org/",
            "http://wiki.hudson-ci.org/",
            "http://hudson.gotdns.com/wiki/",
    };

    private final File cacheDir = new File(System.getProperty("user.home"),".wiki.jenkins-ci.org-cache");
    private final ConfluenceSoapService service;
    private final Map<String, String> pluginPages = new HashMap<String, String>();

    private String wikiSessionId;

    public ConfluencePluginList() throws IOException, ServiceException {
        this(Confluence.connect(new URL(WIKI_URL)));
    }

    ConfluencePluginList(ConfluenceSoapService service) throws IOException, ServiceException {
        this.service = service;

        cacheDir.mkdirs();

        System.out.println("Fetching the 'Plugins' page and child info from the wiki...");
        RemotePage page = service.getPage("", "JENKINS", "Plugins");

        // Note the URL of each child page of the "Plugins" page on the wiki
        for (RemotePageSummary child : service.getChildren("", page.getId())) {
            // Normalise URLs coming from the Confluence API, so that when we later check whether a certain URL is in
            // this list, we don't get a false negative due to differences in how the URL was encoded
            pluginPages.put(getKeyForUrl(child.getUrl()), child.getUrl());
        }
    }

    /** @return A wiki URL if the given URL is a child page of the "Plugins" wiki page, otherwise {@code null}. */
    private String getCanonicalUrl(String url) {
        return pluginPages.get(getKeyForUrl(url));
    }

    private String getKeyForUrl(String url) {
        // We call `getPath()` to ensure that the path is URL-encoded in a consistent way.
        // Confluence is case-insensitive when it comes to the URL path, hence `toLowerCase()`
        URI uri = URI.create(url.replace(' ', '+'));
        return String.format("%s?%s", uri.getPath().toLowerCase(Locale.ROOT), uri.getQuery());
    }

    /**
     * Attempts to determine the canonical wiki URL for a given URL.
     *
     * @param url Any URL.
     * @return A canonical URL to a wiki page, or {@code null} if the URL is not a child of the "Plugins" wiki page.
     * @throws IOException If resolving a short URL fails.
     */
    public String resolveWikiUrl(String url) throws IOException {
        // Empty or null values can't be good
        if (url == null || url.isEmpty()) {
            System.out.println("** Wiki URL is missing");
            return null;
        }

        // If the URL is a short URL (e.g. "/x/tgeIAg"), then resolve the target URL
        Matcher tinylink = TINYLINK_PATTERN.matcher(url);
        if (tinylink.matches()) {
            url = resolveLink(tinylink.group(1));
        }

        // Fix up URLs with old hostnames or paths
        for (String p : OLD_URL_PREFIXES) {
            if (url.startsWith(p)) {
                url = url.replace(p, WIKI_URL).replaceAll("(?i)/HUDSON/", "/JENKINS/");
            }
        }

        // Reject the URL if it's not on the wiki at all
        if (!url.startsWith(WIKI_URL)) {
            System.out.println("** Wiki URLs should start with "+ WIKI_URL+" but got "+url);
            return null;
        }

        // Strip trailing slashes (e.g.
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // If the page exists in the child list we fetched, get the canonical URL
        String canonicalUrl = getCanonicalUrl(url);
        if (canonicalUrl == null) {
            System.out.println("** Wiki page does not exist, or is not a child of the Plugins wiki page: "+ url);
        }
        return canonicalUrl;
    }

    /**
     * Determines the full wiki URL for a given Confluence short URL.
     *
     * @param id Short URL ID.
     * @return The full wiki URL.
     * @throws IOException If accessing the wiki fails.
     */
    private String resolveLink(String id) throws IOException {
        File cache = new File(cacheDir,id+".link");
        if (cache.exists()) {
            return FileUtils.readFileToString(cache);
        }

        String url;
        try {
            // Avoid creating lots of sessions on wiki server.. get a session and reuse it.
            if (wikiSessionId == null)
                wikiSessionId = initSession(WIKI_URL);
            url = checkRedirect(WIKI_URL + "pages/tinyurl.action?urlIdentifier=" + id, wikiSessionId);
            FileUtils.writeStringToFile(cache, url);
        } catch (IOException e) {
            throw new RemoteException("Failed to lookup tinylink redirect", e);
        }
        return url;
    }

    /**
     * Attempts to fetch a page from the wiki, possibly returning from local disk cache.
     *
     * @param pomUrl URL from the POM.
     * @return Wiki page object if the page exists, otherwise {@code null}.
     * @throws IOException If accessing the wiki fails.
     */
    public WikiPage getPage(String pomUrl) throws IOException {
        String url = resolveWikiUrl(pomUrl);
        if (url == null) {
            return null;
        }

        // Determine the page identifier for the given wiki URL
        String cacheKey = getIdentifierForUrl(url);

        // Load the serialised page from the cache, if we retrieved it within the last day
        File cache = new File(cacheDir, cacheKey + ".page");
        if (cache.exists() && cache.lastModified() >= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)) {
            try {
                FileInputStream f = new FileInputStream(cache);
                try {
                    Object o = new ObjectInputStream(f).readObject();
                    if (o == null) {
                        return null;
                    }
                    if (o instanceof WikiPage) {
                        return (WikiPage) o;
                    }
                    // cache invalid. fall through to retrieve the page.
                } finally {
                    f.close();
                }
            } catch (ClassNotFoundException e) {
                throw (IOException) new IOException("Failed to retrieve from cache: " + cache).initCause(e);
            }
        }

        // Otherwise fetch it from the wiki and cache the page
        try {
            RemotePage page;
            if (NumberUtils.isDigits(cacheKey)) {
                page = service.getPage("", Long.parseLong(cacheKey));
            } else {
                page = service.getPage("", "JENKINS", cacheKey);
            }
            RemoteLabel[] labels = service.getLabelsById("", page.getId());
            WikiPage p = new WikiPage(page, labels);
            writeToCache(cache, p);
            return p;
        } catch (RemoteException e) {
            // Something went wrong; invalidate the cache for this page
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
        File tmp = new File(cache+".tmp");
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

    /**
     * @param url Wiki URL, in the canonical URL format.
     * @return The identifier we need to fetch the given URL via the Confluence API.
     */
    private static String getIdentifierForUrl(String url) {
        URI pageUri = URI.create(url);
        String path = pageUri.getPath();
        if (path.equals("/pages/viewpage.action")) {
            // This is the canonical URL format for titles with odd characters, e.g. "Anything Goes" Formatter Plugin
            return pageUri.getQuery().replace("pageId=", "");
        }

        // In all other cases, we can just take the title straight from the URL
        return path.replaceAll("(?i)/display/JENKINS/", "").replace("+", " ");
    }

    private static String checkRedirect(String url, String sessionId) throws IOException {
        return connect(url, sessionId).getHeaderField("Location");
    }

    private static String initSession(String url) throws IOException {
        String cookie = connect(url, null).getHeaderField("Set-Cookie");
        return cookie.substring(0, cookie.indexOf(';')); // Remove ;Path=/
    }

    private static HttpURLConnection connect(String url, String sessionId) throws IOException {
        HttpURLConnection huc = (HttpURLConnection)new URL(url).openConnection();
        huc.setInstanceFollowRedirects(false);
        huc.setDoOutput(false);
        if (sessionId != null) huc.addRequestProperty("Cookie", sessionId);
        InputStream i = huc.getInputStream();
        while (i.read() >= 0) ; // Drain stream
        return huc;
    }

}
