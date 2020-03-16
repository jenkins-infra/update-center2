package org.jvnet.hudson.update_center;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MavenArtifactSource extends ArtifactSource {
    @Override
    public Digests getDigests(MavenArtifact artifact) throws IOException {
        try (FileInputStream fin = new FileInputStream(artifact.resolve())) {
            MessageDigest sha1 = DigestUtils.getSha1Digest();
            MessageDigest sha256 = DigestUtils.getSha256Digest();
            byte[] buf = new byte[2048];
            int len;
            while ((len=fin.read(buf,0,buf.length)) >= 0) {
                sha1.update(buf, 0, len);
                sha256.update(buf, 0, len);
            }

            Digests ret = new Digests();
            ret.sha1 = new String(Base64.encodeBase64(sha1.digest()), "UTF-8");
            ret.sha256 = new String(Base64.encodeBase64(sha256.digest()), "UTF-8");
            return ret;
        }
    }

    @Override
    public Manifest getManifest(MavenArtifact artifact) throws IOException {
        try (InputStream is = getZipFileEntry(artifact, "META-INF/MANIFEST.MF")) {
            return new Manifest(is);
        } catch (IOException x) {
            throw new IOException("Failed to read manifest from "+artifact, x);
        }
    }

    @Override
    public InputStream getZipFileEntry(MavenArtifact artifact, String path) throws IOException {
        File f = artifact.resolve();
        try (FileInputStream fis = new FileInputStream(f);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream stream = new ZipInputStream(bis)) {

            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                if (path.equals(entry.getName())) {
                    // pull out the file from entire zip, copy it into memory, and throw away the rest
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();

                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = stream.read(buffer)) > -1 ) {
                        baos.write(buffer, 0, len);
                    }
                    baos.flush();

                    return new ByteArrayInputStream(baos.toByteArray());
                }
            }
            throw new IOException("No entry " + path + " found in " + artifact);
        } catch (IOException x) {
            throw new IOException("Failed read from " + f + ": " + x.getMessage(), x);
        }
    }
}
