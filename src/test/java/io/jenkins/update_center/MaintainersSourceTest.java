package io.jenkins.update_center;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class MaintainersSourceTest {
    @Test
    public void testContent() {
        final List<MaintainersSource.Maintainer> maintainers = MaintainersSource.getInstance().getMaintainers(new ArtifactCoordinates("org.jenkins-ci.plugins", "matrix-auth", "unused", "unused"));
        Assert.assertEquals("matrix-auth has one maintainer", 1, maintainers.size());
        final MaintainersSource.Maintainer maintainer = maintainers.get(0);
        Assert.assertEquals("User ID expected", "danielbeck", maintainer.getDeveloperId());
        Assert.assertEquals("Display name expected", "Daniel Beck", maintainer.getName());
    }
}
