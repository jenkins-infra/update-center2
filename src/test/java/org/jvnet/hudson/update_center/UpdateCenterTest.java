package org.jvnet.hudson.update_center;

import com.sun.tools.javac.resources.version;
import junit.framework.TestCase;
import net.sf.json.JSONObject;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.dom4j.DocumentException;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: magnayn
 * Date: 27/05/2011
 * Time: 13:34
 * To change this template use File | Settings | File Templates.
 */
public class UpdateCenterTest extends TestCase {
    public void testWrite() throws Exception {
//        MavenRepositoryImpl r = new MavenRepositoryImpl();
//        r.addRemoteRepository("java.net2",
//                new URL("http://updates.jenkins-ci.org/.index/nexus-maven-repository-index.zip"),
//                new URL("http://maven.glassfish.org/content/groups/public/"));

        IArtifactProvider provider = new MockArtifactProvider();

        UpdateCenter uc = new UpdateCenter("Test", provider);

        uc.write(System.out);

    }


    public class MockArtifactProvider implements IArtifactProvider
    {
        public Collection<PluginHistory> listHudsonPlugins() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
            List<PluginHistory> ph = new ArrayList<PluginHistory>();

            PluginHistory plugin = new PluginHistory("testPlugin");
            IHPI hpi = new MockHPI();

            plugin.addArtifact(hpi);
            ph.add(plugin);
            return ph;
        }

        public TreeMap<VersionNumber, HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException {
            TreeMap<VersionNumber, HudsonWar> map = new TreeMap<VersionNumber, HudsonWar>();

            return map;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }

    public class MockHPI implements IHPI {

        public Version getVersion() {
            return new Version("1.0");  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isAuthenticJenkinsArtifact() {
            return true;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Version getRequiredJenkinsVersion() throws IOException {
            return new Version("1.139");  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Date getTimestampAsDate() throws IOException {
            return new Date();  //To change body of implemented methods use File | Settings | File Templates.
        }

        public ArtifactInfo getArtifact() throws IOException {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Timestamp getTimestamp() throws IOException {
            return new Timestamp(12345);  //To change body of implemented methods use File | Settings | File Templates.
        }

        public URL getURL() throws MalformedURLException {
            return new URL("http://news.bbc.co.uk/");  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Pom getPom() throws IOException, DocumentException {
            return new Pom( getClass().getResourceAsStream("testpom.xml") );  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Version getCompatibleSinceVersion() throws IOException {
            return new Version("1.139");  //To change body of implemented methods use File | Settings | File Templates.
        }

        public String getDisplayName() throws IOException {
            return "Fish";  //To change body of implemented methods use File | Settings | File Templates.
        }

        public String getSandboxStatus() throws IOException {
            return "What";  //To change body of implemented methods use File | Settings | File Templates.
        }

        public List<Dependency> getDependencies() throws IOException {
            return new ArrayList<Dependency>();  //To change body of implemented methods use File | Settings | File Templates.
        }

        public List<Developer> getDevelopers() throws IOException {
            return new ArrayList<Developer>();  //To change body of implemented methods use File | Settings | File Templates.
        }

        public JSONObject toJSON(String name) throws IOException {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("version", getVersion().toString());

            o.put("url", getURL().toExternalForm());
            o.put("buildDate", getTimestamp().getTimestampAsString());

            return o;
        }

        public String getBuiltBy() throws IOException {
            return "Fred";  //To change body of implemented methods use File | Settings | File Templates.
        }
    }
}
