package io.jenkins.update_center.json;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class WithoutSignature {
    public void write(File file, boolean pretty) throws IOException {
        if (pretty) {
            JSON.writeJSONString(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8), this, SerializerFeature.DisableCircularReferenceDetect, SerializerFeature.PrettyFormat);
        } else {
            JSON.writeJSONString(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8), this, SerializerFeature.DisableCircularReferenceDetect);
        }
    }
}
