import io.jenkins.update_center.DefaultMavenRepositoryBuilder;
import io.jenkins.update_center.HPI;
import io.jenkins.update_center.MavenRepository;
import io.jenkins.update_center.Plugin;

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
        Collection<Plugin> all = r.listJenkinsPlugins();
        for (Plugin p : all) {
            HPI hpi = p.getLatest();
            groupIds.add(hpi.artifact.groupId);
        }

        for (String groupId : groupIds) {
            System.out.println(groupId);
        }
    }
}
