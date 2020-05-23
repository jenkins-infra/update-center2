import io.jenkins.update_center.DefaultMavenRepositoryBuilder;
import io.jenkins.update_center.HPI;
import io.jenkins.update_center.MavenRepository;
import io.jenkins.update_center.Plugin;

import java.util.Collection;

/**
 * Test program that lists all the plugin names and their versions.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListPluginsAndVersions {
    public static void main(String[] args) throws Exception{
        MavenRepository r = DefaultMavenRepositoryBuilder.getInstance();

        System.out.println(r.getJenkinsWarsByVersionNumber().firstKey());

        Collection<Plugin> all = r.listJenkinsPlugins();
        for (Plugin p : all) {
            HPI hpi = p.getLatest();
            System.out.printf("%s\t%s\n", p.getArtifactId(), hpi.toString());
        }
    }
}
