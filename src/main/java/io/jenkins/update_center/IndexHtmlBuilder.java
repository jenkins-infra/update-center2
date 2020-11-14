/*
 * The MIT License
 *
 * Copyright (c) 2004-2020, Sun Microsystems, Inc. and other contributors
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
package io.jenkins.update_center;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.output.NullWriter;
import org.bouncycastle.util.encoders.Base64;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generates index.html that has a list of files.
 *
 * @author Kohsuke Kawaguchi
 */
public class IndexHtmlBuilder implements Closeable {
    private final PrintWriter out;
    private final String template;
    private StringBuilder content;
    private static String globalTemplate = null;

    public IndexHtmlBuilder(File dir, String title) throws IOException {
        this(openIndexHtml(dir), title);
    }

    private static PrintWriter openIndexHtml(File dir) throws IOException {
        if (dir == null) {
            return new PrintWriter(new NullWriter()); // ignore output
        }

        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("Failed to create " + dir);
        }
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(dir, "index.html")), StandardCharsets.UTF_8));
    }

    private IndexHtmlBuilder(PrintWriter out, String title) {
        this.out = out;
        if (globalTemplate == null) {
            initTemplate();
        }
        template = globalTemplate.replace("{{ title }}", title)
                .replace("{{ description }}", "Download previous versions of " + title);
        content = new StringBuilder("<div id='grid-box'><div class=\"container\"><h1 class=\"mt-3\">")
                .append(title).append("</h1><ul class=\"artifact-list\">\n");
    }

    private void initTemplate() {
        Request request = new Request.Builder()
                .url("https://www.jenkins.io/template/").get().build();

        try {
            try (final ResponseBody body = new OkHttpClient().newCall(request).execute().body()) {
                Objects.requireNonNull(body); // guaranteed to be non-null by Javadoc
                globalTemplate = body.string();
            }
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Problem loading template", ioe);
        }
        Path style = Paths.get(Main.resourcesDir.getAbsolutePath(), "style.css");
        try {
            String styleContent = new String(Files.readAllBytes(style), StandardCharsets.UTF_8);
            globalTemplate = globalTemplate.replaceAll("</head>",
                    "<style>" + styleContent + "</style></head>");
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, "Problem loading template", ioe);
        }
    }

    private String base64ToHex(String base64) {
        byte[] decodedBase64 = Base64.decode(base64.getBytes(StandardCharsets.US_ASCII));
        return Hex.encodeHexString(decodedBase64);
    }

    public void add(MavenArtifact a) throws IOException {
        MavenRepository.Digests digests = a.getDigests();
        if (digests == null) {
            return;
        }
        String checksums = "SHA-1: <code>" + base64ToHex(digests.sha1) + "</code>";
        if (digests.sha256 != null) {
            checksums += ", SHA-256: <code>" + base64ToHex(digests.sha256) + "</code>";
        }
        add(a.getDownloadUrl().getPath(), a.getTimestampAsDate(), a.version, checksums);
    }

    public void add(String url, String caption) {
        add(url, null, caption, null);
    }

    public void add(String url, Date releaseDate, String caption, String metadata) {
        String metadataString = "";
        if (metadata != null) {
            metadataString = "<td>" + metadata + "</td>";
        }

        String releaseDateString = "";
        if (releaseDate != null) {
            releaseDateString = " Released: " + SimpleDateFormat.getDateInstance().format(releaseDate);
        }

        content.append("<li><a href='").append(url)
                .append("'>").append(caption).append(releaseDateString)
                .append("</a><div class=\"checksums\">").append(metadataString)
                .append("</div>").append("</li>\n");
    }

    public void close() {
        content.append("</ul></div></div>\n");
        out.println(template
                .replace("<div id='grid-box'></div>", content.toString()));
        out.close();
    }

    private static final Logger LOGGER = Logger.getLogger(IndexHtmlBuilder.class.getName());
}
