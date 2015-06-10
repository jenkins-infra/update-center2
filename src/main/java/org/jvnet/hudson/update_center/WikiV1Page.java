package org.jvnet.hudson.update_center;

import java.io.Serializable;
import java.util.ArrayList;

import jenkins.plugins.confluence.soap.v1.RemoteLabel;
import jenkins.plugins.confluence.soap.v1.RemotePage;

/**
 * @author Kohsuke Kawaguchi
 */
public class WikiV1Page implements Serializable {
    public final RemotePage page;

    public final RemoteLabel[] labels;

    public WikiV1Page(RemotePage page, RemoteLabel[] labels) {
        this.labels = labels;
        this.page = page;
    }

    public String[] getLabels() {
        if (labels == null) {
            return new String[0];
        }

        ArrayList<String> result = new ArrayList<String>(labels.length);
        for (RemoteLabel label : labels) {
            if (label.getName().startsWith("plugin-")) {
                result.add(label.getName().substring(7));
            }
        }

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
