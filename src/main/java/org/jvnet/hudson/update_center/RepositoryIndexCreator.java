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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.lucene.document.Document;
import org.sonatype.nexus.artifact.Gav;
import org.sonatype.nexus.artifact.GavCalculator;
import org.sonatype.nexus.artifact.M2GavCalculator;
import org.sonatype.nexus.index.ArtifactContext;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.context.IndexCreator;
import org.sonatype.nexus.index.creator.AbstractIndexCreator;

/**
 * Set the remoteUrl of artifact.
 *
 */
public class RepositoryIndexCreator extends AbstractIndexCreator implements IndexCreator
{
    private static GavCalculator GAV_CALCURATOR = new M2GavCalculator();

    private URL repositoryUrl;

    public RepositoryIndexCreator(URL repositoryUrl)
    {
        this.repositoryUrl = repositoryUrl;
    }

    /**
     * @param artifactContext
     * @throws IOException
     * @see org.sonatype.nexus.index.context.IndexCreator#populateArtifactInfo(org.sonatype.nexus.index.ArtifactContext)
     */
    public void populateArtifactInfo(ArtifactContext artifactContext)
            throws IOException
    {
        // TODO Auto-generated method stub

    }

    /**
     * @param artifactInfo
     * @param document
     * @see org.sonatype.nexus.index.context.IndexCreator#updateDocument(org.sonatype.nexus.index.ArtifactInfo,
     *      org.apache.lucene.document.Document)
     */
    public void updateDocument(ArtifactInfo artifactInfo, Document document)
    {
        // TODO Auto-generated method stub

    }

    /**
     * @param document
     * @param artifactInfo
     * @return
     * @see org.sonatype.nexus.index.context.IndexCreator#updateArtifactInfo(org.apache.lucene.document.Document,
     *      org.sonatype.nexus.index.ArtifactInfo)
     */
    public boolean updateArtifactInfo(Document document,
            ArtifactInfo artifactInfo)
    {
        Gav gav = artifactInfo.calculateGav();
        String path = GAV_CALCURATOR.gavToPath(gav);
        if (path.startsWith("/"))
        {
            path = path.substring(1);
        }

        try
        {
            artifactInfo.remoteUrl = new URL(repositoryUrl, path).toString();
        } catch (MalformedURLException e)
        {
            return false;
        }

        return true;
    }

}
