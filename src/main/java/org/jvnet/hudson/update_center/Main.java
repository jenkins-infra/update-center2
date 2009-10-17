package org.jvnet.hudson.update_center;

import net.sf.json.JSONObject;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.net.URL;

/**
 * @author Kohsuke Kawaguchi
 */
public class Main {
    @Option(name="-o",usage="json file")
    public File output = new File("output.json");

    @Option(name="-h",usage="htaccess file")
    public File htaccess = new File(".htaccess");

    /**
     * This option builds the directory image to be staged to http://dlc.sun.com/hudson/downloads
     */
    @Option(name="-dlc",usage="Build dlc.sun.com layout")
    public File dlc = null;

    @Option(name="-www",usage="Built hudson-ci.org layout")
    public File www = null;

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        CmdLineParser p = new CmdLineParser(main);
        p.parseArgument(args);

        if (main.www!=null) {
            main.output = new File(main.www,"update-center.json");
            main.htaccess = new File(main.www,"latest/.htaccess");
        }

        main.run();
    }

    public void run() throws Exception {

        MavenRepository repo = new MavenRepository();

        PrintWriter latestRedirect = new PrintWriter(new FileWriter(htaccess), true);

        JSONObject root = new JSONObject();
        root.put("updateCenterVersion","1");    // we'll bump the version when we make incompatible changes
        root.put("core", buildCore(repo, latestRedirect));
        root.put("plugins", buildPlugins(repo, latestRedirect));

        PrintWriter pw = new PrintWriter(new FileWriter(output));
        pw.println("updateCenter.post(");
        pw.println(root.toString(2));
        pw.println(");");
        pw.close();
        latestRedirect.close();
    }

    /**
     * Build JSON for the plugin list.
     * @param repository
     * @param redirect
     */
    protected JSONObject buildPlugins(MavenRepository repository, PrintWriter redirect) throws Exception {
        ConfluencePluginList cpl = new ConfluencePluginList();

        JSONObject plugins = new JSONObject();
        for( PluginHistory hpi : repository.listHudsonPlugins() ) {
            System.out.println(hpi.artifactId);
            if(hpi.artifactId.equals("ivy2"))
                continue;       // subsumed into the ivy plugin. Hiding from the update center

            List<HPI> versions = new ArrayList<HPI>(hpi.artifacts.values());
            HPI latest = versions.get(versions.size()-1);
            HPI previous = versions.size()>1 ? versions.get(versions.size()-2) : null;

            Plugin plugin = new Plugin(hpi.artifactId,latest,previous,cpl);

            if(plugin.page!=null)
                System.out.println("=> "+plugin.page.getTitle());
            System.out.println("=> "+plugin.toJSON());

            plugins.put(plugin.artifactId,plugin.toJSON());
            redirect.printf("Redirect 302 /latest/%s.hpi %s\n", plugin.artifactId, latest.getURL());
            String permalink = String.format("/download/plugins/%1$s/latest/%1$s.hpi", plugin.artifactId);
            redirect.printf("Redirect 302 %s %s\n", permalink, latest.getURL());

            if (dlc!=null) {
                // build dlc.sun.com layout
                for (HPI v : versions) {
                    ArtifactInfo a = v.artifact;
                    ln("../../../../../maven/2/"+ a.groupId.replace('.','/')+"/"+ a.artifactId+"/"+ a.version+"/"+ a.artifactId+"-"+ a.version+"."+ a.packaging,
                            new File(dlc,"plugins/"+hpi.artifactId+"/"+v.version+"/"+hpi.artifactId+".hpi"));
                }
            }

            if (www!=null)
                buildIndex(new File(www,"download/plugins/"+hpi.artifactId),hpi.artifactId,versions,permalink);
        }

        return plugins;
    }

    private void buildIndex(File dir, String title, Collection<? extends MavenArtifact> versions, String permalink) throws IOException {
        List<MavenArtifact> list = new ArrayList<MavenArtifact>(versions);
        Collections.sort(list,new Comparator<MavenArtifact>() {
            public int compare(MavenArtifact o1, MavenArtifact o2) {
                return -o1.getVersion().compareTo(o2.getVersion());
            }
        });

        IndexHtmlBuilder index = new IndexHtmlBuilder(dir, title);
        index.add(permalink,"permalink to the latest");
        for (MavenArtifact a : list)
            index.add(a);
        index.close();
    }

    /**
     * Creates a symlink.
     */
    private void ln(String from, File to) throws InterruptedException, IOException {
        to.getParentFile().mkdirs();

        ProcessBuilder pb = new ProcessBuilder();
        pb.command("ln","-sf", from,to.getAbsolutePath());
        if (pb.start().waitFor()!=0)
            throw new IOException("ln failed");
    }

    /**
     * Build JSON for the core Hudson.
     */
    protected JSONObject buildCore(MavenRepository repository, PrintWriter redirect) throws Exception {
        TreeMap<VersionNumber,HudsonWar> wars = repository.getHudsonWar();
        HudsonWar latest = wars.get(wars.lastKey());
        JSONObject core = latest.toJSON("core");
        System.out.println("core\n=> "+ core);

        redirect.printf("Redirect 302 /latest/hudson.war %s\n", latest.getURL());
        redirect.printf("Redirect 302 /download/war/latest/hudson.war %s\n", latest.getURL());

        if (dlc!=null) {
            // build dlc.sun.com layout
            for (HudsonWar w : wars.values()) {
                ArtifactInfo a = w.artifact;
                ln("../../../../maven/2/org/jvnet/hudson/main/hudson-war/"+a.version+"/hudson-war-"+a.version+".war",
                        new File(dlc,"war/"+w.version+"/hudson.war"));
            }
        }

        if (www!=null)
            buildIndex(new File(www,"download/war/"),"hudson.war", wars.values(), "/download/war/latest/hudson.war");

        return core;
    }
}
