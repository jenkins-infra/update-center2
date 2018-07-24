package org.jvnet.hudson.update_center;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class XmlCache {
    private static final File CACHE_DIRECTORY = new File("xmlCache");

    public static class CachedValue {
        public final String value;
        private CachedValue(String value) {
            this.value = value;
        }
    }

    public static CachedValue readCache(File file, String xpath) {
        String path = buildBase64FileName(file, xpath);
        File dir = new File(CACHE_DIRECTORY, path);
        if (dir.exists()) {
            // value has been cached previously
            File value = new File(dir, "value");
            try (FileInputStream fis = new FileInputStream(value)) {
                return new CachedValue(IOUtils.toString(fis));
            } catch (IOException io) {
                return new CachedValue(null);
            }
        }
        return null;
    }

    private static String buildBase64FileName(File file, String xpath) {
        return Base64.encodeBase64String((file + ":" + xpath).getBytes(StandardCharsets.UTF_8));
    }

    public static void writeCache(File file, String xpath, String value) throws IOException {
        String path = buildBase64FileName(file, xpath);
        File dir = new File(CACHE_DIRECTORY, path);
        dir.mkdirs();
        File valueFile = new File(dir, "value");
        try(FileOutputStream fos = new FileOutputStream(valueFile)) {
            IOUtils.write(value, fos, "UTF-8");
        } catch (IOException ex) {
            dir.delete();
        }
    }
}
