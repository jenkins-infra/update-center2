import org.jvnet.hudson.update_center.HPI;
import org.jvnet.hudson.update_center.MavenRepositoryImpl;
import org.jvnet.hudson.update_center.PluginHistory;

import java.net.URL;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * List up existing groupIds used by plugins.
 * 
 * @author Kohsuke Kawaguchi
 */
public class ListExisting {
    public static void main(String[] args) throws Exception{
        MavenRepositoryImpl r = new MavenRepositoryImpl();
        r.addRemoteRepository("java.net2",
                new URL("http://updates.jenkins-ci.org/.index/nexus-maven-repository-index.zip"),
                new URL("http://maven.glassfish.org/content/groups/public/"));
        Set<String> groupIds = new TreeSet<String>();
        Collection<PluginHistory> all = r.listHudsonPlugins();
        for (PluginHistory p : all) {
            HPI hpi = p.latest();
            groupIds.add(hpi.artifact.groupId);
        }

        for (String groupId : groupIds) {
            System.out.println(groupId);
        }
    }
}
