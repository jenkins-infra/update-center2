/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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

import hudson.plugins.jira.soap.RemotePage;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Locale;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An entry of a Hudson plugin in the update center metadata.
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
     * Latest version of this plugin.
     */
    public final HPI previous;
    /**
     * Confluence page of this plugin in Wiki.
     * Null if we couldn't find it.
     */
    public final RemotePage page;
    /**
     * Confluence labels for the plugin wiki page.
     * Null if wiki page wasn't found.
     */
    public final String[] labels;

    public Plugin(String artifactId, HPI latest, HPI previous, ConfluencePluginList cpl) throws IOException {
        this.artifactId = artifactId;
        this.latest = latest;
        this.previous = previous;
        this.page = findPage(cpl);
        this.labels = getLabels(cpl);
    }

    /**
     * Locates the page for this plugin on Wiki.
     *
     * <p>
     * First we'll try to parse POM and obtain the URL.
     * If that fails, find the nearest name from the children list.
     */
    private RemotePage findPage(ConfluencePluginList cpl) throws IOException {
        try {
            DocumentFactory factory = new DocumentFactory();
            factory.setXPathNamespaceURIs(Collections.singletonMap("m","http://maven.apache.org/POM/4.0.0"));

            File pom = latest.resolvePOM();
            Document dom = new SAXReader(factory).read(pom);
            Node url = dom.selectSingleNode("/project/url");
            if(url==null)
                url = dom.selectSingleNode("/m:project/m:url");
            if(url!=null) {
                String wikiPage = ((Element)url).getTextTrim();
                try {
                    return cpl.getPage(wikiPage); // found the confluence page successfully
                } catch (RemoteException e) {
                    System.err.println("Failed to fetch "+wikiPage);
                    e.printStackTrace();
                }
            }
        } catch (DocumentException e) {
            System.err.println("Can't parse POM for "+artifactId);
            e.printStackTrace();
        }

        try {
            String p = OVERRIDES.getProperty(artifactId);
            if(p!=null)
                return cpl.getPage(p);
        } catch (RemoteException e) {
            System.err.println("Override failed for "+artifactId);
            e.printStackTrace();
        }

        // try to guess the Wiki page
        try {
            return cpl.findNearest(artifactId);
        } catch (RemoteException e) {
            System.err.println("Failed to locate nearest");
            e.printStackTrace();
        }

        return null;
    }

    private String[] getLabels(ConfluencePluginList cpl) {
        if (page!=null) try {
            return cpl.getLabels(page);
        } catch (RemoteException e) {
            System.err.println("Failed to fetch labels for " + page.getUrl());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Obtains the excerpt of this wiki page in HTML. Otherwise null.
     */
    public String getExcerptInHTML() {
        String content = page.getContent();
        if(content==null)
            return null;

        Matcher m = EXCERPT_PATTERN.matcher(content);
        if(!m.find())
            return null;

        String excerpt = m.group(1);
        String oneLiner = NEWLINE_PATTERN.matcher(excerpt).replaceAll(" ");
        return HYPERLINK_PATTERN.matcher(oneLiner).replaceAll("<a href='$2'>$1</a>");
    }

    // Tweaking to ignore leading whitespace after the initial {excerpt}
    private static final Pattern EXCERPT_PATTERN = Pattern.compile("\\{excerpt(?::hidden(?:=true)?)?\\}\\s*(.+)\\{excerpt\\}", Pattern.DOTALL);
    private static final Pattern HYPERLINK_PATTERN = Pattern.compile("\\[([^|\\]]+)\\|([^|\\]]+)(|([^]])+)?\\]");
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("(?:\\r\\n|\\n)");

    public String getTitle() {
        String title = "";
        if (page!=null) {
            title = page.getTitle();
        }
        return title;
    }

    public String getWiki() {
        String wiki = "";
        if (page!=null) {
            wiki = page.getUrl();
        }
        return wiki;
    }
    
    public JSONObject toJSON() throws IOException {
        JSONObject json = latest.toJSON(artifactId);

        SimpleDateFormat fisheyeDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'.00Z'", Locale.US);
        fisheyeDateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        json.put("releaseTimestamp", fisheyeDateFormatter.format(latest.getTimestamp()));
        if (previous!=null)
            json.put("previousTimestamp", fisheyeDateFormatter.format(previous.getTimestamp()));


        if(page!=null) {
            json.put("wiki",page.getUrl());
            json.put("title",page.getTitle());
            String excerpt = getExcerptInHTML();
            if(excerpt!=null)
                json.put("excerpt",excerpt);
            if(labels!=null)
                json.put("labels",labels);
        }

        HPI hpi = latest;
        json.put("requiredCore", fixNull(hpi.getRequiredHudsonVersion()));
        
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
            devs.add(new HPI.Developer("", latest.getBuiltBy(), "").toJSON());
        }
        json.put("developers", devs);

        return json;
    }

    /**
     * Earlier versions of the maven-hpi-plugin put "null" string literal, so we need to treat it as real null.
     */
    private String fixNull(String v) {
        if("null".equals(v))    return null;
        return v;
    }


    private static final Properties OVERRIDES = new Properties();

    static {
        try {
            OVERRIDES.load(Plugin.class.getClassLoader().getResourceAsStream("wiki-overrides.properties"));
        } catch (IOException e) {
            throw new Error(e);
        }
    }
}
