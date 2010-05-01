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
import org.jvnet.hudson.confluence.Confluence;

import javax.xml.rpc.ServiceException;
import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
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

    private final Map<String,RemotePage> pageCache = new HashMap<String, RemotePage>();
    private final Map<Long,String[]> labelCache = new HashMap<Long, String[]>();

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

            RemotePage page = pageCache.get(pageName);
            if (page==null) {
                page = service.getPage("", "HUDSON", pageName);
                pageCache.put(pageName,page);
            }
            return page;
        }
        return null;
    }

    public String[] getLabels(RemotePage page) throws RemoteException {
        String[] r = labelCache.get(page.getId());
        if (r==null) {
            RemoteLabel[] labels = service.getLabelsById("", page.getId());
            if (labels==null) return new String[0];
            ArrayList<String> result = new ArrayList<String>(labels.length);
            for (RemoteLabel label : labels)
                if (label.getName().startsWith("plugin-"))
                    result.add(label.getName().substring(7));
            r = result.toArray(new String[result.size()]);
            labelCache.put(page.getId(),r);
        }
        return r;
    }

    private static final String[] HUDSON_WIKI_PREFIX = {
            "http://hudson.gotdns.com/wiki/display/HUDSON/",
            "http://wiki.hudson-ci.org/display/HUDSON/",
    };
}
