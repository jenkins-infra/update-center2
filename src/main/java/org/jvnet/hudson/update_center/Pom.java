/*
 * The MIT License
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

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Pom {

    private final SAXReader xmlReader;

    private Document pom;
    private Pom      parent;

    public Pom(InputStream pomFile) throws IOException, DocumentException {
        this(pomFile, null);
    }

    public Pom(InputStream pomFile, Pom parent) throws IOException, DocumentException
    {
        this.parent = parent;
        DocumentFactory factory = new DocumentFactory();
        factory.setXPathNamespaceURIs(
                Collections.singletonMap("m", "http://maven.apache.org/POM/4.0.0"));
        this.xmlReader = new SAXReader(factory);

        this.pom = readPOM(pomFile);
    }

      private Document readPOM(InputStream pomFile) throws IOException, DocumentException {
        try {
            return xmlReader.read(pomFile);
        } catch (DocumentException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public Node selectSingleNode(String path) {
        Node result = pom.selectSingleNode(path);
        if (result == null)
            result = pom.selectSingleNode(path.replaceAll("/", "/m:"));
        return result;
    }

    public String selectSingleValue(String path) {
        Node node = selectSingleNode(path);
        return node != null ? ((Element)node).getTextTrim() : null;
    }

    /**
     * Get hostname of SCM specified in POM of latest release, or null.
     * Used to determine if source lives in github or svn.
     */
    public String getScmHost() {
        if (pom != null) {
            String scm = selectSingleValue("/project/scm/connection");
            if (scm == null) {
                // Try parent pom
                Element parent = (Element)selectSingleNode("/project/parent");
                if (parent != null) try {
                    return getParent().getScmHost();

                } catch (Exception ex) {
                    System.out.println("** Failed to read parent pom");
                    ex.printStackTrace();
                }
            }
            if (scm != null) {
                Matcher m = HOSTNAME_PATTERN.matcher(scm);
                if (m.find())
                    return m.group(1);
                else System.out.println("** Unable to parse scm/connection: " + scm);
            }
            else System.out.println("** No scm/connection found in pom");
        }
        return null;
    }

    public Pom getParent() {
        return parent;
    }

    public void setParent(Pom parent) {
        this.parent = parent;
    }

    private static final Pattern HOSTNAME_PATTERN =
           Pattern.compile("(?:://(?:\\w*@)?|scm:git:\\w*@)([\\w.-]+)[/:]");

}
