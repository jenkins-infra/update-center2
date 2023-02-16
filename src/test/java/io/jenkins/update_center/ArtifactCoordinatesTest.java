package io.jenkins.update_center;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ArtifactCoordinatesTest {

    @Test
    public void testVersionValid() {
        assertVersionIsValid("2", true);
        assertVersionIsValid("3.1-rc4", true);
        assertVersionIsValid("4-beta", true);
        assertVersionIsValid("2g", false);
        assertVersionIsValid("g2", false);
        assertVersionIsValid("-2", false);
    }

    private void assertVersionIsValid(String version, boolean valid) {
        ArtifactCoordinates coordinates = new ArtifactCoordinates("", "", version, "");
        assertEquals(valid, coordinates.isVersionValid());
    }
}
