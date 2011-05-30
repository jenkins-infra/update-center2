/*
 * The MIT License
 *
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

import net.sf.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;


public class UpdateCenter {

    public boolean prettyPrint = false;

    IArtifactProvider repository;

    final String id;

    public String connectionCheckUrl;

    public Signing signing;

    public UpdateCenter(String id, IArtifactProvider repository)
    {
        this.id = id;
        this.repository = repository;
    }

    public void write(OutputStream os) throws Exception
    {
        JSONObject root = new JSONObject();
        root.put("updateCenterVersion","1");    // we'll bump the version when we make incompatible changes
        JSONObject core = buildCoreJSON();
        if (core!=null)
            root.put("core", core);
        root.put("plugins", getPluginsJSON());
        root.put("id",id);

        if (connectionCheckUrl!=null)
            root.put("connectionCheckUrl",connectionCheckUrl);

        if( signing != null )
            signing.sign(root);

        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
        pw.println("updateCenter.post(");
        pw.println(prettyPrint?root.toString(2):root.toString());
        pw.println(");");
        pw.flush();


    }

     /**
     * Build JSON for the plugin list.
     */
    protected JSONObject getPluginsJSON() throws Exception {
        ConfluencePluginList cpl = new ConfluencePluginList();

        JSONObject plugins = new JSONObject();
        for( PluginHistory hpi : repository.listHudsonPlugins() ) {
            try {
                System.out.println(hpi.artifactId);

                List<IHPI> versions = new ArrayList<IHPI>(hpi.artifacts.values());
                IHPI latest = versions.get(0);
                IHPI previous = versions.size()>1 ? versions.get(1) : null;
                // Doublecheck that latest-by-version is also latest-by-date:
                checkLatestDate(versions, latest);

                Plugin plugin = new Plugin(hpi.artifactId,latest,previous,cpl);
                if (plugin.isDeprecated()) {
                    System.out.println("=> Plugin is deprecated.. skipping.");
                    continue;
                }

                System.out.println(
                  plugin.page!=null ? "=> "+plugin.page.getTitle() : "** No wiki page found");
                JSONObject json = plugin.toJSON();
                System.out.println("=> " + json);
                plugins.put(plugin.artifactId, json);

            } catch (IOException e) {
                e.printStackTrace();
                // move on to the next plugin
            }
        }

        return plugins;
    }

     /**
     * Build JSON for the core Jenkins.
     */
    protected JSONObject buildCoreJSON() throws Exception {
        TreeMap<VersionNumber,HudsonWar> wars = repository.getHudsonWar();
        if (wars.isEmpty())     return null;

        HudsonWar latest = wars.get(wars.firstKey());
        JSONObject core = latest.toJSON("core");
        System.out.println("core\n=> "+ core);

        return core;
    }

    private void checkLatestDate(Collection<IHPI> artifacts, IHPI latestByVersion) throws IOException {
        TreeMap<Long,IHPI> artifactsByDate = new TreeMap<Long,IHPI>();
        for (IHPI h : artifacts)
            artifactsByDate.put(h.getTimestamp().getTimestamp(), h);
        IHPI latestByDate = artifactsByDate.get(artifactsByDate.lastKey());
        if (latestByDate != latestByVersion) System.out.println(
            "** Latest-by-version (" + latestByVersion.getVersion() + ','
            + latestByVersion.getTimestamp().getTimestampAsString() + ") doesn't match latest-by-date ("
            + latestByDate.getVersion() + ',' + latestByDate
                    .getTimestamp().getTimestampAsString() + ')');
    }
}
