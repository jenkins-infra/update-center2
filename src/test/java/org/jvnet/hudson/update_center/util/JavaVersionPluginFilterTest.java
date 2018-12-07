package org.jvnet.hudson.update_center.util;

import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.update_center.HPI;
import org.jvnet.hudson.update_center.PluginHistory;
import org.jvnet.hudson.update_center.impl.pluginFilter.JavaVersionPluginFilter;
import org.sonatype.nexus.index.ArtifactInfo;

import javax.annotation.CheckForNull;

public class JavaVersionPluginFilterTest {
    private static class MockHPI extends HPI {

        private final JavaSpecificationVersion minimumJavaVersion;

        public MockHPI(ArtifactInfo info, JavaSpecificationVersion minimumJavaVersion) throws Exception {
            super(null, new PluginHistory(info.artifactId), info);
            this.minimumJavaVersion = minimumJavaVersion;
        }

        @CheckForNull
        @Override
        public JavaSpecificationVersion getMinimumJavaVersion() {
            return minimumJavaVersion;
        }
    }

    @Test
    public void testFilter() throws Exception {
        ArtifactInfo info = new ArtifactInfo();
        info.artifactId = "test-plugin";
        info.version = "1.0";
        JavaSpecificationVersion version11 = new JavaSpecificationVersion("11");
        JavaSpecificationVersion version8 = new JavaSpecificationVersion("1.8");
        JavaSpecificationVersion version7 = new JavaSpecificationVersion("1.7");
        HPI java11Hpi = new MockHPI(info, version11);
        HPI undefinedJavaHpi = new MockHPI(info, null);

        Assert.assertFalse(new JavaVersionPluginFilter(version11).shouldIgnore(java11Hpi));
        Assert.assertTrue(new JavaVersionPluginFilter(version8).shouldIgnore(java11Hpi));
        Assert.assertTrue(new JavaVersionPluginFilter(version7).shouldIgnore(java11Hpi));

        Assert.assertFalse(new JavaVersionPluginFilter(version11).shouldIgnore(undefinedJavaHpi));
        Assert.assertFalse(new JavaVersionPluginFilter(version8).shouldIgnore(undefinedJavaHpi));
        Assert.assertFalse(new JavaVersionPluginFilter(version7).shouldIgnore(undefinedJavaHpi));
    }
}
