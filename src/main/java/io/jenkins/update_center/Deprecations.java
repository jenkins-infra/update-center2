package io.jenkins.update_center;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class Deprecations {
    private Deprecations() {}

    public static String getCustomDeprecationUri(String pluginName) {
        return DEPRECATIONS.getProperty(pluginName);
    }

    public static List<String> getDeprecatedPlugins() {
        return DEPRECATIONS.keySet().stream().map(Object::toString).collect(Collectors.toList());
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
