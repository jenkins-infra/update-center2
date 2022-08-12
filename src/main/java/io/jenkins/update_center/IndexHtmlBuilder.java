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

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.output.NullWriter;
import org.bouncycastle.util.encoders.Base64;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Generates index.html that has a list of files.
 *
 * @author Kohsuke Kawaguchi
 */
public class IndexHtmlBuilder implements Closeable {
    private final PrintWriter out;
    private final String template;
    private final String title;
    private String subtitle;
    private final String description;
    private final StringBuilder content;
    private final String opengraphImage;

    public IndexHtmlBuilder(File dir, String title, String globalTemplate) throws IOException {
        this.out = openIndexHtml(dir);
        this.template = globalTemplate;
        this.title = title;
        this.content = new StringBuilder();
        this.subtitle = "";
        this.description = "Download previous versions of " + title;
        this.opengraphImage = "https://www.jenkins.io/images/logo-title-opengraph.png";
    }

    public IndexHtmlBuilder withSubtitle(String subtitle) {
        if (subtitle != null) {
            this.subtitle = subtitle;
        }
        return this;
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

    private String base64ToHex(String base64) {
        byte[] decodedBase64 = Base64.decode(base64.getBytes(StandardCharsets.US_ASCII));
        return Hex.encodeHexString(decodedBase64);
    }

    public void add(MavenArtifact a) throws IOException {
        MavenRepository.ArtifactMetadata artifactMetadata = a.getMetadata();
        if (artifactMetadata == null) {
            return;
        }
        if (a instanceof HPI) {
            add(a.getDownloadUrl().getPath(), a.getTimestampAsDate(), a.version, artifactMetadata, ((HPI) a).getRequiredJenkinsVersion());
        } else {
            add(a.getDownloadUrl().getPath(), a.getTimestampAsDate(), a.version, artifactMetadata, null);
        }
    }

    public void add(String url, String caption) {
        add(url, null, caption, null, null);
    }

    public void add(String url, Date releaseDate, String caption, MavenRepository.ArtifactMetadata metadata, String requiredJenkinsVersion) {
        String releaseDateString = "";
        if (releaseDate != null) {
            releaseDateString = " Released: " + SimpleDateFormat.getDateInstance().format(releaseDate);
        }

        content.append("<li").append(releaseDate == null ? "" : " id=\"" + caption + "\"")
                .append("><a class=\"version\" href='").append(url)
                .append("'>").append(caption).append("</a><div class=\"metadata\">\n<div class=\"released\">")
                .append(releaseDateString)
                .append("</div>");
        if (metadata != null) {
            content.append("\n<div class=\"checksums\">SHA-1: <code>")
                    .append(base64ToHex(metadata.sha1)).append("</code></div>");
            if (metadata.sha256 != null) {
                content.append("\n<div class=\"checksums\">SHA-256: <code>")
                        .append(base64ToHex(metadata.sha256)).append("</code></div>");
            }
        }
        if (requiredJenkinsVersion != null) {
            content.append("\n<div class=\"core-dependency\">Requires Jenkins ").append(requiredJenkinsVersion).append("</div>");
        }
        content.append("</div></li>\n");
    }

    @Override
    public void close() {
        out.println(template
                .replace("{{ title }}", title)
                .replace("{{ subtitle }}", subtitle)
                .replace("{{ description }}", description)
                .replace("{{ opengraphImage }}", opengraphImage)
                .replace("{{ content }}", content.toString()));
        out.close();
    }
}
