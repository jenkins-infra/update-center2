package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;

public class PlatformPlugin {

    /**
     * The plugin ID.
     */
    @JSONField
    public String name;

    /**
     * In which version of Jenkins was this suggestion added?
     * Used for upgrade wizard / similar functionality.
     */
    @JSONField
    public String added;

    /**
     * Whether this plugin should be installed by default.
     */
    @JSONField
    public boolean suggested;
}
