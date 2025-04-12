package io.jenkins.update_center.json;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class WithoutSignature {
    public void write(File file, boolean pretty) throws IOException {
        final File parent = file.getParentFile();
        if (!parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Failed to create " + parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            if (pretty) {
                JSON.writeJSONString(writer, this, SerializerFeature.DisableCircularReferenceDetect, SerializerFeature.PrettyFormat);
            } else {
                JSON.writeJSONString(writer, this, SerializerFeature.DisableCircularReferenceDetect);
            }
        }
    }
}
