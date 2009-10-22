import junit.framework.TestCase;
import org.jvnet.hudson.update_center.CertUtil;

import java.security.GeneralSecurityException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * @author Kohsuke Kawaguchi
 */
public class PKIXTest extends TestCase {
    /**
     * Makes sure valid certificate chain validates.
     */
    public void testPathValidation() throws Exception {
        X509Certificate site = load("site.crt");
        X509Certificate sun = load("sun.crt");
        X509Certificate verisign = load("verisign.crt");

        CertUtil.validatePath(Arrays.asList(site,sun));

        assertFailedValidation(sun,site);   // invalid order
        assertFailedValidation(site);       // missing link

    }

    private void assertFailedValidation(X509Certificate... certs) throws GeneralSecurityException {
        try {
            CertUtil.validatePath(Arrays.asList(certs));
            fail();
        } catch (CertPathValidatorException e) {
            System.out.println(e.getMessage());
        }
    }

    private X509Certificate load(String res) throws GeneralSecurityException {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(getClass().getResourceAsStream(res));
    }

//    private static void test1() throws CertificateException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, KeyStoreException, IOException, CertPathBuilderException {
//        X509Certificate verisign = loadCertificate(new FileInputStream("/home/kohsuke/Desktop/VerisignClass3PublicPrimaryCertificationAuthority-G2.crt"));
//        X509Certificate sun = loadCertificate(new FileInputStream("/home/kohsuke/Desktop/SunMicrosystemsIncSSLCA.crt"));
//        X509Certificate cert = loadCertificate(new FileInputStream("/home/kohsuke/Desktop/identity.sun.com.crt"));
//        CertStore cs = CertStore.getInstance("Collection",new CollectionCertStoreParameters(Arrays.asList(sun,verisign)));
//
//
//        KeyStore ks = KeyStore.getInstance("JKS");
//        ks.load(null);
//        ks.setCertificateEntry("root", verisign);
//        ks.setCertificateEntry("root2", sun);
//
//        X509CertSelector target = new X509CertSelector();
//        target.setCertificate(cert);
//        CertPathBuilder builder = CertPathBuilder.getInstance("PKIX");
//        PKIXBuilderParameters params = new PKIXBuilderParameters(ks,target);
//        params.setCertStores(Arrays.asList(cs));
//        CertPathBuilderResult result = builder.build(params);
//        System.out.println(result);
//        result.getCertPath().getEncoded();
//    }

//    private static X509Certificate loadCertificate(FileInputStream src) throws CertificateException, FileNotFoundException {
//        return (X509Certificate) CertificateFactory.getInstance("X.509")
//                .generateCertificate(src);
//    }
}
