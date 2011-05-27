/*
 * The MIT License
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
import org.bouncycastle.openssl.PEMReader;
import org.jvnet.hudson.crypto.CertificateUtil;
import org.jvnet.hudson.crypto.SignatureOutputStream;
import org.kohsuke.args4j.Option;

import java.io.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class Signing {

    public final File privateKey;

    public final List<File> certificates;

    public Signing(File privateKey, List<File> certificates) throws Exception
    {
        this.privateKey = privateKey;
        this.certificates = certificates;

        if (privateKey!=null || !certificates.isEmpty())
            throw new Exception("private key and certificate must be both specified");
    }

    /**
     * Generates a canonicalized JSON format of the given object, and put the signature in it.
     * Because it mutates the signed object itself, validating the signature needs a bit of work,
     * but this enables a signature to be added transparently.
     */
    public void sign(JSONObject o) throws GeneralSecurityException, IOException {
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
}
