package io.jenkins.update_center;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertFalse;

public class DeprecationTest {

    @Test
    public void noOverlap() throws IOException {
        Properties ignore = new Properties();
        InputStream stream = Files.newInputStream(new File(Main.resourcesDir,
                "artifact-ignores.properties").toPath());
        ignore.load(stream);
        Properties deprecations = new Properties();
        stream = Files.newInputStream(new File(Main.resourcesDir,
                "deprecations.properties").toPath());
        deprecations.load(stream);
        for (String key: deprecations.stringPropertyNames()) {
            assertFalse(ignore.containsKey(key));
        }
        for (String key: ignore.stringPropertyNames()) {
            assertFalse(deprecations.containsKey(key));
        }
    }
}
