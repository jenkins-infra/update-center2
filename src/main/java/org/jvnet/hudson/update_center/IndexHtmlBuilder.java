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

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.ByteArrayOutputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
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
        return new PrintWriter(new FileWriter(new File(dir,"index.html")));
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

    public void add(MavenArtifact a) throws IOException {
        add(a.getURL().getPath(), a.version);
    }

    public void add(String url, String caption) throws MalformedURLException {
        out.println(
            "<tr><td><img src='/images/jar.png'/></td><td><a href='"+ url +"'>"+ caption +"</a></td></tr>"
        );
    }

    public void close() throws IOException {
        out.println("</table>\n" +
                "<hr>\n" +
                "</body></html>");
        out.close();
    }
}
