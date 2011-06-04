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

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.jvnet.hudson.crypto.CertificateUtil;
import org.jvnet.hudson.crypto.SignatureOutputStream;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static java.security.Security.addProvider;

/**
 * @author Kohsuke Kawaguchi
 */
public class Main {
    @Option(name="-o",usage="json file")
    public File output = new File("output.json");

    @Option(name="-r",usage="release history JSON file")
    public File releaseHistory = new File("release-history.json");

    @Option(name="-h",usage="htaccess file")
    public File htaccess = new File(".htaccess");

    /**
     * This option builds the directory image for the download server.
     */
    @Option(name="-download",usage="Build download server layout")
    public File download = null;

    @Option(name="-www",usage="Built jenkins-ci.org layout")
    public File www = null;

    @Option(name="-index.html",usage="Update the version number of the latest jenkins.war in jenkins-ci.org/index.html")
    public File indexHtml = null;

    @Option(name="-key",usage="Private key to sign the update center. Must be used in conjunction with -certificate.")
    public File privateKey = null;

    @Option(name="-certificate",usage="X509 certificate for the private key given by the -key option")
    public List<File> certificates = new ArrayList<File>();

    @Option(name="-id",required=true,usage="Uniquely identifies this update center. We recommend you use a dot-separated name like \"com.sun.wts.jenkins\". This value is not exposed to users, but instead internally used by Jenkins.")
    public String id;

    @Option(name="-connectionCheckUrl",
            usage="Specify an URL of the 'always up' server for performing connection check.")
    public String connectionCheckUrl;

    @Option(name="-pretty",usage="Pretty-print the result")
    public boolean prettyPrint;

    @Option(name="-cap",usage="Cap the version number and only report data that's compatible with ")
    public String cap = null;

    public static void main(String[] args) throws Exception {
        System.exit(new Main().run(args));
    }

    public int run(String[] args) throws Exception {
        CmdLineParser p = new CmdLineParser(this);
        try {
            p.parseArgument(args);

            if (www!=null) {
                output = new File(www,"update-center.json");
                htaccess = new File(www,"latest/.htaccess");
                indexHtml = new File(www,"index.html");
                releaseHistory = new File(www,"release-history.json");
            }

            run();
            return 0;
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            p.printUsage(System.err);
            return 1;
        }
    }

    public void run() throws Exception {

        MavenRepository repo = createRepository();
        if (cap!=null)
            repo = new VersionCappedMavenRepository(repo,new VersionNumber(cap));

        File p = htaccess.getParentFile();
        if (p!=null)        p.mkdirs();
        PrintWriter latestRedirect = new PrintWriter(new FileWriter(htaccess), true);

        JSONObject root = new JSONObject();
        root.put("updateCenterVersion","1");    // we'll bump the version when we make incompatible changes
        JSONObject core = buildCore(repo, latestRedirect);
        if (core!=null)
            root.put("core", core);
        root.put("plugins", buildPlugins(repo, latestRedirect));
        root.put("id",id);
        if (connectionCheckUrl!=null)
            root.put("connectionCheckUrl",connectionCheckUrl);

        if(privateKey!=null || !certificates.isEmpty())
        {
            Signing s = new Signing(privateKey, certificates);
            s.sign(root);
        }

        PrintWriter pw = new PrintWriter(new FileWriter(output));
        pw.println("updateCenter.post(");
        pw.println(prettyPrint?root.toString(2):root.toString());
        pw.println(");");
        pw.close();
        JSONObject rhRoot = new JSONObject();
        rhRoot.put("releaseHistory", buildReleaseHistory(repo));
        PrintWriter rhpw = new PrintWriter(new FileWriter(releaseHistory));
        rhpw.println(prettyPrint?rhRoot.toString(2):rhRoot.toString());
        rhpw.close();
        latestRedirect.close();
    }

