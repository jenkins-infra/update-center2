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
import org.mortbay.util.QuotedStringTokenizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.security.Security.addProvider;

/**
 * @author Kohsuke Kawaguchi
 */
public class Signer {
    @Option(name="-key",usage="Private key to sign the update center. Must be used in conjunction with -certificate.")
    public File privateKey = null;

    @Option(name="-certificate",usage="X509 certificate for the private key given by the -key option. Specify additional -certificate options to pass in intermediate certificates, if any.")
    public List<File> certificates = new ArrayList<File>();

    @Option(name="-root-certificate",usage="Additional root certificates")
    public List<File> rootCA = new ArrayList<File>();

    // debug option. spits out the canonical update center file used to compute the signature
    @Option(name="-canonical")
    public File canonical = null;

    /**
     * Parses JENKINS_SIGNER environment variable as the argument list and configure the instance.
     */
    public Signer configureFromEnvironment() throws CmdLineException {
        List<String> args = new ArrayList<String>();

        String env = System.getenv("JENKINS_SIGNER");
        if (env==null)      return this;

        QuotedStringTokenizer qst = new QuotedStringTokenizer(env," ");
        while (qst.hasMoreTokens()) {
            args.add(qst.nextToken());
        }
        new CmdLineParser(this).parseArgument(args);
        return this;
    }

    /**
     * Checks if the signer is properly configured to generate a signature
     *
     * @throws CmdLineException
     *      If the configuration is partial and it's not clear whether the user intended to sign or not to sign.
     */
    public boolean isConfigured() throws CmdLineException {
        if(privateKey!=null && !certificates.isEmpty())
            return true;
        if (privateKey!=null || !certificates.isEmpty())
            throw new CmdLineException("private key and certificate must be both specified");
        return false;
    }

    /**
     * Generates a canonicalized JSON format of the given object, and put the signature in it.
     * Because it mutates the signed object itself, validating the signature needs a bit of work,
     * but this enables a signature to be added transparently.
     *
     * @return
     *      The same value passed as the argument so that the method can be used like a filter.
     */
    public JSONObject sign(JSONObject o) throws GeneralSecurityException, IOException, CmdLineException {
        if (!isConfigured())    return o;

        JSONObject sign = new JSONObject();

        List<X509Certificate> certs = getCertificateChain();
        X509Certificate signer = certs.get(0); // the first one is the signer, and the rest is the chain to a root CA.

        PrivateKey key = ((KeyPair)new PEMReader(new FileReader(privateKey)).readObject()).getPrivate();

        // first, backward compatible signature for <1.433 Jenkins that forgets to flush the stream.
        // we generate this in the original names that those Jenkins understands.
        SignatureGenerator sg = new SignatureGenerator(signer, key);
        o.writeCanonical(new OutputStreamWriter(sg.getOut(),"UTF-8"));
        sg.addRecord(sign,"");

        // then the correct signature, into names that don't collide.
        OutputStream raw = new NullOutputStream();
        if (canonical!=null) {
            raw = new FileOutputStream(canonical);
        }
        sg = new SignatureGenerator(signer, key);
        o.writeCanonical(new OutputStreamWriter(new TeeOutputStream(sg.getOut(),raw),"UTF-8")).close();
        sg.addRecord(sign,"correct_");

        // and certificate chain
        JSONArray a = new JSONArray();
        for (X509Certificate cert : certs)
            a.add(new String(Base64.encodeBase64(cert.getEncoded())));
        sign.put("certificates",a);

        o.put("signature",sign);

        return o;
    }

    /**
     * Generates a digest and signature. Can be only used once, and then it needs to be thrown away.
     */
    static class SignatureGenerator {
        private final MessageDigest sha1;
        private final Signature sig;
        private final TeeOutputStream out;
        private final Signature verifier;

        SignatureGenerator(X509Certificate signer, PrivateKey key) throws GeneralSecurityException, IOException {
            // this is for computing a digest
            sha1 = MessageDigest.getInstance("SHA1");
            DigestOutputStream dos = new DigestOutputStream(new NullOutputStream(), sha1);

            // this is for computing a signature
            sig = Signature.getInstance("SHA1withRSA");
            sig.initSign(key);
            SignatureOutputStream sos = new SignatureOutputStream(sig);

            // this is for verifying that signature validates
            verifier = Signature.getInstance("SHA1withRSA");
            verifier.initVerify(signer.getPublicKey());
            SignatureOutputStream vos = new SignatureOutputStream(verifier);

            out = new TeeOutputStream(new TeeOutputStream(dos, sos), vos);
        }

        public TeeOutputStream getOut() {
            return out;
        }

        public void addRecord(JSONObject sign, String prefix) throws GeneralSecurityException, IOException {
            // digest
            byte[] digest = sha1.digest();
            sign.put(prefix+"digest",new String(Base64.encodeBase64(digest)));

            // signature
            byte[] s = sig.sign();
            sign.put(prefix+"signature",new String(Base64.encodeBase64(s)));

            // did the signature validate?
            if (!verifier.verify(s))
                throw new GeneralSecurityException("Signature failed to validate. Either the certificate and the private key weren't matching, or a bug in the program.");
        }
    }

    /**
     * Loads a certificate chain and makes sure it's valid.
     */
    protected List<X509Certificate> getCertificateChain() throws IOException, GeneralSecurityException {
        CertificateFactory cf = CertificateFactory.getInstance("X509");
        List<X509Certificate> certs = new ArrayList<X509Certificate>();
        for (File f : certificates) {
            X509Certificate c = loadCertificate(cf, f);
            c.checkValidity(new Date(System.currentTimeMillis()+ TimeUnit.DAYS.toMillis(30)));
            certs.add(c);
        }

        Set<TrustAnchor> rootCAs = CertificateUtil.getDefaultRootCAs();
        rootCAs.add(new TrustAnchor((X509Certificate)cf.generateCertificate(getClass().getResourceAsStream("/hudson-community.cert")),null));
        rootCAs.add(new TrustAnchor((X509Certificate)cf.generateCertificate(getClass().getResourceAsStream("/jenkins-update-center-root-ca.cert")),null));
        for (File f : rootCA) {
            rootCAs.add(new TrustAnchor(loadCertificate(cf, f),null));
        }

        try {
            CertificateUtil.validatePath(certs,rootCAs);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        return certs;
    }

    private X509Certificate loadCertificate(CertificateFactory cf, File f) throws CertificateException, IOException {
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                X509Certificate c = (X509Certificate) cf.generateCertificate(in);
                c.checkValidity();
                return c;
            } finally {
                in.close();
            }
        } catch (CertificateException e) {
            throw (IOException)new IOException("Failed to load certificate "+f).initCause(e);
        } catch (IOException e) {
            throw (IOException)new IOException("Failed to load certificate "+f).initCause(e);
        }
    }

    static {
        addProvider(new BouncyCastleProvider());
    }
}
