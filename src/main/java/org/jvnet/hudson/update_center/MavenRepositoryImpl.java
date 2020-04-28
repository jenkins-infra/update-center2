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

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.transform.ArtifactTransformationManager;
import org.apache.tools.ant.taskdefs.Expand;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.FlatSearchRequest;
import org.sonatype.nexus.index.FlatSearchResponse;
import org.sonatype.nexus.index.NexusIndexer;
import org.sonatype.nexus.index.context.DefaultIndexingContext;
import org.sonatype.nexus.index.context.IndexUtils;
import org.sonatype.nexus.index.context.NexusAnalyzer;
import org.sonatype.nexus.index.context.NexusIndexWriter;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;
import org.sonatype.nexus.index.updater.IndexDataReader;
import org.sonatype.nexus.index.updater.IndexDataReader.IndexDataReadResult;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Maven repository and its nexus index.
 *
 * Using Maven embedder 2.0.4 results in problem caused by Plexus incompatibility.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenRepositoryImpl extends BaseMavenRepository {
    protected NexusIndexer indexer;
    protected ArtifactFactory af;
    protected ArtifactResolver ar;
    protected List<ArtifactRepository> remoteRepositories = new ArrayList<ArtifactRepository>();
    private final File localRepo;
    protected ArtifactRepository local;
    protected ArtifactRepositoryFactory arf;
    private PlexusContainer plexus;

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

        localRepo = new File(new File(System.getProperty("user.home")), ".m2/repository");
        local = arf.createArtifactRepository("local",
                localRepo.toURI().toURL().toExternalForm(),
                new DefaultRepositoryLayout(), POLICY, POLICY);
    }

    /**
     * @param id
     *      Repository ID. This ID has to match the ID in the repository index, due to a design bug in Maven.
     * @param indexDirectory
     *      Directory that contains exploded index zip file.
     * @param repository
     *      URL of the Maven repository. Used to resolve artifacts.
     */
    public void addRemoteRepository(String id, File indexDirectory, URL repository) throws IOException {
        try {
            indexer.addIndexingContext(id, id, null, indexDirectory, null, null, NexusIndexer.DEFAULT_INDEX);
            remoteRepositories.add(
                    arf.createArtifactRepository(id, repository.toExternalForm(),
                            new DefaultRepositoryLayout(), POLICY, POLICY));
        } catch (UnsupportedExistingLuceneIndexException ex) {
            throw new IOException(ex);
        }
    }

    public void addRemoteRepository(String id, URL repository) throws IOException {
        addRemoteRepository(id,new URL(repository,".index/nexus-maven-repository-index.gz"), repository);
    }

    public void addRemoteRepository(String id, URL remoteIndex, URL repository) throws IOException {
        addRemoteRepository(id,loadIndex(id,remoteIndex), repository);
    }

    /**
     * Loads a remote repository index (.zip or .gz), convert it to Lucene index and return it.
     */
    private File loadIndex(String id, URL url) throws IOException {
        File dir = new File(new File(System.getProperty("java.io.tmpdir")), "maven-index/" + id);
        File local = new File(dir,"index"+getExtension(url));
        File expanded = new File(dir,"expanded");

        URLConnection con = url.openConnection();
        if (url.getUserInfo()!=null) {
            con.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(url.getUserInfo().getBytes("UTF-8")));
        }

        if (!expanded.exists() || !local.exists() || (local.lastModified() < con.getLastModified())) {
            System.out.println("Downloading "+url);
            // if the download fail in the middle, only leave a broken tmp file
            dir.mkdirs();
            File tmp = new File(dir,"index_"+getExtension(url));
            FileOutputStream o = new FileOutputStream(tmp);
            IOUtils.copy(con.getInputStream(), o);
            o.close();

            if (expanded.exists())
                FileUtils.deleteDirectory(expanded);
            expanded.mkdirs();

            if (url.toExternalForm().endsWith(".gz")) {
                System.out.println("Reconstructing index from "+url);
                FSDirectory directory = FSDirectory.getDirectory(expanded);
                NexusIndexWriter w = new NexusIndexWriter(directory, new NexusAnalyzer(), true);
                FileInputStream in = new FileInputStream(tmp);
                try {
                    IndexDataReader dr = new IndexDataReader(in);
                    IndexDataReadResult result = dr.readIndex(w,
                            new DefaultIndexingContext(id,id,null,expanded,null,null,NexusIndexer.DEFAULT_INDEX,true));
                } catch (UnsupportedExistingLuceneIndexException ex) {
                    throw new IOException(ex);
                } finally {
                    IndexUtils.close(w);
                    IOUtils.closeQuietly(in);
                    directory.close();
                }
            } else
            if (url.toExternalForm().endsWith(".zip")) {
                Expand e = new Expand();
                e.setSrc(tmp);
                e.setDest(expanded);
                e.execute();
            } else {
                throw new UnsupportedOperationException("Unsupported index format: "+url);
            }

            // as a proof that the expansion was properly completed
            tmp.renameTo(local);
            local.setLastModified(con.getLastModified());
        } else {
            System.out.println("Reusing the locally cached "+url+" at "+local);
        }

        return expanded;
    }

    private static String getExtension(URL url) {
        String s = url.toExternalForm();
        int idx = s.lastIndexOf('.');
        if (idx<0)  return "";
        else        return s.substring(idx);
    }

    public Collection<ArtifactCoordinates> listAllPlugins() throws IOException {
        BooleanQuery q = new BooleanQuery();
        q.setMinimumNumberShouldMatch(1);
        q.add(indexer.constructQuery(ArtifactInfo.PACKAGING,"hpi"), Occur.SHOULD);
        q.add(indexer.constructQuery(ArtifactInfo.PACKAGING,"jpi"), Occur.SHOULD);

        FlatSearchRequest request = new FlatSearchRequest(q);
        FlatSearchResponse response = indexer.searchFlat(request);

        return response.getResults().stream().map(a -> new ArtifactCoordinates(a.groupId, a.artifactId, a.version, a.packaging, a.classifier, a.lastModified)).collect(Collectors.toSet());
    }

    protected Set<ArtifactCoordinates> listAllJenkinsWars(String groupId) throws IOException {
        BooleanQuery q = new BooleanQuery();
        q.add(indexer.constructQuery(ArtifactInfo.GROUP_ID,groupId), Occur.MUST);
        q.add(indexer.constructQuery(ArtifactInfo.PACKAGING,"war"), Occur.MUST);

        FlatSearchRequest request = new FlatSearchRequest(q);
        FlatSearchResponse response = indexer.searchFlat(request);

        return response.getResults().stream().map(a -> new ArtifactCoordinates(a.groupId, a.artifactId, a.version, a.packaging, a.classifier, a.lastModified)).collect(Collectors.toSet());
    }

    @Override
    public Digests getDigests(MavenArtifact artifact) throws IOException {
        try (FileInputStream fin = new FileInputStream(artifact.resolve())) {
            MessageDigest sha1 = DigestUtils.getSha1Digest();
            MessageDigest sha256 = DigestUtils.getSha256Digest();
            byte[] buf = new byte[2048];
            int len;
            while ((len=fin.read(buf,0,buf.length)) >= 0) {
                sha1.update(buf, 0, len);
                sha256.update(buf, 0, len);
            }

            Digests ret = new Digests();
            ret.sha1 = new String(org.apache.commons.codec.binary.Base64.encodeBase64(sha1.digest()), "UTF-8");
            ret.sha256 = new String(org.apache.commons.codec.binary.Base64.encodeBase64(sha256.digest()), "UTF-8");
            return ret;
        }
    }

    @Override
    public Manifest getManifest(MavenArtifact artifact) throws IOException {
        try (InputStream is = getZipFileEntry(artifact, "META-INF/MANIFEST.MF")) {
            return new Manifest(is);
        } catch (IOException x) {
            throw new IOException("Failed to read manifest from "+artifact, x);
        }
    }

    @Override
    public InputStream getZipFileEntry(MavenArtifact artifact, String path) throws IOException {
        File f = artifact.resolve();
        try (FileInputStream fis = new FileInputStream(f);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream stream = new ZipInputStream(bis)) {

            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                if (path.equals(entry.getName())) {
                    // pull out the file from entire zip, copy it into memory, and throw away the rest
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();

                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = stream.read(buffer)) > -1 ) {
                        baos.write(buffer, 0, len);
                    }
                    baos.flush();

                    return new ByteArrayInputStream(baos.toByteArray());
                }
            }
            throw new IOException("No entry " + path + " found in " + artifact);
        } catch (IOException x) {
            throw new IOException("Failed read from " + f + ": " + x.getMessage(), x);
        }
    }

    @Override
    public File resolve(ArtifactCoordinates a) throws IOException {
        Artifact artifact = af.createArtifactWithClassifier(a.groupId, a.artifactId, a.version, a.packaging, a.classifier);
        if (!new File(localRepo, local.pathOf(artifact)).isFile()) {
            System.err.println("Downloading " + artifact);
        }
        try {
            ar.resolve(artifact, remoteRepositories, local);
        } catch (RuntimeException | ArtifactResolutionException | ArtifactNotFoundException e) {
            throw new IOException(e.getMessage(), e);
        }
        return artifact.getFile();
    }
}
