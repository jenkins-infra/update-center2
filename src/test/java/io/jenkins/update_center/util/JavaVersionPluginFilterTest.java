package io.jenkins.update_center.util;

import io.jenkins.update_center.ArtifactCoordinates;
import io.jenkins.update_center.filters.JavaVersionPluginFilter;
import org.junit.Assert;
import org.junit.Test;
import io.jenkins.update_center.HPI;

import javax.annotation.CheckForNull;

public class JavaVersionPluginFilterTest {
    private static class MockHPI extends HPI {

        private final JavaSpecificationVersion minimumJavaVersion;

        public MockHPI(ArtifactCoordinates info, JavaSpecificationVersion minimumJavaVersion) throws Exception {
            super(null, info, null);
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
        ArtifactCoordinates info = new ArtifactCoordinates(null, "test-plugin", "1.0", null);
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
