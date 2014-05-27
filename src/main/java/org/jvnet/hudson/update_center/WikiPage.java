package org.jvnet.hudson.update_center;

import hudson.plugins.jira.soap.RemoteLabel;
import hudson.plugins.jira.soap.RemotePage;

import java.util.ArrayList;

/**
 * @author Kohsuke Kawaguchi
 */
public class WikiPage {
    public final RemotePage page;
    public final RemoteLabel[] labels;

    public WikiPage(RemotePage page, RemoteLabel[] labels) {
        this.labels = labels;
        this.page = page;
    }

    public String[] getLabels() {
        if (labels==null) return new String[0];

        ArrayList<String> result = new ArrayList<String>(labels.length);
        for (RemoteLabel label : labels)
            if (label.getName().startsWith("plugin-"))
                result.add(label.getName().substring(7));

        return result.toArray(new String[result.size()]);
    }

    public String getTitle() {
        return page.getTitle();
    }

    public String getUrl() {
        return page.getUrl();
    }

    public String getContent() {
        return page.getContent();
    }
}
