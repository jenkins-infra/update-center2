package io.jenkins.update_center.json;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.serializer.SerializerFeature;
import io.jenkins.update_center.Signer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Support generation of JSON output with included checksum + signatures block for the same JSON output.
 */
public abstract class WithSignature {
    private JsonSignature signature;
    private final String generationTimestamp = DateTimeFormatter.ISO_DATE_TIME.format(Instant.now().atOffset(ZoneOffset.UTC).withNano(0));

    @JSONField
    public JsonSignature getSignature() {
        return signature;
    }

    /**
     * Returns a string with the current date and time in ISO-8601 format.
     * It doesn't have fractional seconds and the timezone is always UTC ('Z').
     *
     * @return a string with the current date and time in the format YYYY-MM-DD'T'HH:mm:ss'Z'
     */
    public String getGenerationTimestamp() {
        return generationTimestamp;
    }

    /**
     * Generate JSON checksums and add a signature block to the JSON written to the specified {@link Writer}.
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
     * @param writer the writer to write to
     * @param signer the signer
     * @param pretty whether to pretty-print format the JSON output
     * @throws IOException when any IO error occurs
     * @throws GeneralSecurityException when an issue during signing occurs
     */
    private void writeWithSignature(Writer writer, Signer signer, boolean pretty) throws IOException, GeneralSecurityException {
        signature = null;

        final String unsignedJson = JSON.toJSONString(this, SerializerFeature.DisableCircularReferenceDetect);
        signature = signer.sign(unsignedJson);

        if (pretty) {
            JSON.writeJSONString(writer, this, SerializerFeature.DisableCircularReferenceDetect, SerializerFeature.PrettyFormat);
        } else {
            JSON.writeJSONString(writer, this, SerializerFeature.DisableCircularReferenceDetect);
        }
        writer.flush();
    }

    /**
     * Convenience wrapper for {@link #writeWithSignature(Writer, Signer, boolean)} writing to a file.
     *
     * @param outputFile the file to write to
     * @param signer the signer
     * @param pretty whether to pretty-print format the JSON output
     * @throws IOException when any IO error occurs
     * @throws GeneralSecurityException when an issue during signing occurs
     */
    public void writeWithSignature(File outputFile, Signer signer, boolean pretty) throws IOException, GeneralSecurityException {
        try (OutputStream os = Files.newOutputStream(outputFile.toPath()); OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            writeWithSignature(writer, signer, pretty);
        }
    }

    /**
     * Like {@link #writeWithSignature(File, Signer, boolean)} but the output is returned as a String.
     * @param signer the signer
     * @param pretty whether to pretty-print format the JSON output
     * @return the JSON output
     * @throws IOException when any IO error occurs
     * @throws GeneralSecurityException when an issue during signing occurs
     */
    public String encodeWithSignature(Signer signer, boolean pretty)  throws IOException, GeneralSecurityException {
        StringWriter writer = new StringWriter();
        writeWithSignature(writer, signer, pretty);
        return writer.getBuffer().toString();
    }
}
