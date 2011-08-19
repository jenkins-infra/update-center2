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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.transform.ArtifactTransformationManager;
import org.apache.tools.ant.taskdefs.Expand;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.FlatSearchRequest;
import org.sonatype.nexus.index.FlatSearchResponse;
import org.sonatype.nexus.index.NexusIndexer;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Maven repository and its nexus index.
 *
 * Using Maven embedder 2.0.4 results in problem caused by Plexus incompatibility.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenRepositoryImpl extends MavenRepository {
    protected NexusIndexer indexer;
    protected ArtifactFactory af;
    protected ArtifactResolver ar;
    protected List<ArtifactRepository> remoteRepositories = new ArrayList<ArtifactRepository>();
    protected ArtifactRepository local;
    protected ArtifactRepositoryFactory arf;
    private PlexusContainer plexus;
    private Integer maxPlugins;

    public MavenRepositoryImpl() throws Exception {
        ClassWorld classWorld = new ClassWorld( "plexus.core", MavenRepositoryImpl.class.getClassLoader() );
        ContainerConfiguration configuration = new DefaultContainerConfiguration().setClassWorld( classWorld );
        plexus = new DefaultPlexusContainer( configuration );
        ComponentDescriptor<ArtifactTransformationManager> componentDescriptor = plexus.getComponentDescriptor(ArtifactTransformationManager.class,
            ArtifactTransformationManager.class.getName(), "default");
        if (componentDescriptor == null) {
            throw new IllegalArgumentException("Unable to find maven default ArtifactTransformationManager component. You might get this if you run the program from within the exec:java mojo.");
        }
        componentDescriptor.setImplementationClass(DefaultArtifactTransformationManager.class);

        indexer = plexus.lookup( NexusIndexer.class );

        af = plexus.lookup(ArtifactFactory.class);
        ar = plexus.lookup(ArtifactResolver.class);
        arf = plexus.lookup(ArtifactRepositoryFactory.class);

        local = arf.createArtifactRepository("local",
                new File(new File(System.getProperty("user.home")), ".m2/repository").toURI().toURL().toExternalForm(),
                new DefaultRepositoryLayout(), POLICY, POLICY);
    }

    public void setMaxPlugins(Integer maxPlugins) {
        this.maxPlugins = maxPlugins;
    }

    /**
     * Plexus container that's hosting the Maven components.
     */
    public PlexusContainer getPlexus() {
        return plexus;
    }

    /**
     * @param id
     *      Repository ID. This ID has to match the ID in the repository index, due to a design bug in Maven.
     * @param indexDirectory
     *      Directory that contains exploded index zip file.
     * @param repository
     *      URL of the Maven repository. Used to resolve artifacts.
     */
    public void addRemoteRepository(String id, File indexDirectory, URL repository) throws IOException, UnsupportedExistingLuceneIndexException {
        indexer.addIndexingContext(id, id,null, indexDirectory,null,null, NexusIndexer.DEFAULT_INDEX);
        remoteRepositories.add(
                arf.createArtifactRepository(id, repository.toExternalForm(),
                        new DefaultRepositoryLayout(), POLICY, POLICY));
    }

    public void addRemoteRepository(String id, URL repository) throws IOException, UnsupportedExistingLuceneIndexException {
        addRemoteRepository(id,new URL(repository,".index/nexus-maven-repository-index.zip"), repository);
    }

    public void addRemoteRepository(String id, URL remoteIndex, URL repository) throws IOException, UnsupportedExistingLuceneIndexException {
        addRemoteRepository(id,load(id,remoteIndex), repository);
    }

    private static File load(String id, URL url) throws IOException {
        File dir = new File(new File(System.getProperty("java.io.tmpdir")), "maven-index/" + id);
        File zip = new File(dir,"index.zip");
        File expanded = new File(dir,"expanded");

        URLConnection con = url.openConnection();
        if (url.getUserInfo()!=null) {
            con.setRequestProperty("Authorization","Basic "+new sun.misc.BASE64Encoder().encode(url.getUserInfo().getBytes()));
        }

        if (!expanded.exists() || !zip.exists() || zip.lastModified()!=con.getLastModified()) {
            System.out.println("Downloading "+url);
            // if the download fail in the middle, only leave a broken tmp file
            dir.mkdirs();
            File tmp = new File(dir,"index.zi_");
            FileOutputStream o = new FileOutputStream(tmp);
            IOUtils.copy(con.getInputStream(), o);
            o.close();

            if (expanded.exists())
                FileUtils.deleteDirectory(expanded);
            expanded.mkdirs();

            Expand e = new Expand();
            e.setSrc(tmp);
            e.setDest(expanded);
            e.execute();

            // as a proof that the expansion was properly completed
            tmp.renameTo(zip);
            zip.setLastModified(con.getLastModified());
        } else {
            System.out.println("Reusing the locally cached "+url+" at "+zip);
        }

        return expanded;
    }

    protected File resolve(ArtifactInfo a, String type, String classifier) throws AbstractArtifactResolutionException {
        Artifact artifact = af.createArtifactWithClassifier(a.groupId, a.artifactId, a.version, type, classifier);
        ar.resolve(artifact, remoteRepositories, local);
        return artifact.getFile();
    }

    public Collection<PluginHistory> listHudsonPlugins() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
        BooleanQuery q = new BooleanQuery();
        q.add(indexer.constructQuery(ArtifactInfo.PACKAGING,"hpi"), Occur.MUST);

        FlatSearchRequest request = new FlatSearchRequest(q);
        FlatSearchResponse response = indexer.searchFlat(request);

        Map<String, PluginHistory> plugins =
            new TreeMap<String, PluginHistory>(String.CASE_INSENSITIVE_ORDER);

        for (ArtifactInfo a : response.getResults()) {
            if (a.version.contains("SNAPSHOT"))     continue;       // ignore snapshots
            if (IGNORE.containsKey(a.artifactId) || IGNORE.containsKey(a.artifactId + "-" + a.version))
                continue;       // artifactIds or particular versions to omit

            PluginHistory p = plugins.get(a.artifactId);
            if (p==null)
                plugins.put(a.artifactId, p=new PluginHistory(a.artifactId));
            p.addArtifact(createHpiArtifact(a, p));
            p.groupId.add(a.groupId);
        }
        return reduceToMaxPluginsIfSpecified(plugins.values());
    }

    private Collection<PluginHistory> reduceToMaxPluginsIfSpecified(Collection<PluginHistory> values) {
        if (maxPlugins == null) {
            return values;
        }
        System.out.println("Limiting the number of plugins handled to " + maxPlugins);
        List<PluginHistory> result = new ArrayList<PluginHistory>(values);
        return result.subList(0, maxPlugins);
    }

    public TreeMap<VersionNumber,HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException {
        TreeMap<VersionNumber,HudsonWar> r = new TreeMap<VersionNumber, HudsonWar>(VersionNumber.DESCENDING);
        listWar(r, "org.jenkins-ci.main", null);
        listWar(r, "org.jvnet.hudson.main", CUT_OFF);
        return r;
    }

    private void listWar(TreeMap<VersionNumber, HudsonWar> r, String groupId, VersionNumber cap) throws IOException {
        BooleanQuery q = new BooleanQuery();
        q.add(indexer.constructQuery(ArtifactInfo.GROUP_ID,groupId), Occur.MUST);
        q.add(indexer.constructQuery(ArtifactInfo.PACKAGING,"war"), Occur.MUST);

        FlatSearchRequest request = new FlatSearchRequest(q);
        FlatSearchResponse response = indexer.searchFlat(request);

        for (ArtifactInfo a : response.getResults()) {
            if (a.version.contains("SNAPSHOT"))     continue;       // ignore snapshots
            if (!a.artifactId.equals("jenkins-war")
             && !a.artifactId.equals("hudson-war"))  continue;      // somehow using this as a query results in 0 hits.
            if (a.classifier!=null)  continue;          // just pick up the main war
            if (cap!=null && new VersionNumber(a.version).compareTo(cap)>0) continue;

            VersionNumber v = new VersionNumber(a.version);
            r.put(v, createHudsonWarArtifact(a));
        }
    }

/*
    Hook for subtypes to use customized implementations.
 */

    protected HPI createHpiArtifact(ArtifactInfo a, PluginHistory p) throws AbstractArtifactResolutionException {
        return new HPI(this,p,a);
    }

    protected HudsonWar createHudsonWarArtifact(ArtifactInfo a) {
        return new HudsonWar(this,a);
    }

    private static final Properties IGNORE = new Properties();

    static {
        try {
            IGNORE.load(Plugin.class.getClassLoader().getResourceAsStream("artifact-ignores.properties"));
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    protected static final ArtifactRepositoryPolicy POLICY = new ArtifactRepositoryPolicy(true, "daily", "warn");

    /**
     * Hudson -> Jenkins cut-over version.
     */
    public static final VersionNumber CUT_OFF = new VersionNumber("1.395");
}
