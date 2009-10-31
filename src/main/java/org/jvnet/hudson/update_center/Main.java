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

import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.sonatype.nexus.index.ArtifactInfo;
import org.jvnet.hudson.crypto.SignatureOutputStream;
import org.jvnet.hudson.crypto.CertificateUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import static java.security.Security.addProvider;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.cert.TrustAnchor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.Set;

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

    @Option(name="-index.html",usage="Update the version number of the latest hudson.war in hudson-ci.org/index.html")
    public File indexHtml = null;

    @Option(name="-key",usage="Private key to sign the update center. Must be used in conjunction with -certificate.")
    public File privateKey = null;

    @Option(name="-certificate",usage="X509 certificate for the private key given by the -key option")
    public List<File> certificates = new ArrayList<File>();

    @Option(name="-id",required=true,usage="Uniquely identifies this update center. We recommend you use a dot-separated name like \"com.sun.wts.hudson\". This value is not exposed to users, but instead internally used by Hudson.")
    public String id;

    @Option(name="-connectionCheckUrl",
            usage="Specify an URL of the 'always up' server for performing connection check.")
    public String connectionCheckUrl;

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        CmdLineParser p = new CmdLineParser(main);
        p.parseArgument(args);

        if (main.www!=null) {
            main.output = new File(main.www,"update-center.json");
            main.htaccess = new File(main.www,"latest/.htaccess");
            main.indexHtml = new File(main.www,"index.html");
        }

        main.run();
    }

    public void run() throws Exception {

        MavenRepository repo = new MavenRepository();

        updateIndexHtml(repo);

        PrintWriter latestRedirect = new PrintWriter(new FileWriter(htaccess), true);

        JSONObject root = new JSONObject();
        root.put("updateCenterVersion","1");    // we'll bump the version when we make incompatible changes
        root.put("core", buildCore(repo, latestRedirect));
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

        PrintWriter pw = new PrintWriter(new FileWriter(output));
        pw.println("updateCenter.post(");
        pw.println(root.toString(2));
        pw.println(");");
        pw.close();
        latestRedirect.close();
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
     * Updates the version number display in http://hudson-ci.org/index.html.
     */
    private void updateIndexHtml(MavenRepository repo) throws IOException, AbstractArtifactResolutionException {
        if (indexHtml!=null) {
            VersionNumber latest = repo.getHudsonWar().firstKey();
            System.out.println("Latest version is "+latest);
            String content = IOUtils.toString(new FileInputStream(indexHtml), "UTF-8");
            // replace text inside the marker 
            content = content.replaceFirst("LATEST_VERSION.+/LATEST_VERSION","LATEST_VERSION-->"+latest+"<!--/LATEST_VERSION");
            FileOutputStream out = new FileOutputStream(indexHtml);
            IOUtils.write(content, out,"UTF-8");
            out.close();
        }
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
            HPI latest = versions.get(0);
            HPI previous = versions.size()>1 ? versions.get(1) : null;

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
        HudsonWar latest = wars.get(wars.firstKey());
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

    static {
        addProvider(new BouncyCastleProvider());
    }
}
