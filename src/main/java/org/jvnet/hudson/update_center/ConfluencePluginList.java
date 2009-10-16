package org.jvnet.hudson.update_center;

import com.sun.xml.bind.v2.util.EditDistance;
import hudson.plugins.jira.soap.ConfluenceSoapService;
import hudson.plugins.jira.soap.RemotePage;
import hudson.plugins.jira.soap.RemotePageSummary;
import org.jvnet.hudson.confluence.Confluence;

import javax.xml.rpc.ServiceException;
import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

/**
 * List of plugins from confluence.
 *
 * See http://confluence.atlassian.com/display/DOC/Remote+API+Specification
 * for the confluence API.
 *
 * @author Kohsuke Kawaguchi
 */
public class ConfluencePluginList {
    private final ConfluenceSoapService service;
    private final Map<String,RemotePageSummary> children = new HashMap<String, RemotePageSummary>();
    private final String[] normalizedTitles;

    public ConfluencePluginList() throws IOException, ServiceException {
        service = Confluence.connect(new URL("http://wiki.hudson-ci.org/"));
        RemotePage page = service.getPage("", "HUDSON", "Plugins");

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
    public RemotePage findNearest(String pluginArtifactId) throws RemoteException {
        // comparison is case insensitive
        pluginArtifactId = pluginArtifactId.toLowerCase();

        String nearest = EditDistance.findNearest(pluginArtifactId, normalizedTitles);
        if(EditDistance.editDistance(nearest,pluginArtifactId)<4)
            return service.getPage("","HUDSON",children.get(nearest).getTitle());
        else
            return null;    // too far
    }

    public RemotePage getPage(String url) throws RemoteException {
        for( String p : HUDSON_WIKI_PREFIX ) {
            if(!url.startsWith(p))
                continue;

            String pageName = url.substring(p.length()).replace('+',' '); // poor hack for URL escape
            return service.getPage("","HUDSON",pageName);
        }
        return null;
    }

    private static final String[] HUDSON_WIKI_PREFIX = {
            "http://hudson.gotdns.com/wiki/display/HUDSON/",
            "http://wiki.hudson-ci.org/display/HUDSON/",
    };
}
