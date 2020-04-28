package org.jvnet.hudson.update_center;

import org.junit.Assume;
import org.junit.Test;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class MavenArtifactSourceTest {

    @Test
    public void getManifest() throws Exception {
        final BaseMavenRepository instance = DefaultMavenRepositoryBuilder.getInstance();
        Assume.assumeTrue(instance instanceof MavenRepositoryImpl);
        MavenRepositoryImpl mavenRepository = (MavenRepositoryImpl) instance;

        HPI plugin = mavenRepository.findPlugin("org.jenkins-ci.plugins", "active-directory", "2.10");

        MavenArtifact mavenArtifact = new MavenArtifact(mavenRepository, plugin.artifact);

        Manifest manifest = mavenRepository.getManifest(mavenArtifact);
        Attributes mainAttributes = manifest.getMainAttributes();

        Attributes expected = new Attributes();
        expected.putValue("Hudson-Version", "1.625.3");
        expected.putValue("Plugin-Dependencies", "mailer:1.5");
        expected.putValue("Implementation-Title", "active-directory");
        expected.putValue("Long-Name", "Jenkins Active Directory plugin");
        expected.putValue("Implementation-Version", "2.10");
        expected.putValue("Group-Id", "org.jenkins-ci.plugins");
        expected.putValue("Archiver-Version", "Plexus Archiver");
        expected.putValue("Built-By", "fbelzunc");
        expected.putValue("Specification-Title", "Enables authentication through Active Directory");
        expected.putValue("Plugin-Version", "2.10");
        expected.putValue("Jenkins-Version", "1.625.3");
        expected.putValue("Url", "https://wiki.jenkins-ci.org/display/JENKINS/Active+Directory+Plugin");
        expected.putValue("Manifest-Version", "1.0");
        expected.putValue("Short-Name", "active-directory");
        expected.putValue("Compatible-Since-Version", "2.0");
        expected.putValue("Plugin-Developers", "Kohsuke Kawaguchi:kohsuke:,Felix Belzunce Arcos:fbelzunc:");
        expected.putValue("Extension-Name", "active-directory");
        expected.putValue("Created-By", "Apache Maven");
        expected.putValue("Build-Jdk", "1.8.0_65");

        assertThat(mainAttributes.entrySet(), is(expected.entrySet()));
    }
}