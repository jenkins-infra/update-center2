package org.jvnet.hudson.update_center;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import javax.lang.model.element.Name;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Command-line tool to list up extension points and their implementations into a JSON file.
 *
 * @author Kohsuke Kawaguchi
 */
public class ExtensionPointListGenerator {
    private final Map<String,Family> families = new HashMap<String,Family>();

    public class Family {
        Extension definition;
        final List<Extension> implementations = new ArrayList<Extension>();

        public String getName() {
            return definition.extensionPoint.getQualifiedName().toString();
        }
    }

    public static void main(String[] args) throws Exception {
        new ExtensionPointListGenerator().run();
    }

    public void run() throws Exception {
        MavenRepositoryImpl r = new MavenRepositoryImpl();
        r.addRemoteRepository("java.net2",
                new File("updates.jenkins-ci.org"),
                new URL("http://maven.glassfish.org/content/groups/public/"));

        final ConfluencePluginList cpl = new ConfluencePluginList();

        // this object captures information about modules where extensions are defined/found.
        final JSONObject artifacts = new JSONObject();

        HudsonWar war = r.getHudsonWar().firstEntry().getValue();
        discover(war.getCoreArtifact());
        artifacts.put(war.getCoreArtifact().getGavId(), toJSON(war, cpl));

        ExecutorService svc = Executors.newFixedThreadPool(4);
        Set<Future> futures = new HashSet<Future>();
        for (final PluginHistory p : new ArrayList<PluginHistory>(r.listHudsonPlugins()).subList(0,5)) {
            futures.add(svc.submit(new Runnable() {
                public void run() {
                    try {
                        System.out.println(p.artifactId);
                        discover(p.latest());
                        synchronized (artifacts) {
                            artifacts.put(p.latest().getGavId(), toJSON(p.latest(), cpl));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        // skip to the next plugin
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }));
        }
        for (Future f : futures) {
            f.get();
        }
        svc.shutdown();

        JSONObject all = new JSONObject();
        for (Family f : families.values()) {
            if (f.definition==null)     continue;   // skip undefined extension points
            JSONObject o = f.definition.toJSON();

            JSONArray use = new JSONArray();
            for (Extension impl : f.implementations)
                use.add(impl.toJSON());
            o.put("implementations", use);

            all.put(f.getName(),o);
        }

        JSONObject container = new JSONObject();
        container.put("extensionPoints",all);
        container.put("artifacts",artifacts);

        FileUtils.writeStringToFile(new File("extension-points.json"), container.toString(2));
    }

    private void discover(MavenArtifact a) throws IOException, InterruptedException {
        for (Extension e : new ExtensionPointsExtractor(a).extract()) {
            synchronized (families) {
                System.out.printf("Found %s as %s\n",
                        e.implementation.getQualifiedName(),
                        e.extensionPoint.getQualifiedName());

                String key = e.extensionPoint.getQualifiedName().toString();
                Family f = families.get(key);
                if (f==null)    families.put(key,f=new Family());

                if (e.isDefinition()) {
                    assert f.definition==null;
                    f.definition = e;
                } else {
                    f.implementations.add(e);
                }
            }
        }
    }


    /**
     * Builds information about an artifact into JSON.
     */
    private JSONObject toJSON(MavenArtifact a) throws IOException, InterruptedException {
        JSONObject o = new JSONObject();
        o.put("gav",a.getGavId());
        return o;
    }

    private JSONObject toJSON(HPI hpi, ConfluencePluginList cpl) throws IOException, InterruptedException {
        JSONObject o = toJSON(hpi);
        Plugin p = new Plugin(hpi,cpl);
        o.put("url",p.getWiki());
        return o;
    }

    private JSONObject toJSON(HudsonWar war, ConfluencePluginList cpl) throws IOException, InterruptedException {
        JSONObject o = toJSON(war.getCoreArtifact());
        o.put("url","https://github.com/jenkinsci/jenkins");
        return o;
    }
}
