package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;

import java.util.List;

public class PlatformCategory {
    @JSONField
    public String category;

    @JSONField
    public List<PlatformPlugin> plugins;
}
