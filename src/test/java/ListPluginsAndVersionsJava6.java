import org.jvnet.hudson.update_center.BaseMavenRepository;
import org.jvnet.hudson.update_center.DefaultMavenRepositoryBuilder;
import org.jvnet.hudson.update_center.HPI;
import org.jvnet.hudson.update_center.Plugin;
import org.jvnet.hudson.update_center.filters.JavaVersionPluginFilter;
import org.jvnet.hudson.update_center.util.JavaSpecificationVersion;
import org.jvnet.hudson.update_center.wrappers.FilteringRepository;

import java.util.Collection;

/**
 * Test program that lists all the plugin names and their versions.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListPluginsAndVersionsJava6 {
    public static void main(String[] args) throws Exception{
        BaseMavenRepository r = DefaultMavenRepositoryBuilder.getInstance();
        FilteringRepository f = new FilteringRepository(r).withPluginFilter(new JavaVersionPluginFilter(JavaSpecificationVersion.JAVA_6));

        System.out.println(f.getHudsonWar().firstKey());

        Collection<Plugin> all = f.listHudsonPlugins();
        for (Plugin p : all) {
            HPI hpi = p.getLatest();
            System.out.printf("%s\t%s\n", p.getArtifactId(), hpi.toString());
        }
    }
}
