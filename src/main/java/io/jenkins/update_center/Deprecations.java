package io.jenkins.update_center;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;
import java.util.stream.Stream;

public class Deprecations {
    private Deprecations() {}

    public static String getCustomDeprecationUri(String pluginName) {
        return DEPRECATIONS.getProperty(pluginName);
    }

    public static Stream<String> getDeprecatedPlugins() {
        return Stream.concat(DEPRECATIONS.keySet().stream(),
                BaseMavenRepository.getIgnoresWithDeprecationUrl())
                .map(Object::toString);
    }

    private static final Properties DEPRECATIONS = new Properties();

    static {
        try (InputStream stream = Files.newInputStream(new File(Main.resourcesDir, "deprecations.properties").toPath())){
            DEPRECATIONS.load(stream);
        } catch (IOException e) {
            throw new Error(e);
        }
    }
}
