package org.jvnet.hudson.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;

import java.util.List;

public class JsonSignature {
    @JSONField
    public final List<String> certificates;

    @JSONField
    public final String correct_digest;
    @JSONField
    public final String correct_signature;

    @JSONField
    public final String correct_digest512;
    @JSONField
    public final String correct_signature512;

    public JsonSignature(List<String> certificates, String correct_digest, String correct_signature, String correct_digest512, String correct_signature512) {
        this.certificates = certificates;
        this.correct_digest = correct_digest;
        this.correct_signature = correct_signature;
        this.correct_digest512 = correct_digest512;
        this.correct_signature512 = correct_signature512;
    }
}
