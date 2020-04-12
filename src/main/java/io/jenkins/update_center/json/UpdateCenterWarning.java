package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;

import java.util.ArrayList;
import java.util.List;

public class UpdateCenterWarning {
    @JSONField
    public String id;

    @JSONField
    public String message;

    @JSONField
    public String name;

    @JSONField
    public String type;

    @JSONField
    public String url;

    @JSONField
    public List<UpdateCenterWarningVersionRange> versions = new ArrayList<>();
}
