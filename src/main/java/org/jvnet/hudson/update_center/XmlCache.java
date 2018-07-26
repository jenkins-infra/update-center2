package org.jvnet.hudson.update_center;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    public static void writeCache(File file, String xpath, String value) throws IOException {
        cache.put(file + ":" + xpath, new CachedValue(value));
    }
}
