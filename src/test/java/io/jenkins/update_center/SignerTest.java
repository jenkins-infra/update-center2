package io.jenkins.update_center;

import io.jenkins.update_center.json.JsonSignature;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

// The resources used in these tests expire in 2034.
// Generated with:
//  OpenSSL 3.3.2 3 Sep 2024 (Library: OpenSSL 3.3.2 3 Sep 2024)
// Using:
//  openssl genrsa [-traditional] -out FILENAME.key 4096
//  openssl req -new -x509 -days 3650 -key FILENAME.key -out FILENAME.cert -subj "/C=/ST=/L=/O=SignerTest/OU=SignerTest/CN=SignerTest/emailAddress=example@example.invalid"
public class SignerTest {
    @Test
    public void traditionalFormat() throws IOException, GeneralSecurityException {
        Signer signer = new Signer();
        try (InputStream is = SignerTest.class.getResourceAsStream("/traditional.key")) {
            final Path filePath = Files.createTempFile("update-center2-", ".key");
            Files.copy(is, filePath, StandardCopyOption.REPLACE_EXISTING);
            signer.privateKey = filePath.toFile();
        }
        try (InputStream is = SignerTest.class.getResourceAsStream("/traditional.cert")) {
            final Path filePath = Files.createTempFile("update-center2-", ".cert");
            Files.copy(is, filePath, StandardCopyOption.REPLACE_EXISTING);
            signer.certificates = Collections.singletonList(filePath.toFile());
        }

        final JsonSignature signature = signer.sign("{}");
        assertThat(signature.getSignature512(), notNullValue());
        assertThat(signature.getDigest512(), notNullValue());
        assertThat(signature.getSignature(), notNullValue());
        assertThat(signature.getCertificates().size(), is(1));
    }

    @Test
    public void modernFormat() throws IOException, GeneralSecurityException {
        Signer signer = new Signer();
        try (InputStream is = SignerTest.class.getResourceAsStream("/modern.key")) {
            final Path filePath = Files.createTempFile("update-center2-", ".key");
            Files.copy(is, filePath, StandardCopyOption.REPLACE_EXISTING);
            signer.privateKey = filePath.toFile();
        }
        try (InputStream is = SignerTest.class.getResourceAsStream("/modern.cert")) {
            final Path filePath = Files.createTempFile("update-center2-", ".cert");
            Files.copy(is, filePath, StandardCopyOption.REPLACE_EXISTING);
            signer.certificates = Collections.singletonList(filePath.toFile());
        }

        final JsonSignature signature = signer.sign("{}");
        assertThat(signature.getSignature512(), notNullValue());
        assertThat(signature.getDigest512(), notNullValue());
        assertThat(signature.getSignature(), notNullValue());
        assertThat(signature.getCertificates().size(), is(1));
    }
}
