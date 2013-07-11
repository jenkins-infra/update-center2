/*
 * The MIT License
 * 
 * Copyright (c) 2013 IKEDA Yasuyuki
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.sonatype.nexus.index.ArtifactInfo;

/**
 * HPI located in local.
 */
public class LocalHPI extends HPI
{
    private File jarFile;
    private URL baseUrl;
    
    public LocalHPI(
            MavenRepository repository,
            PluginHistory history,
            ArtifactInfo artifact,
            File jarFile,
            URL url
    ) throws AbstractArtifactResolutionException
    {
        super(repository, history, artifact);
        this.jarFile = jarFile;
        this.baseUrl = url;
    }
    
    @Override
    public URL getURL() throws MalformedURLException
    {
        return new URL(baseUrl, "download/" + getRelativePath());
    }
    
    @Override
    public File resolve() throws IOException
    {
        return jarFile;
    }
    
    @Override
    public File resolvePOM() throws IOException
    {
        JarFile jar = new JarFile(jarFile);
        String pomPath = String.format("META-INF/maven/%s/%s/pom.xml", artifact.groupId, artifact.artifactId);
        ZipEntry e = jar.getEntry(pomPath);
        
        File temporaryFile = File.createTempFile(artifact.artifactId, ".xml");
        temporaryFile.deleteOnExit();
        
        InputStream in = jar.getInputStream(e);
        OutputStream out = new FileOutputStream(temporaryFile);
        
        IOUtils.copy(in, out);
        
        out.close();
        in.close();
        jar.close();
        return temporaryFile;
    }
}
