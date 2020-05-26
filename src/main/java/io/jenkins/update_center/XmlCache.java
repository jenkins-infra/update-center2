package io.jenkins.update_center;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class XmlCache {
    public static class CachedValue {
        public final String value;
        private CachedValue(String value) {
            this.value = value;
        }
    }

    private static Map<String, CachedValue> cache = new HashMap<>();

    public static CachedValue readCache(File file, String xpath) {
        return cache.getOrDefault(file + ":" + xpath, null);
    }

    public static void writeCache(File file, String xpath, String value) {
        cache.put(file + ":" + xpath, new CachedValue(value));
    }
}
