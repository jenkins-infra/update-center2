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

import com.sun.xml.bind.v2.util.EditDistance;
import hudson.plugins.jira.soap.ConfluenceSoapService;
import hudson.plugins.jira.soap.RemoteLabel;
import hudson.plugins.jira.soap.RemotePage;
import hudson.plugins.jira.soap.RemotePageSummary;
import org.apache.commons.io.FileUtils;
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
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final ConfluenceSoapService service;
    private final Map<String,RemotePageSummary> children = new HashMap<String, RemotePageSummary>();
    private final String[] normalizedTitles;

    private String wikiSessionId;
    private final String WIKI_URL = "https://wiki.jenkins-ci.org/";

    private final File cacheDir = new File(System.getProperty("user.home"),".wiki.jenkins-ci.org-cache");

    public ConfluencePluginList() throws IOException, ServiceException {
        cacheDir.mkdirs();

        service = Confluence.connect(new URL(WIKI_URL));
        RemotePage page = service.getPage("", "JENKINS", "Plugins");

        for (RemotePageSummary child : service.getChildren("", page.getId()))
            children.put(normalize(child.getTitle()),child);
        normalizedTitles = children.keySet().toArray(new String[children.size()]);
    }

    /**
     * Make the page title as close to artifactId as possible.
     */
    private String normalize(String title) {
        title = title.toLowerCase().trim();
        if(title.endsWith("plugin"))    title=title.substring(0,title.length()-6).trim();
        return title.replace(" ","-");
    }

    /**
     * Finds the closest match, if any. Otherwise null.
     */
    public WikiPage findNearest(String pluginArtifactId) throws IOException {
        // comparison is case insensitive
        pluginArtifactId = pluginArtifactId.toLowerCase();

        String nearest = EditDistance.findNearest(pluginArtifactId, normalizedTitles);
        if (EditDistance.editDistance(nearest,pluginArtifactId) <= 1) {
            System.out.println("** No wiki page specified.. picking one with similar name."
                               + "\nUsing '"+nearest+"' for "+pluginArtifactId);
            return loadPage(children.get(nearest).getTitle());
        } else
            return null;    // too far
    }

    public WikiPage getPage(String url) throws IOException {
        Matcher tinylink = TINYLINK_PATTERN.matcher(url);
        if (tinylink.matches()) {
            String id = tinylink.group(1);

            File cache = new File(cacheDir,id+".link");
            if (cache.exists()) {
                url = FileUtils.readFileToString(cache);
            } else {
                try {
                    // Avoid creating lots of sessions on wiki server.. get a session and reuse it.
                    if (wikiSessionId == null)
                        wikiSessionId = initSession(WIKI_URL);
                    url = checkRedirect(
                            WIKI_URL + "pages/tinyurl.action?urlIdentifier=" + id,
                            wikiSessionId);
                    FileUtils.writeStringToFile(cache,url);
                } catch (IOException e) {
                    throw new RemoteException("Failed to lookup tinylink redirect", e);
                }
            }
        }

        for( String p : WIKI_PREFIXES ) {
            if (!url.startsWith(p))
                continue;

            String pageName = url.substring(p.length()).replace('+',' '); // poor hack for URL escape

            return loadPage(pageName);
        }
        throw new IllegalArgumentException("** Failed to resolve "+url);
    }

    /**
     * Loads the page from Wiki after consulting with the cache.
     */
    private WikiPage loadPage(String title) throws IOException {
        File cache = new File(cacheDir,md5(title)+".page");
        if (cache.exists() && cache.lastModified() >= System.currentTimeMillis()- TimeUnit.DAYS.toMillis(1)) {
            // load from cache
            try {
                FileInputStream f = new FileInputStream(cache);
                try {
                    Object o = new ObjectInputStream(f).readObject();
                    if (o==null)    return null;
                    if (o instanceof WikiPage) {
                        WikiPage p = (WikiPage) o;
                        return p;
                    }
                    // cache invalid. fall through to retrieve the page.
                } finally {
                    f.close();
                }
            } catch (ClassNotFoundException e) {
                throw (IOException)new IOException("Failed to retrieve from cache: "+cache).initCause(e);
            }
        }

        try {
            RemotePage page = service.getPage("", "JENKINS", title);
            RemoteLabel[] labels = service.getLabelsById("", page.getId());
            WikiPage p = new WikiPage(page, labels);
            writeToCache(cache, p);
            return p;
        } catch (RemoteException e) {
            writeToCache(cache, null);
            throw e;
        }
    }

    private void writeToCache(File cache, Object o) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cache));
        try {
            oos.writeObject(o);
        } finally {
            oos.close();
        }
    }

    private String md5(String pageName) {
        return pageName;    // TODO: md5
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

    private static final String[] WIKI_PREFIXES = {
        "https://wiki.jenkins-ci.org/display/JENKINS/",
        "http://wiki.jenkins-ci.org/display/JENKINS/",
        "http://wiki.hudson-ci.org/display/HUDSON/",
        "http://hudson.gotdns.com/wiki/display/HUDSON/",
    };

    private static final Pattern TINYLINK_PATTERN = Pattern.compile(".*/x/(\\w+)");
}
