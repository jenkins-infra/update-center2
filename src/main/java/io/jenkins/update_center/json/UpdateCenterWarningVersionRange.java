package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;

public class UpdateCenterWarningVersionRange {
    @JSONField
    public String firstVersion;

    @JSONField
    public String lastVersion;

    @JSONField
    public String pattern;
}
