import hudson.util.VersionNumber;
import org.jvnet.hudson.update_center.DefaultMavenRepositoryBuilder;
import org.jvnet.hudson.update_center.HPI;
import org.jvnet.hudson.update_center.MavenRepositoryImpl;
import org.jvnet.hudson.update_center.PluginHistory;
import org.jvnet.hudson.update_center.impl.pluginFilter.JavaVersionPluginFilter;
import org.jvnet.hudson.update_center.util.JavaSpecificationVersion;

import java.util.Collection;

/**
 * Test program that lists all the plugin names and their versions.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListPluginsAndVersionsJava6 {
    public static void main(String[] args) throws Exception{
        MavenRepositoryImpl r = DefaultMavenRepositoryBuilder.getInstance();
        r.addPluginFilter(new JavaVersionPluginFilter(JavaSpecificationVersion.JAVA_6));

        System.out.println(r.getHudsonWar().firstKey());

        Collection<PluginHistory> all = r.listHudsonPlugins();
        for (PluginHistory p : all) {
            HPI hpi = p.latest();
            System.out.printf("%s\t%s\n", p.artifactId, hpi.toString());
        }
    }
}
