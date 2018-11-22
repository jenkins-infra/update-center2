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

import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.util.encoders.Base64;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.ByteArrayOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Generates index.html that has a list of files.
 *
 * @author Kohsuke Kawaguchi
 */
public class IndexHtmlBuilder implements Closeable {
    private final PrintWriter out;

    public IndexHtmlBuilder(File dir, String title) throws IOException {
        this(openIndexHtml(dir),title);
    }

    private static PrintWriter openIndexHtml(File dir) throws IOException {
        if (dir==null)  return new PrintWriter(new ByteArrayOutputStream()); // ignore output
        
        dir.mkdirs();
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(dir,"index.html")), "UTF-8"));
    }

    public IndexHtmlBuilder(PrintWriter out, String title) {
        this.out = out;

        out.println(
                "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n" +
                "<html>\n" +
                " <head>\n" +
                "  <title>"+title+"</title>\n" +
                " </head>\n" +
                " <body>\n" +
                "<h1>"+title+"</h1>\n" +
                "<hr>\n" +
                "<table>"
        );
    }

    private String base64ToHex(String base64) {
        byte[] decodedBase64 = Base64.decode(base64.getBytes());
        return Hex.encodeHexString(decodedBase64);
    }

    public void add(MavenArtifact a) throws IOException {
        ArtifactSource.Digests digests = a.getDigests();
        if (digests == null) {
            return;
        }
        String checksums = "SHA-1: " + base64ToHex(digests.sha1);
        if (digests.sha256 != null) {
            checksums += ", SHA-256: " + base64ToHex(digests.sha256);
        }
        add(a.getURL().getPath(), a.getTimestampAsDate(), a.version, checksums);
    }

    public void add(String url, String caption) throws MalformedURLException {
        add(url, null, caption, null);
    }

    public void add(String url, Date releaseDate, String caption, String metadata) throws MalformedURLException {
        String metadataString = "";
        if (metadata != null) {
            metadataString = "<td>" + metadata + "</td>";
        }

        String releaseDateString = "";
        if (releaseDate != null) {
            releaseDateString = " title='Released " + SimpleDateFormat.getDateInstance().format(releaseDate) + "' ";
        }

        out.println("<tr><td><img src='http://jenkins-ci.org/images/jar.png' /></td><td><a href='" + url + "'" + releaseDateString + "'>"
                + caption + "</a></td>" + metadataString + "</tr>");
    }

    public void close() throws IOException {
        out.println("</table>\n" +
                "<hr>\n" +
                "</body></html>");
        out.close();
    }
}