    protected MavenRepository createRepository() throws Exception {
        MavenRepositoryImpl r = new MavenRepositoryImpl();
        r.addRemoteRepository("java.net2",
                new URL("http://updates.jenkins-ci.org/.index/nexus-maven-repository-index.zip"),
                new URL("http://maven.glassfish.org/content/groups/public/"));
        return r;
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
                String permalink = String.format("/latest/%s.hpi", plugin.artifactId);
                redirect.printf("Redirect 302 %s %s\n", permalink, latest.getURL().getPath());

                if (download!=null) {
                    for (IHPI v : versions) {
                        stage((MavenArtifact)v, new File(download, "plugins/" + hpi.artifactId + "/" + v.getVersion() + "/" + hpi.artifactId + ".hpi"));
                    }
                    if (!versions.isEmpty())
                        createLatestSymlink(hpi, versions.get(0));
                }

                if (www!=null)
                    buildIndex(new File(www,"download/plugins/"+hpi.artifactId),hpi.artifactId,versions,permalink);
            } catch (IOException e) {
                e.printStackTrace();
                // move on to the next plugin
            }
        }

        return plugins;
    }

    /**
     * Generates symlink to the latest version.
     */
    protected void createLatestSymlink(PluginHistory hpi, IHPI latest) throws InterruptedException, IOException {
        File dir = new File(download, "plugins/" + hpi.artifactId);
        new File(dir,"latest").delete();

        ProcessBuilder pb = new ProcessBuilder();
        pb.command("ln","-s", latest.getVersion().toString(), "latest");
        pb.directory(dir);
        int r = pb.start().waitFor();
        if (r !=0)
            throw new IOException("ln failed: "+r);
    }

    private void checkLatestDate(Collection<IHPI> artifacts, IHPI latestByVersion) throws IOException {
        TreeMap<Long,IHPI> artifactsByDate = new TreeMap<Long,IHPI>();
        for (IHPI h : artifacts)
            artifactsByDate.put(h.getTimestamp().getTimestamp(), h);
        IHPI latestByDate = artifactsByDate.get(artifactsByDate.lastKey());
        if (latestByDate != latestByVersion) System.out.println(
            "** Latest-by-version (" + latestByVersion.getVersion() + ','
            + latestByVersion.getTimestamp().getTimestampAsString() + ") doesn't match latest-by-date ("
            + latestByDate.getVersion() + ',' + latestByDate.getTimestamp().getTimestampAsString() + ')');
    }

    /**
     * Stages an artifact into the specified location.
     */
    protected void stage(MavenArtifact a, File dst) throws IOException, InterruptedException {
        File src = a.resolve();
        if (dst.exists() && dst.lastModified()==src.lastModified() && dst.length()==src.length())
            return;   // already up to date

//        dst.getParentFile().mkdirs();
//        FileUtils.copyFile(src,dst);

        // TODO: directory and the war file should have the release timestamp
        dst.getParentFile().mkdirs();

        ProcessBuilder pb = new ProcessBuilder();
        pb.command("ln","-f", src.getAbsolutePath(), dst.getAbsolutePath());
        if (pb.start().waitFor()!=0)
            throw new IOException("ln failed");

    }

    /**
     * Build JSON for the release history list.
     * @param repository
     */
    protected JSONArray buildReleaseHistory(MavenRepository repository) throws Exception {
        ConfluencePluginList cpl = new ConfluencePluginList();

        JSONArray releaseHistory = new JSONArray();
        for( Map.Entry<Date,Map<String,IHPI>> relsOnDate : repository.listHudsonPluginsByReleaseDate().entrySet() ) {
            String relDate = Timestamp.getDateFormat().format(relsOnDate.getKey());
            System.out.println("Releases on " + relDate);
            
            JSONArray releases = new JSONArray();

            for (Map.Entry<String,IHPI> rel : relsOnDate.getValue().entrySet()) {
                IHPI h = rel.getValue();
                JSONObject o = new JSONObject();
                try {
                    Plugin plugin = new Plugin(h.getArtifact().artifactId, h, null, cpl);
                    
                    String title = plugin.getTitle();
                    if ((title==null) || (title.equals(""))) {
                        title = h.getArtifact().artifactId;
                    }
                    
                    o.put("title", title);
                    o.put("gav", h.getArtifact().groupId+':'+h.getArtifact().artifactId+':'+h.getArtifact().version);
                    o.put("timestamp", h.getTimestamp());
                    o.put("wiki", plugin.getWiki());
                    o.put("version", h.getVersion().toString());
                    System.out.println("\t" + title + ":" + h.getVersion());
                } catch (IOException e) {
                    System.out.println("Failed to resolve plugin " + h.getArtifact().artifactId + " so using defaults");
                    o.put("title", h.getArtifact().artifactId);
                    o.put("wiki", "");
                    o.put("version", h.getArtifact());
                }
                releases.add(o);
            }
            JSONObject d = new JSONObject();
            d.put("date", relDate);
            d.put("releases", releases);
            releaseHistory.add(d);
        }
        
        return releaseHistory;
    }

    private void buildIndex(File dir, String title, Collection<? extends IArtifact> versions, String permalink) throws IOException {
        List<IArtifact> list = new ArrayList<IArtifact>(versions);
        Collections.sort(list,new Comparator<IArtifact>() {
            public int compare(IArtifact o1, IArtifact o2) {
                return -o1.getVersion().compareTo(o2.getVersion());
            }
        });

        IndexHtmlBuilder index = new IndexHtmlBuilder(dir, title);
        index.add(permalink,"permalink to the latest");
        for (IArtifact a : list)
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
     * Build JSON for the core Jenkins.
     */
    protected JSONObject buildCore(MavenRepository repository, PrintWriter redirect) throws Exception {
        TreeMap<VersionNumber,HudsonWar> wars = repository.getHudsonWar();
        if (wars.isEmpty())     return null;

        HudsonWar latest = wars.get(wars.firstKey());
        JSONObject core = latest.toJSON("core");
        System.out.println("core\n=> "+ core);

        redirect.printf("Redirect 302 /latest/jenkins.war %s\n", latest.getURL().getPath());
        redirect.printf("Redirect 302 /latest/debian/jenkins.deb http://pkg.jenkins-ci.org/debian/binary/jenkins_%s_all.deb\n", latest.getVersion());
        redirect.printf("Redirect 302 /latest/redhat/jenkins.rpm http://pkg.jenkins-ci.org/redhat/RPMS/noarch/jenkins-%s-1.1.noarch.rpm\n", latest.getVersion());
        redirect.printf("Redirect 302 /latest/opensuse/jenkins.rpm http://pkg.jenkins-ci.org/opensuse/RPMS/noarch/jenkins-%s-1.1.noarch.rpm\n", latest.getVersion());

        if (download!=null) {
            // build the download server layout
            for (HudsonWar w : wars.values()) {
                 stage(w, new File(download,"war/"+w.version+"/"+w.getFileName()));
            }
        }

        if (www!=null)
            buildIndex(new File(www,"download/war/"),"jenkins.war", wars.values(), "/latest/jenkins.war");

        return core;
    }

    static {
        addProvider(new BouncyCastleProvider());
    }
}
