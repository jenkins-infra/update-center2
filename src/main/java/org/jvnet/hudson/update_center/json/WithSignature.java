package org.jvnet.hudson.update_center.json;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.TeeOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

/**
 * Support generation of JSON output with included checksum + signatures block for the same JSON output.
 */
public abstract class WithSignature {
    private JsonSignature signature;

    @JSONField
    public JsonSignature getSignature() {
        return signature;
    }

    /**
     * Generate JSON checksums and add a signature block to the JSON written to the file.
     *
     * This will run JSON generation twice: Once without the signature block to compute checksums, and a second time to
     * include the signature block and write it to the output file.
     *
     * Because of this, it is important that (with the exception of {@link #getSignature()} all getters etc. of subtypes
     * and any types reachable through the object graph for JSON generation return the same content on subsequent calls.
     *
     * Additionally, implementations of this class, and all types reachable via fields and getters used during JSON
     * generation should employ some sort of caching to prevent expensive computations from being invoked twice.
     *
     * @param outputFile the file to write to
     * @throws IOException when any IO error occurs
     */
    public void writeWithSignature(File outputFile) throws IOException {
        // TODO actually compute signatures, for now this only does checksums
        signature = null;
        MessageDigest sha1 = DigestUtils.getSha1Digest();
        MessageDigest sha512 = DigestUtils.getSha512Digest();
        DigestOutputStream dos1 = new DigestOutputStream(new NullOutputStream(), sha1);
        DigestOutputStream dos512 = new DigestOutputStream(new NullOutputStream(), sha512);

        OutputStream out = new TeeOutputStream(dos1, dos512);

        JSON.writeJSONString(out, this, SerializerFeature.DisableCircularReferenceDetect);
        out.flush();

        byte[] digest = sha1.digest();
        String sha1s = new String(Base64.encodeBase64(digest), StandardCharsets.UTF_8);
        String sha256s = Hex.encodeHexString(sha512.digest());
        signature = new JsonSignature(null, sha1s, null, sha256s, null);
        JSON.writeJSONString(Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8), this, SerializerFeature.DisableCircularReferenceDetect);
    }
}
