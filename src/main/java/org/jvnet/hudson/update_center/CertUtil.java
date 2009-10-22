package org.jvnet.hudson.update_center;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility code to work around horrible Java Crypto API.
 *
 * @author Kohsuke Kawaguchi
 */
public class CertUtil {
    /**
     * Obtains the list of default root CAs installed in the JRE.
     */
    public static Set<TrustAnchor> getDefaultRootCAs() throws NoSuchAlgorithmException, KeyStoreException {
        X509TrustManager x509tm = getDefaultX509TrustManager();

        Set<TrustAnchor> rootCAs = new HashSet<TrustAnchor>();
        for (X509Certificate c : x509tm.getAcceptedIssuers()) {
            rootCAs.add(new TrustAnchor(c,null));
        }
        return rootCAs;
    }

    /**
     * Loads the system default {@link X509TrustManager}.
     */
    public static X509TrustManager getDefaultX509TrustManager() throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore)null);

        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new IllegalStateException("X509TrustManager is not found");
    }

    /**
     * Validate a certificate chain. Normal return indicates a successful validation.
     */
    public static PKIXCertPathValidatorResult validatePath(List<X509Certificate> certs) throws GeneralSecurityException {
        CertPathValidator cpv = CertPathValidator.getInstance("PKIX");
        PKIXParameters params = new PKIXParameters(getDefaultRootCAs());
        params.setRevocationEnabled(false);

        CertificateFactory cf = CertificateFactory.getInstance("X509");
        CertPath path = cf.generateCertPath(certs);

        return (PKIXCertPathValidatorResult) cpv.validate(path, params);
    }
}
