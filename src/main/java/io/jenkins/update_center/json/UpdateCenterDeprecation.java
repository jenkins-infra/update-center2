package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;

public class UpdateCenterDeprecation {

    @JSONField
    public final String url;

    public UpdateCenterDeprecation(String url) {
        this.url = url;
    }
}
