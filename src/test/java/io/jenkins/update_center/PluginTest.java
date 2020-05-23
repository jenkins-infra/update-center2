package io.jenkins.update_center;

import junit.framework.TestCase;

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

}