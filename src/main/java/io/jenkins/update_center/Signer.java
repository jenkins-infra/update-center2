package io.jenkins.update_center;

import io.jenkins.update_center.json.JsonSignature;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.jvnet.hudson.crypto.CertificateUtil;
import org.jvnet.hudson.crypto.SignatureOutputStream;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.security.Security.addProvider;

/**
 * @author Kohsuke Kawaguchi
 */
public class Signer {
    private static final Logger LOGGER = Logger.getLogger(Signer.class.getName());

    @Option(name="--key",usage="Private key to sign the update center. Must be used in conjunction with -certificate.")
    public File privateKey = null;

    @Option(name="--certificate",usage="X509 certificate for the private key given by the -key option. Specify additional -certificate options to pass in intermediate certificates, if any. These certificates will be part of update site metadata.")
    public List<File> certificates;

    @Option(name="--root-certificate",usage="Additional root certificates for use in validation. These certificates will not be part of update site metadata.")
    public List<File> rootCA;

    /**
     * Checks if the signer is properly configured to generate a signature
     */
    public boolean isConfigured() {
        if(privateKey != null && certificates != null && !certificates.isEmpty()) {
            return true;
        }
        if (privateKey != null || certificates != null && !certificates.isEmpty()) {
            throw new IllegalStateException("Both -key and -certificate must be specified");
        }
        // neither argument is provided, so we just don't sign the JSON
        return false;
    }

    public JsonSignature sign(String json) throws GeneralSecurityException, IOException {
        if (!isConfigured()) {
            return null;
        }

        JsonSignature sign = new JsonSignature();

        List<X509Certificate> certs = getCertificateChain();
        X509Certificate signer = certs.get(0); // the first one is the signer, and the rest is the chain to a root CA.

        PrivateKey key = ((KeyPair) new PEMReader(Files.newBufferedReader(privateKey.toPath(), StandardCharsets.UTF_8)).readObject()).getPrivate();

        // the correct signature (since Jenkins 1.433); no longer generate wrong signatures for older releases.
        SignatureGenerator sg = new SignatureGenerator(signer, key);

        try (OutputStreamWriter osw = new OutputStreamWriter(sg.out, StandardCharsets.UTF_8)) {
            IOUtils.write(json, osw);
        }
        sg.fill(sign);

        // and certificate chain
        List<String> certificates = new ArrayList<>();
        for (X509Certificate cert : certs)
            certificates.add(new String(Base64.encodeBase64(cert.getEncoded()), StandardCharsets.UTF_8));
        sign.setCertificates(certificates);

        return sign;
    }

    /**
     * Generates a digest and signature. Can be only used once, and then it needs to be thrown away.
     */
    static class SignatureGenerator {
        private final MessageDigest sha1;
        private final Signature sha1sig;
        private final MessageDigest sha512;
        private final Signature sha512sig;
        private final TeeOutputStream out;
        private final Signature verifier1;
        private final Signature verifier512;

        SignatureGenerator(X509Certificate signer, PrivateKey key) throws GeneralSecurityException {
            // this is for computing a digest
            sha1 = DigestUtils.getSha1Digest();
            sha512 = DigestUtils.getSha512Digest();
            DigestOutputStream dos1 = new DigestOutputStream(new NullOutputStream(), sha1);
            DigestOutputStream dos512 = new DigestOutputStream(new NullOutputStream(), sha512);

            // this is for computing a signature
            sha1sig = Signature.getInstance("SHA1withRSA");
            sha1sig.initSign(key);
            SignatureOutputStream sos1 = new SignatureOutputStream(sha1sig);

            sha512sig = Signature.getInstance("SHA512withRSA");
            sha512sig.initSign(key);
            SignatureOutputStream sos512 = new SignatureOutputStream(sha512sig);

            // this is for verifying that signature validates
            verifier1 = Signature.getInstance("SHA1withRSA");
            verifier1.initVerify(signer.getPublicKey());
            SignatureOutputStream vos1 = new SignatureOutputStream(verifier1);

            verifier512 = Signature.getInstance("SHA512withRSA");
            verifier512.initVerify(signer.getPublicKey());
            SignatureOutputStream vos512 = new SignatureOutputStream(verifier512);

            out = new TeeOutputStream(new TeeOutputStream(new TeeOutputStream(new TeeOutputStream(new TeeOutputStream(dos1, sos1), vos1), dos512), sos512), vos512);
        }

        public TeeOutputStream getOut() {
            return out;
        }

        private void fill(JsonSignature signature) throws GeneralSecurityException {
            // digest
            byte[] digest = sha1.digest();
            signature.setDigest(new String(Base64.encodeBase64(digest), StandardCharsets.UTF_8));
            signature.setDigest512(Hex.encodeHexString(sha512.digest()));

            // signature
            byte[] s1 = sha1sig.sign();
            byte[] s512 = sha512sig.sign();
            signature.setSignature(new String(Base64.encodeBase64(s1), StandardCharsets.UTF_8));
            signature.setSignature512(Hex.encodeHexString(s512));

            // did the signature validate?
            if (!verifier1.verify(s1))
                throw new GeneralSecurityException("Signature (SHA-1) failed to validate. Either the certificate and the private key weren't matching, or a bug in the program.");
            if (!verifier512.verify(s512))
                throw new GeneralSecurityException("Signature (SHA-512) failed to validate. Either the certificate and the private key weren't matching, or a bug in the program.");
        }
    }

    /**
     * Loads a certificate chain and makes sure it's valid.
     */
    private List<X509Certificate> getCertificateChain() throws IOException, GeneralSecurityException {
        CertificateFactory cf = CertificateFactory.getInstance("X509");
        List<X509Certificate> certs = new ArrayList<>();
        if (certificates != null) {
            for (File f : certificates) {
                X509Certificate c = loadCertificate(cf, f);
                c.checkValidity(new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)));
                if (certs.isEmpty()) {
                    // This is the first cert we add to the list, i.e. it's the one most likely to expire soonest
                    LOGGER.log(Level.INFO, () -> "Update site certificate: Subject: " + c.getSubjectDN() + " Issuer: " + c.getIssuerDN() + " NotBefore: " + c.getNotBefore() + " NotAfter: " + c.getNotAfter());
                }
                certs.add(c);
            }
        }

        for (X509Certificate certificate : certs) {
            LOGGER.log(Level.CONFIG, "Certificate: " + certificate);
        }

        Set<TrustAnchor> rootCAs = new HashSet<>();
        if (rootCA != null) {
            for (File f : rootCA) {
                rootCAs.add(new TrustAnchor(loadCertificate(cf, f), null));
            }
        }

        for (TrustAnchor anchor : rootCAs) {
            LOGGER.log(Level.CONFIG, "Trust anchor: " + anchor);
        }

        if (rootCA == null || rootCA.size() == 0) {
            LOGGER.log(Level.WARNING, "No root CA specified, skipping path validation");
        } else {
            try {
                CertificateUtil.validatePath(certs, rootCAs);
            } catch (GeneralSecurityException e) {
                LOGGER.log(Level.WARNING, "Failed path validation", e);
            }
        }
        return certs;
    }

    private X509Certificate loadCertificate(CertificateFactory cf, File f) throws IOException {
        try {
            try (FileInputStream in = new FileInputStream(f)) {
                X509Certificate c = (X509Certificate) cf.generateCertificate(in);
                c.checkValidity();
                return c;
            }
        } catch (CertificateException | IOException e) {
            throw (IOException) new IOException("Failed to load certificate "+f).initCause(e);
        }
    }

    static {
        addProvider(new BouncyCastleProvider());
    }
}
