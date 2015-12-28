import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.jvnet.hudson.update_center.DefaultMavenRepositoryBuilder;
import org.jvnet.hudson.update_center.HPI;
import org.jvnet.hudson.update_center.Main;
import org.jvnet.hudson.update_center.MavenRepositoryImpl;
import org.jvnet.hudson.update_center.PluginHistory;

/**
 * List up existing groupIds used by plugins.
 *
 * @author Kohsuke Kawaguchi
 */
public class LocalTesting {
    public static void main(String[] args) throws Exception {

        // -hpiDirectory=/Users/jrichard/.m2

        // File m2Repo = new File("/Users/jrichard/.m2");
        // File downloadDir = new File("./testDownloadDir/downloadHere");
        // LocalDirectoryRepository localRepo = new LocalDirectoryRepository(m2Repo, new
        // URL("file:///Users/jrichard/.m2"), true, downloadDir);
        //
        // Set<String> gavsLocal = new TreeSet<String>();
        // Collection<PluginHistory> allLocal = localRepo.listHudsonPlugins();
        // for (PluginHistory p : allLocal) {
        // HPI hpi = p.latest();
        // gavsLocal.add(hpi.artifact.artifactId + ":" + hpi.artifact.artifactId + ":" + hpi.artifact.version);
        // }
        //
        // for (String gav : gavsLocal) {
        // System.out.println(gav);
        // }

        String repoUrl = "http://artifactory.blackducksoftware.com:8081/artifactory/bds-jenkins-plugins-snapshot/";

        MavenRepositoryImpl r = DefaultMavenRepositoryBuilder.createStandardInstance("bds-jenkins-plugins-snapshot",
                repoUrl, null, true,
                true);

        Set<String> gavs = new TreeSet<String>();
        Collection<PluginHistory> all = r.listHudsonPlugins();
        for (PluginHistory p : all) {
            HPI hpi = p.latest();
            gavs.add(hpi.artifact.artifactId + ":" + hpi.artifact.artifactId + ":" + hpi.artifact.version);
        }

        for (String gav : gavs) {
            System.out.println(gav);
        }
        List<String> customArgs = new ArrayList<String>();
        customArgs.add("-id");
        customArgs.add("BlackDuckInternalJenkinsUpdateSnapshot");

        customArgs.add("-www");
        customArgs.add("./update-center.json");

        customArgs.add("-repositoryName");
        customArgs.add("bds-jenkins-plugins-snapshot");

        customArgs.add("-repository");
        customArgs.add("http://artifactory.blackducksoftware.com:8081/artifactory/bds-jenkins-plugins-snapshot/");

        customArgs.add("-connectionCheckUrl");
        customArgs.add("http://google.com");

        customArgs.add("-customWikiBaseUrl");
        customArgs.add("http://localhost:8090/");
        customArgs.add("-customWikiSpaceName");
        customArgs.add("JUS");
        customArgs.add("-customWikiPageTitle");
        customArgs.add("Jenkins Update Site Home");
        customArgs.add("-confluenceVersion");
        customArgs.add("2");

        customArgs.add("-wikiUser");
        customArgs.add("admin");
        customArgs.add("-wikiPassword");
        customArgs.add("admin");

        // customArgs.add("-nowiki");
        customArgs.add("-pretty");
        customArgs.add("-download");
        customArgs.add("./CHECKTHISFOLDER/download");
        customArgs.add("-includeSnapshots");
        customArgs.add("-directLink");

        Main.main(customArgs.toArray(new String[0]));

    }
}
