package io.jenkins.update_center.util;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Environment {
    private Environment() {}

    public static String getString(String key) {
        return getString(key, null);
    }

    public static String getString(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null) {
            LOGGER.log(Level.CONFIG, "Found key: " + key + " in system properties: " + value);
            return value;
        }

        value = System.getenv(key);
        if (value != null) {
            LOGGER.log(Level.CONFIG, "Found key: " + key + " in process environment: " + value);
            return value;
        }

        LOGGER.log(Level.CONFIG, "Failed to find key: " + key + " so using default: " + defaultValue);
        return defaultValue;
    }

    public static int getInteger(String key) {
        return getInteger(key, 0);
    }

    public static int getInteger(String key, int defaultValue) {
        String value = getString(key, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Invalid value for key: " + key + ", value: " + value, e);
            return defaultValue;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Environment.class.getName());
}

