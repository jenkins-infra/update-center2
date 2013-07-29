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

import hudson.util.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.tools.ant.DirectoryScanner;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.nexus.index.ArtifactAvailablility;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;

/**
 * Use a local repository containing HPI files as a plugin repository.
 * It must not be a maven repository.
 * This class acts as a fake maven repository.
 */
public class LocalDirectoryRepository extends MavenRepository
{
    private File dir;
    private URL baseUrl;
    private final boolean includeSnapshots;
    private final File downloadDir;
    
    /**
     * @param dir a directory containing HPI files.
     * @param includeSnapshots 
     */
    public LocalDirectoryRepository(File dir, URL baseUrl, boolean includeSnapshots, File downloadDir)
    {
        this.dir = dir;
        this.baseUrl = baseUrl;
        this.includeSnapshots = includeSnapshots;
        this.downloadDir = downloadDir;
    }
    
    /**
     * Return all plugins contained in the directory.
     * 
     * @return a collection of histories of plugins contained in the directory.
     * @see org.jvnet.hudson.update_center.MavenRepository#listHudsonPlugins()
     */
    @Override
    public Collection<PluginHistory> listHudsonPlugins()
            throws PlexusContainerException, ComponentLookupException,
            IOException, UnsupportedExistingLuceneIndexException,
            AbstractArtifactResolutionException
    {
        // Search all files *.hpi contained in the directory.
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(dir);
        ds.setIncludes(new String[]{"**/*.hpi"});
        ds.scan();
        
        // build plugin history.
        Map<String, PluginHistory> plugins =
                new TreeMap<String, PluginHistory>(String.CASE_INSENSITIVE_ORDER);
        
        for(String filename: ds.getIncludedFiles())
        {
            File hpiFile = new File(dir, filename);
            JarFile jar = new JarFile(hpiFile);
            Manifest manifest = jar.getManifest();
            long lastModified = jar.getEntry("META-INF/MANIFEST.MF").getTime();
            jar.close();

            String groupId = manifest.getMainAttributes().getValue("Group-Id");
            if (groupId == null) {
                // Old plugins inheriting from org.jvnet.hudson.plugins do not have
                // Group-Id field set in manifest. Absence of group id causes NPE in ArtifactInfo.
                groupId = "org.jvnet.hudson.plugins";
            }
            
            ArtifactInfo a = new ArtifactInfo(
                    null,  // fname
                    "hpi",  // fextension
                    groupId,
                    manifest.getMainAttributes().getValue("Extension-Name"),    // artifactId
                        // maybe Short-Name or Implementation-Title is more proper.
                    manifest.getMainAttributes().getValue("Plugin-Version"),    // version
                        // maybe Implementation-Version is more proper.
                    null,  // classifier
                    "hpi",  // packaging
                    manifest.getMainAttributes().getValue("Long-Name"),    // name
                    manifest.getMainAttributes().getValue("Specification-Title"),    // description
                    lastModified,   // lastModified
                    hpiFile.length(),   // size
                    null,   // md5
                    null,   // sha1
                    ArtifactAvailablility.NOT_PRESENT, // sourcesExists
                    ArtifactAvailablility.NOT_PRESENT, //javadocExists,
                    ArtifactAvailablility.NOT_PRESENT, //signatureExists,
                    null    // repository
            );
            
            if (!includeSnapshots && a.version.contains("SNAPSHOT"))     continue;       // ignore snapshots
            PluginHistory p = plugins.get(a.artifactId);
            if (p==null)
                plugins.put(a.artifactId, p=new PluginHistory(a.artifactId));
            
            URL url;
            if (downloadDir == null) {
                // No downloadDir specified.
                // Just link to packages where they are located.
                String path = filename;
                if(File.separatorChar != '/')
                {
                    // fix path separate character to /
                    path = filename.replace(File.separatorChar, '/');
                }
                url = new URL(baseUrl, path);
            } else {
                // downloadDir is specified.
                // Packages are deployed into downloadDir, based on its plugin name and version.
                final String path = new LocalHPI(this, p, a, hpiFile, null).getRelativePath();
                url = new URL(new URL(baseUrl, "download/"), path);
            }
            p.addArtifact(new LocalHPI(this, p, a, hpiFile, url));
            p.groupId.add(a.groupId);
        }
        
        return plugins.values();
    }
    
    /**
     * Returns empty list.
     * 
     * @return empty tree map.
     * @see org.jvnet.hudson.update_center.MavenRepository#getHudsonWar()
     */
    @Override
    public TreeMap<VersionNumber, HudsonWar> getHudsonWar() throws IOException,
            AbstractArtifactResolutionException
    {
        TreeMap<VersionNumber,HudsonWar> r = new TreeMap<VersionNumber, HudsonWar>(VersionNumber.DESCENDING);
        return r;
    }
    
    /**
     * Return a file specified by artifact information.
     * 
     * @see org.jvnet.hudson.update_center.MavenRepository#resolve(org.sonatype.nexus.index.ArtifactInfo, java.lang.String, java.lang.String)
     */
    @Override
    protected File resolve(ArtifactInfo a, String type, String classifier)
            throws AbstractArtifactResolutionException
    {
        // TODO Auto-generated method stub
        return null;
    }
    
}
