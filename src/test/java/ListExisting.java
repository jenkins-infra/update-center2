import org.jvnet.hudson.update_center.*;

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
        MavenRepository r = DefaultMavenRepositoryBuilder.getInstance();

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
