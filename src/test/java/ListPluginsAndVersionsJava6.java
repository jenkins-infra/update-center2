import io.jenkins.update_center.BaseMavenRepository;
import io.jenkins.update_center.DefaultMavenRepositoryBuilder;
import io.jenkins.update_center.HPI;
import io.jenkins.update_center.MavenRepository;
import io.jenkins.update_center.Plugin;
import io.jenkins.update_center.filters.JavaVersionPluginFilter;
import io.jenkins.update_center.util.JavaSpecificationVersion;
import io.jenkins.update_center.wrappers.FilteringRepository;

import java.util.Collection;

/**
 * Test program that lists all the plugin names and their versions.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListPluginsAndVersionsJava6 {
    public static void main(String[] args) throws Exception{
        BaseMavenRepository r = DefaultMavenRepositoryBuilder.getInstance();
        MavenRepository f = new FilteringRepository().withPluginFilter(new JavaVersionPluginFilter(JavaSpecificationVersion.JAVA_6)).withBaseRepository(r);

        System.out.println(f.getJenkinsWarsByVersionNumber().firstKey());

        Collection<Plugin> all = f.listJenkinsPlugins();
        for (Plugin p : all) {
            HPI hpi = p.getLatest();
            System.out.printf("%s\t%s\n", p.getArtifactId(), hpi.toString());
        }
    }
}
