package jenkins.repository.updateCenter;

import com.nirima.jenkins.repo.RepositoryElement;
import com.nirima.jenkins.repo.RepositoryExtensionPoint;
import com.nirima.jenkins.repo.RootElement;
import hudson.Extension;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jvnet.hudson.update_center.*;
import org.jvnet.hudson.update_center.UpdateCenter;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Extension
public class UpdateCenterRepository extends RepositoryExtensionPoint implements IArtifactProvider {

    private Map<String, PluginHistory> pluginHistoryList = new HashMap<String, PluginHistory>();

    private static UpdateCenterRepository instance;

    private String cachedJSON = null;

    public UpdateCenterRepository() {
        instance = this;
        generateInitialMap();
    }

    public String getData() throws Exception {
        synchronized (pluginHistoryList) {
            if( cachedJSON == null ) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                getUpdateCenter().write(byteArrayOutputStream);
                byteArrayOutputStream.close();
                cachedJSON = new String(byteArrayOutputStream.toByteArray(), "UTF-8");
            }
            return cachedJSON;
        }
    }

    @Extension
    public static class RunListenerImpl extends RunListener<Run> {
        @Override
        public void onCompleted(Run run, TaskListener listener) {
            if( run.getResult().isBetterOrEqualTo(Result.SUCCESS) ) {
                instance.updateProject(run.getParent().getName(), run);
            }
        }
    }

    @Override
    public RepositoryElement getRepositoryRoot(RootElement rootElement) {
        return new UpdateCenterRepositoryRoot(this, rootElement);
    }

    public Collection<PluginHistory> listHudsonPlugins() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
        synchronized (pluginHistoryList) {
            return new ArrayList<PluginHistory>(pluginHistoryList.values());
        }
    }

    private void generateInitialMap() {
        // For each project..
        for (BuildableItemWithBuildWrappers buildItem : Hudson.getInstance().getAllItems(BuildableItemWithBuildWrappers.class)) {

            Run run = buildItem.asProject().getLastSuccessfulBuild();
            if( run != null )
                updateProject(buildItem.getName(), run);
        }
    }

    private void updateProject(String projectName, Run run) {
        // If we have a last successful build
        synchronized (pluginHistoryList) {
            cachedJSON = null;
            if (run instanceof MavenModuleSetBuild) {
                PluginHistory ph = null;
                MavenModuleSetBuild item = (MavenModuleSetBuild) run;

                Map<MavenModule, List<MavenBuild>> modulesMap = item.getModuleBuilds();

                // Look through each of the built modules, maybe one was an HPI
                for (List<MavenBuild> builds : modulesMap.values()) {
                    for (MavenBuild build : builds) {

                        MavenArtifactRecord artifacts = build.getAction(MavenArtifactRecord.class);
                        if (artifacts != null) {
                            if (artifactRepresentsAPlugin(artifacts)) {
                                // Yes, this object was a plugin
                                String name = artifacts.mainArtifact.artifactId;
                                if( ph == null ) {
                                    ph = new PluginHistory(name);
                                    pluginHistoryList.put(projectName, ph);
                                }

                                try {
                                    JenkinsHPI hpi = new JenkinsHPI(ph, projectName, build, artifacts);
                                    ph.addArtifact(hpi);
                                }
                                catch (Exception ex) {
                                    // Problem with adding
                                }

                            }

                        }
                    }

                }

            }
        }
    }

    private boolean artifactRepresentsAPlugin(MavenArtifactRecord artifacts) {
        return artifacts.mainArtifact.type.equalsIgnoreCase("hpi");
    }


    public TreeMap<VersionNumber, HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException {
        TreeMap<VersionNumber, HudsonWar> map = new TreeMap<VersionNumber, HudsonWar>();

        return map;
    }

    public UpdateCenter getUpdateCenter() {
        return new UpdateCenter("Jenkins-CI", this);
    }
}
