package io.jenkins.update_center;

import junit.framework.TestCase;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class PluginTest extends TestCase {

    public void testSimplifyPluginName() {
        assertSimpleName("AWS Batch Plugin", "AWS Batch");
        assertSimpleName("AWS CodeDeploy Plugin for Jenkins", "AWS CodeDeploy");
        assertSimpleName("All changes plugin", "All changes");
        assertSimpleName("Chef Sinatra Jenkins plugin", "Chef Sinatra");
//        assertSimpleName("ClearCase UCM Plugin!", "ClearCase UCM");
//        assertSimpleName("CocoaPods Jenkins Integration", "CocoaPods");
//        assertSimpleName("Computer-queue-plugin", "Computer-queue");
//        assertSimpleName("Coverage/Complexity Scatter Plot PlugIn", "Coverage/Complexity Scatter Plot");
        assertSimpleName("Cucumber json test reporting.", "Cucumber json test reporting");
        assertSimpleName("DaticalDB4Jenkins", "DaticalDB4Jenkins");
        assertSimpleName("Dynamic Extended Choice Parameter Plug-In", "Dynamic Extended Choice Parameter");
        assertSimpleName("ElasticBox Jenkins Kubernetes CI/CD Plug-in", "ElasticBox Jenkins Kubernetes CI/CD");
//        assertSimpleName("emotional-jenkins-plugin", "emotional-jenkins");
        assertSimpleName("Google Deployment Manager Jenkins Plugin", "Google Deployment Manager");
        assertSimpleName("Hudson Blame Subversion Plug-in", "Blame Subversion");
        assertSimpleName("Hudson global-build-stats plugin", "global-build-stats");
        assertSimpleName("IBM Content Navigator remote plug-in reloader", "IBM Content Navigator remote plug-in reloader");
//        assertSimpleName("Inedo BuildMaster Plugin.", "Inedo BuildMaster");
        assertSimpleName("Jenkins AccuRev plugin", "AccuRev");
        assertSimpleName("Jenkins Clone Workspace SCM Plug-in", "Clone Workspace SCM");
        assertSimpleName("Jenkins Harvest SCM", "Harvest SCM");
        assertSimpleName("Jenkins Self-Organizing Swarm Plug-in Modules", "Self-Organizing Swarm Plug-in Modules");
        assertSimpleName("JenkinsLint Plugin", "JenkinsLint");
//        assertSimpleName("jenkins-cloudformation-plugin", "jenkins-cloudformation");
//        assertSimpleName("Mail Commander Plugin for Jenkins-ci", "Mail Commander");
//        assertSimpleName("Maven Metadata Plugin for Jenkins CI server", "Maven Metadata");
        assertSimpleName("PTC Integrity CM - Jenkins Plugin", "PTC Integrity CM");
        assertSimpleName("Plugin Usage - Plugin", "Plugin Usage");
        assertSimpleName("Smart Jenkins", "Smart Jenkins");
//        assertSimpleName("Testdroid Plugin for CI", "Testdroid");
        assertSimpleName("Use Dumpling from Jenkins groovy scripts", "Use Dumpling from Jenkins groovy scripts");
        assertSimpleName("JavaScript GUI Lib: jQuery bundles (jQuery and jQuery UI)", "JavaScript GUI Lib: jQuery bundles (jQuery and jQuery UI)");
    }

    private static void assertSimpleName(String original, String expected) {
        assertEquals(expected, HPI.simplifyPluginName(original));
    }

    public void testTopLevelUrl() {
        assertEquals("https://github.com/jenkinsci/repo",
                HPI.requireTopLevelUrl("https://github.com/jenkinsci/repo"));
        assertNull(HPI.requireTopLevelUrl("https://github.com/jenkinsci/repo/subfolder"));
    }

    private static class TestRepository extends BaseMavenRepository {

        private Map<ArtifactCoordinates, Long> metadata = new HashMap<>();

        @Override
        protected Set<ArtifactCoordinates> listAllJenkinsWars(String groupId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<ArtifactCoordinates> listAllPlugins() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ArtifactMetadata getMetadata(MavenArtifact artifact) {
            ArtifactMetadata metadata = new ArtifactMetadata();
            metadata.timestamp = this.metadata.get(artifact.artifact);
            return metadata;
        }

        @Override
        public Manifest getManifest(MavenArtifact artifact) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getZipFileEntry(MavenArtifact artifact, String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File resolve(ArtifactCoordinates artifact) {
            throw new UnsupportedOperationException();
        }
    }

    private static HPI registerAndAdd(TestRepository repository, ArtifactCoordinates coordinates, Plugin plugin, long timestamp) throws IOException {
        repository.metadata.put(coordinates, timestamp);
        final HPI hpi = new HPI(repository, coordinates, plugin);
        plugin.addArtifact(hpi);
        return hpi;
    }

    @Test
    public void testDuplicateDetection() throws Exception {
        Plugin plugin = new Plugin("foo");
        final RecordingHandler handler = new RecordingHandler();
        Logger.getLogger(Plugin.class.getName()).addHandler(handler);
        TestRepository repository = new TestRepository();
        registerAndAdd(repository, new ArtifactCoordinates("the-group", "foo", "1.0", "hpi"), plugin, 0);
        registerAndAdd(repository, new ArtifactCoordinates("the-group", "foo", "1.0.0", "hpi"), plugin, 0);
        registerAndAdd(repository, new ArtifactCoordinates("the-group", "foo", "1.0.0.0", "hpi"), plugin, 0);
        registerAndAdd(repository, new ArtifactCoordinates("the-other-group", "foo", "1.0", "hpi"), plugin, 0);
        assertMessageSubstringLogged(handler, "Found a duplicate artifact the-group:foo:1.0.0 (proposed) considered identical to the-group:foo:1.0 (existing) due to non-determinism. Neither has a timestamp. Neither will be published.");
        assertMessageSubstringLogged(handler, "Found another duplicate artifact the-group:foo:1.0.0.0 considered identical due to non-determinism. Neither has a timestamp. Neither will be published.");
        assertMessageSubstringLogged(handler, "Found another duplicate artifact the-other-group:foo:1.0 considered identical due to non-determinism. Neither has a timestamp. Neither will be published.");
        assertTrue("No versions", plugin.getArtifacts().isEmpty());
    }

    public void testKeepOlder() throws Exception {
        Plugin plugin = new Plugin("foo");
        final RecordingHandler handler = new RecordingHandler();
        Logger.getLogger(Plugin.class.getName()).addHandler(handler);
        TestRepository repository = new TestRepository();
        final HPI first = registerAndAdd(repository, new ArtifactCoordinates("the-group", "foo", "1.0", "hpi"), plugin, 1);
        plugin.addArtifact(first);
        registerAndAdd(repository, new ArtifactCoordinates("the-group", "foo", "1.0.0", "hpi"), plugin, 2);
        registerAndAdd(repository, new ArtifactCoordinates("the-group", "foo", "1.0.0.0", "hpi"), plugin, 1);
        registerAndAdd(repository, new ArtifactCoordinates("the-other-group", "foo", "1.0", "hpi"), plugin, 4);
        assertMessageSubstringLogged(handler, "The proposed artifact: the-group:foo:1.0.0 is not older than the existing artifact the-group:foo:1.0, so ignore it.");
        assertMessageSubstringLogged(handler, "The proposed artifact: the-group:foo:1.0.0.0 is not older than the existing artifact the-group:foo:1.0, so ignore it.");
        assertMessageSubstringLogged(handler, "The proposed artifact: the-other-group:foo:1.0 is not older than the existing artifact the-group:foo:1.0, so ignore it.");
        assertEquals("One artifact", 1, plugin.getArtifacts().size());
        assertEquals("Original artifact retained", plugin.getArtifacts().firstEntry().getValue(), first);
    }

    public void testReplaceAll() throws Exception {
        Plugin plugin = new Plugin("foo");
        final RecordingHandler handler = new RecordingHandler();
        Logger.getLogger(Plugin.class.getName()).addHandler(handler);
        TestRepository repository = new TestRepository();
        registerAndAdd(repository, new ArtifactCoordinates("the-group", "foo", "1.0", "hpi"), plugin, 4);
        registerAndAdd(repository, new ArtifactCoordinates("the-group", "foo", "1.0.0", "hpi"), plugin, 3);
        registerAndAdd(repository, new ArtifactCoordinates("the-group", "foo", "1.0.0.0", "hpi"), plugin, 2);
        final HPI oldest = registerAndAdd(repository, new ArtifactCoordinates("the-other-group", "foo", "1.0", "hpi"), plugin, 1);
        assertMessageSubstringLogged(handler, "The proposed artifact: the-group:foo:1.0.0 is older than the existing artifact the-group:foo:1.0, so replace it.");
        assertMessageSubstringLogged(handler, "The proposed artifact: the-group:foo:1.0.0.0 is older than the existing artifact the-group:foo:1.0.0, so replace it.");
        assertMessageSubstringLogged(handler, "The proposed artifact: the-other-group:foo:1.0 is older than the existing artifact the-group:foo:1.0.0.0, so replace it.");
        assertEquals("One artifact", 1, plugin.getArtifacts().size());
        assertEquals("Original artifact retained", plugin.getArtifacts().firstEntry().getValue(), oldest);
    }

    public void testRemoveAll() throws Exception {
        Plugin plugin = new Plugin("foo");
        final RecordingHandler handler = new RecordingHandler();
        Logger.getLogger(Plugin.class.getName()).addHandler(handler);
        TestRepository repository = new TestRepository();
        registerAndAdd(repository, new ArtifactCoordinates("the-group", "foo", "1.0", "hpi"), plugin, 0);
        registerAndAdd(repository, new ArtifactCoordinates("the-group", "foo", "1.0.0", "hpi"), plugin, 42);
        registerAndAdd(repository, new ArtifactCoordinates("the-group", "foo", "1.0.0.0", "hpi"), plugin, 42);
        registerAndAdd(repository, new ArtifactCoordinates("the-other-group", "foo", "1.0", "hpi"), plugin, 0);
        assertMessageSubstringLogged(handler, "The proposed artifact: the-group:foo:1.0.0 has a timestamp and the existing artifact the-group:foo:1.0 does not, so replace it.");
        assertMessageSubstringLogged(handler, "The proposed artifact: the-group:foo:1.0.0.0 is not older than the existing artifact the-group:foo:1.0.0, so ignore it.");
        assertMessageSubstringLogged(handler, "The proposed artifact: the-other-group:foo:1.0 has no timestamp (but the existing artifact the-other-group:foo:1.0 does), so ignore it.");
        assertEquals("One artifact", 1, plugin.getArtifacts().size());
    }

    private static void assertMessageSubstringLogged(RecordingHandler handler, String message) {
        assertTrue("Message logged: " + message, handler.records.stream().anyMatch(it -> it.getMessage().contains(message)));
    }

    private static class RecordingHandler extends Handler {

        private List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() throws SecurityException {

        }
    }
}