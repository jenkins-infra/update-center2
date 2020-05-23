package io.jenkins.update_center.util;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Environment {
    private Environment() {}

    public static String getString(@Nonnull String key, @CheckForNull String defaultValue) {
        final String property = System.getProperty(key);
        if (property != null) {
            LOGGER.log(Level.CONFIG, "Found key: " + key + " in system properties: " + property);
            return property;
        }

        final String env = System.getenv(key);
        if (env != null) {
            LOGGER.log(Level.CONFIG, "Found key: " + key + " in process environment: " + env);
            return env;
        }

        LOGGER.log(Level.CONFIG, "Failed to find key: " + key + " so using default: " + defaultValue);
        return defaultValue;
    }

    public static String getString(@Nonnull String key) {
        return getString(key, null);
    }

    private static final Logger LOGGER = Logger.getLogger(Environment.class.getName());
}
