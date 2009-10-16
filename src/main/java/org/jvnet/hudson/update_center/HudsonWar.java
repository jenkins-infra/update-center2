package org.jvnet.hudson.update_center;

import org.sonatype.nexus.index.ArtifactInfo;

import java.net.URL;
import java.net.MalformedURLException;

/**
 * @author Kohsuke Kawaguchi
 */
public class HudsonWar extends MavenArtifact {
    public HudsonWar(MavenRepository repository, ArtifactInfo artifact) {
        super(repository, artifact);
    }

    @Override
    public URL getURL() throws MalformedURLException {
        return new URL("http://hudson-ci.org/download/war/"+version+"/hudson.war");
    }
}
