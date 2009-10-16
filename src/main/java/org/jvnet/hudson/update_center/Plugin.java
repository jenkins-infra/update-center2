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
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Locale;
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

    public Plugin(String artifactId, HPI latest, HPI previous, ConfluencePluginList cpl) throws IOException {
        this.artifactId = artifactId;
        this.latest = latest;
        this.previous = previous;
        this.page = findPage(cpl);
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
                return cpl.getPage(wikiPage); // found the confluence page successfully
            }
        } catch (DocumentException e) {
            System.err.println("Can't parse POM for "+artifactId);
            e.printStackTrace();
        } catch (RemoteException e) {
            System.err.println("POM points to a non-confluence page for "+artifactId);
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
        return HYPERLINK_PATTERN.matcher(excerpt).replaceAll("<a href='$2'>$1</a>");
    }

    // Tweaking to ignore leading whitespace after the initial {excerpt}
    private static final Pattern EXCERPT_PATTERN = Pattern.compile("\\{excerpt(?::hidden(?:=true)?)?\\}\\s*(.+)\\{excerpt\\}");
    private static final Pattern HYPERLINK_PATTERN = Pattern.compile("\\[([^|\\]]+)\\|([^|\\]]+)(|([^]])+)?\\]");

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
        }

        HPI hpi = latest;
        json.put("requiredCore",hpi.getRequiredHudsonVersion());
        
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
        if (!hpi.getDevelopers().isEmpty()) {
            for (HPI.Developer dev : hpi.getDevelopers())
                devs.add(dev.toJSON());
        } else {
            try {
                devs.add(new HPI.Developer(" :" + latest.getBuiltBy()+ ": ").toJSON());
            } catch (ParseException e) {
                throw new AssertionError(e);
            }
        }
        json.put("developers", devs);

        return json;
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
