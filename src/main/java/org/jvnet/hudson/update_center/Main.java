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

    @Option(name="-latestCore.txt",usage="Update the version number of the latest jenkins.war in latestCore.txt")
    public File latestCoreTxt = null;

    @Option(name="-key",usage="Private key to sign the update center. Must be used in conjunction with -certificate.")
    public File privateKey = null;

    @Option(name="-certificate",usage="X509 certificate for the private key given by the -key option")
    public List<File> certificates = new ArrayList<File>();

    @Option(name="-id",required=true,usage="Uniquely identifies this update center. We recommend you use a dot-separated name like \"com.sun.wts.jenkins\". This value is not exposed to users, but instead internally used by Jenkins.")
    public String id;

    @Option(name="-maxPlugins",usage="For testing purposes. Limit the number of plugins managed to the specified number.")
    public Integer maxPlugins;

    @Option(name="-connectionCheckUrl",
            usage="Specify an URL of the 'always up' server for performing connection check.")
    public String connectionCheckUrl;

    @Option(name="-pretty",usage="Pretty-print the result")
    public boolean prettyPrint;

    @Option(name="-cap",usage="Cap the version number and only report data that's compatible with ")
    public String cap = null;

    public static final String EOL = System.getProperty("line.separator");

    public static void main(String[] args) throws Exception {
        System.exit(new Main().run(args));
    }

    public int run(String[] args) throws Exception {
        CmdLineParser p = new CmdLineParser(this);
        try {
            p.parseArgument(args);

            if (www!=null) {
                prepareStandardDirectoryLayout();
            }

            run();
            return 0;
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            p.printUsage(System.err);
            return 1;
        }
    }

    private void prepareStandardDirectoryLayout() {
        output = new File(www,"update-center.json");
        htaccess = new File(www,"latest/.htaccess");
        indexHtml = new File(www,"index.html");
        releaseHistory = new File(www,"release-history.json");
        latestCoreTxt = new File(www,"latestCore.txt");
    }

    public void run() throws Exception {

        MavenRepository repo = createRepository();

        PrintWriter latestRedirect = createHtaccessWriter();

        JSONObject ucRoot = buildUpdateCenterJson(repo, latestRedirect);
        String uc = updateCenterPostCallJson(ucRoot);
        writeToFile(uc, output);

        JSONObject rhRoot = buildFullReleaseHistory(repo);
        String rh = prettyPrintJson(rhRoot);
        writeToFile(rh, releaseHistory);

        latestRedirect.close();
    }

    String updateCenterPostCallJson(JSONObject ucRoot) {
        return "updateCenter.post(" + EOL + prettyPrintJson(ucRoot) + EOL + ");";
    }

    private PrintWriter createHtaccessWriter() throws IOException {
        File p = htaccess.getParentFile();
        if (p!=null)        p.mkdirs();
        return new PrintWriter(new FileWriter(htaccess), true);
    }

    private JSONObject buildUpdateCenterJson(MavenRepository repo, PrintWriter latestRedirect) throws Exception {
        JSONObject root = new JSONObject();
        root.put("updateCenterVersion","1");    // we'll bump the version when we make incompatible changes
        JSONObject core = buildCore(repo, latestRedirect);
        if (core!=null)
            root.put("core", core);
        root.put("plugins", buildPlugins(repo, latestRedirect));
        root.put("id",id);
        if (connectionCheckUrl!=null)
            root.put("connectionCheckUrl",connectionCheckUrl);

        if(privateKey!=null && !certificates.isEmpty())
            sign(root);
        else {
            if (privateKey!=null || !certificates.isEmpty())
                throw new Exception("private key and certificate must be both specified");
        }

        return root;
    }

    private static void writeToFile(String string, final File file) throws IOException {
        PrintWriter rhpw = new PrintWriter(new FileWriter(file));
        rhpw.println(string);
        rhpw.close();
    }

    private String prettyPrintJson(JSONObject json) {
        return prettyPrint? json.toString(2): json.toString();
    }

    protected MavenRepository createRepository() throws Exception {
        MavenRepository repo = DefaultMavenRepositoryBuilder.createStandardInstance(maxPlugins);
        if (cap!=null)
            repo = new VersionCappedMavenRepository(repo,new VersionNumber(cap));
        return repo;
    }

    /**
     * Generates a canonicalized JSON format of the given object, and put the signature in it.
     * Because it mutates the signed object itself, validating the signature needs a bit of work,
     * but this enables a signature to be added transparently.
     */
    private void sign(JSONObject o) throws GeneralSecurityException, IOException {
        JSONObject sign = new JSONObject();


        List<X509Certificate> certs = getCertificateChain();
        X509Certificate signer = certs.get(0); // the first one is the signer, and the rest is the chain to a root CA.

        // this is for computing a digest
        MessageDigest sha1 = MessageDigest.getInstance("SHA1");
        DigestOutputStream dos = new DigestOutputStream(new NullOutputStream(),sha1);

        // this is for computing a signature
        PrivateKey key = ((KeyPair)new PEMReader(new FileReader(privateKey)).readObject()).getPrivate();
        Signature sig = Signature.getInstance("SHA1withRSA");
        sig.initSign(key);
        SignatureOutputStream sos = new SignatureOutputStream(sig);

        // this is for verifying that signature validates
        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(signer.getPublicKey());
        SignatureOutputStream vos = new SignatureOutputStream(verifier);

        o.writeCanonical(new OutputStreamWriter(new TeeOutputStream(new TeeOutputStream(dos,sos),vos),"UTF-8"));

        // digest
        byte[] digest = sha1.digest();
        sign.put("digest",new String(Base64.encodeBase64(digest)));

        // signature
        byte[] s = sig.sign();
        sign.put("signature",new String(Base64.encodeBase64(s)));

        // and certificate chain
        JSONArray a = new JSONArray();
        for (X509Certificate cert : certs)
            a.add(new String(Base64.encodeBase64(cert.getEncoded())));
        sign.put("certificates",a);

        // did the signature validate?
        if (!verifier.verify(s))
            throw new GeneralSecurityException("Signature failed to validate. Either the certificate and the private key weren't matching, or a bug in the program.");

        o.put("signature",sign);
    }

    /**
     * Loads a certificate chain and makes sure it's valid.
     */
    private List<X509Certificate> getCertificateChain() throws FileNotFoundException, GeneralSecurityException {
        CertificateFactory cf = CertificateFactory.getInstance("X509");
        List<X509Certificate> certs = new ArrayList<X509Certificate>();
        for (File f : certificates) {
            X509Certificate c = (X509Certificate) cf.generateCertificate(new FileInputStream(f));
            c.checkValidity();
            certs.add(c);
        }
        
        Set<TrustAnchor> rootCAs = CertificateUtil.getDefaultRootCAs();
        rootCAs.add(new TrustAnchor((X509Certificate)cf.generateCertificate(getClass().getResourceAsStream("/hudson-community.cert")),null));

        try {
            CertificateUtil.validatePath(certs,rootCAs);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        return certs;
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

                Plugin plugin = new Plugin(hpi,cpl);
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
                redirect.printf("Redirect 302 %s %s\n", permalink, plugin.latest.getURL().getPath());

                if (download!=null) {
                    for (HPI v : hpi.artifacts.values()) {
                        stage(v, new File(download, "plugins/" + hpi.artifactId + "/" + v.version + "/" + hpi.artifactId + ".hpi"));
                    }
                    if (!hpi.artifacts.isEmpty())
                        createLatestSymlink(hpi, plugin.latest);
                }

                if (www!=null)
                    buildIndex(new File(www,"download/plugins/"+hpi.artifactId),hpi.artifactId,hpi.artifacts.values(),permalink);
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
    protected void createLatestSymlink(PluginHistory hpi, HPI latest) throws InterruptedException, IOException {
        File dir = new File(download, "plugins/" + hpi.artifactId);
        new File(dir,"latest").delete();

        ProcessBuilder pb = new ProcessBuilder();
        pb.command("ln","-s", latest.version, "latest");
        pb.directory(dir);
        int r = pb.start().waitFor();
        if (r !=0)
            throw new IOException("ln failed: "+r);
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
     * @param repo
     */
    protected JSONObject buildFullReleaseHistory(MavenRepository repo) throws Exception {
        JSONObject rhRoot = new JSONObject();
        rhRoot.put("releaseHistory", buildReleaseHistory(repo));
        return rhRoot;
    }

    protected JSONArray buildReleaseHistory(MavenRepository repository) throws Exception {
        ConfluencePluginList cpl = new ConfluencePluginList();

        JSONArray releaseHistory = new JSONArray();
        for( Map.Entry<Date,Map<String,HPI>> relsOnDate : repository.listHudsonPluginsByReleaseDate().entrySet() ) {
            String relDate = MavenArtifact.getDateFormat().format(relsOnDate.getKey());
            System.out.println("Releases on " + relDate);
            
            JSONArray releases = new JSONArray();

            for (Map.Entry<String,HPI> rel : relsOnDate.getValue().entrySet()) {
                HPI h = rel.getValue();
                JSONObject o = new JSONObject();
                try {
                    Plugin plugin = new Plugin(h, cpl);
                    
                    String title = plugin.getTitle();
                    if ((title==null) || (title.equals(""))) {
                        title = h.artifact.artifactId;
                    }
                    
                    o.put("title", title);
                    o.put("gav", h.artifact.groupId+':'+h.artifact.artifactId+':'+h.artifact.version);
                    o.put("timestamp", h.getTimestamp());
                    o.put("wiki", plugin.getWiki());
                    o.put("version", h.version);
                    System.out.println("\t" + title + ":" + h.version);
                } catch (IOException e) {
                    System.out.println("Failed to resolve plugin " + h.artifact.artifactId + " so using defaults");
                    o.put("title", h.artifact.artifactId);
                    o.put("wiki", "");
                    o.put("version", h.version);
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
     * Identify the latest core, populates the htaccess redirect file, optionally download the core wars and build the index.html
     * @return the JSON for the core Jenkins
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

        if (latestCoreTxt !=null)
            writeToFile(latest.getVersion().toString(), latestCoreTxt);

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
