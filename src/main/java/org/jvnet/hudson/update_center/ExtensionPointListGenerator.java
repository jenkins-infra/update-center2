package org.jvnet.hudson.update_center;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import javax.lang.model.element.Name;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExtensionPointListGenerator {
    private final Map<Name,Family> families = new HashMap<Name,Family>();

    public class Family {
        ExtensionImpl definition;
        final List<ExtensionImpl> implementations = new ArrayList<ExtensionImpl>();

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

        HudsonWar war = r.getHudsonWar().firstEntry().getValue();

        discover(war.getCoreArtifact());

        ExecutorService svc = Executors.newFixedThreadPool(4);
        for (final PluginHistory p : new ArrayList<PluginHistory>(r.listHudsonPlugins()).subList(0,5)) {
            svc.submit(new Runnable() {
                public void run() {
                    try {
                        System.out.println(p.artifactId);
                        discover(p.latest());
                    } catch (IOException e) {
                        e.printStackTrace();
                        // skip to the next plugin
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        svc.shutdown();
        svc.awaitTermination(999, TimeUnit.DAYS);

        JSONObject all = new JSONObject();
        for (Family f : families.values()) {
            if (f.definition==null)     continue;   // skip undefined extension points
            JSONObject o = new JSONObject();

            o.put("javadoc",f.definition.getJavadoc());

            JSONArray use = new JSONArray();
            for (ExtensionImpl impl : f.implementations) {
                JSONObject i = new JSONObject();
                i.put("className",impl.implementation.getQualifiedName().toString());
                i.put("plugin",impl.artifact.artifact.artifactId);
                i.put("javadoc",impl.getJavadoc());
                use.add(i);
            }
            o.put("implementations",use);

            all.put(f.getName(),o);
        }

        FileUtils.writeStringToFile(new File("extension-points.json"), all.toString(2));
    }

    private void discover(MavenArtifact a) throws IOException, InterruptedException {
        for (ExtensionImpl e : new ExtensionpointsExtractor(a).extract()) {
            synchronized (families) {
                System.out.printf("Found %s as %s\n",
                        e.implementation.getQualifiedName(),
                        e.extensionPoint.getQualifiedName());

                Name key = e.extensionPoint.getQualifiedName();
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
     * Builds the extension point information into JSON object.
     */
    private JSONObject toJSON(MavenArtifact a) throws IOException, InterruptedException {
        JSONArray impl = new JSONArray();
        JSONArray def = new JSONArray();

        for (ExtensionImpl e : new ExtensionpointsExtractor(a).extract()) {
            System.out.printf("Found %s as %s\n",
                    e.implementation.getQualifiedName(),
                    e.extensionPoint.getQualifiedName());

            JSONObject o = new JSONObject();
            o.put("extensionPoint",e.extensionPoint.getQualifiedName());
            String doc = e.getJavadoc();
            if (doc!=null)
                o.put("javadoc", doc);

            if (!e.isDefinition()) {
                o.put("implementation",e.implementation.getQualifiedName());
            }

            (e.isDefinition() ? def : impl).add(o);
        }

        JSONObject o = new JSONObject();
        o.put("definitions",def);
        o.put("implementations",impl);
        o.put("name",a.artifact.artifactId);
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
