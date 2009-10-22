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
