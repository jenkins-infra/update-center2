package org.jvnet.hudson.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;

public class UpdateCenterWarningVersionRange {
    @JSONField
    public String lastVersion;

    @JSONField
    public String Pattern;
}
