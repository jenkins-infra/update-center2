import org.jvnet.hudson.update_center.HPI;
import org.jvnet.hudson.update_center.MavenRepository;
import org.jvnet.hudson.update_center.MavenRepositoryImpl;
import org.jvnet.hudson.update_center.PluginHistory;

import java.net.URL;
import java.util.Set;
import java.util.TreeSet;

/**
 * List up existing groupIds used by plugins.
 * 
 * @author Kohsuke Kawaguchi
 */
public class ListExisting {
    public static void main(String[] args) throws Exception{
        MavenRepository r = new MavenRepositoryImpl("java.net2",new URL("http://maven.dyndns.org/2/"));
        Set<String> groupIds = new TreeSet<String>();
        for (PluginHistory p : r.listHudsonPlugins()) {
            HPI hpi = p.latest();
            groupIds.add(hpi.artifact.groupId);
        }

        for (String groupId : groupIds) {
            System.out.println(groupId);
        }
    }
}
