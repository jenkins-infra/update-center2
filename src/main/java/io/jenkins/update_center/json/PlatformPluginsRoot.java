package io.jenkins.update_center.json;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class PlatformPluginsRoot extends WithSignature {

    @JSONField
    public List<PlatformCategory> categories;

    public PlatformPluginsRoot(File referenceFile) throws IOException {
        try (Reader r = Files.newReader(referenceFile, StandardCharsets.UTF_8)) {
            categories = Arrays.asList(JSON.parseObject(IOUtils.toString(r), PlatformCategory[].class));
        }
    }
}
