package io.jenkins.update_center.json;

import com.alibaba.fastjson.annotation.JSONField;

import java.util.List;

public class JsonSignature {
    private List<String> certificates;

    private String digest;
    private String signature;

    private String digest512;
    private String signature512;

    public void setCertificates(List<String> certificates) {
        this.certificates = certificates;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public void setDigest512(String digest512) {
        this.digest512 = digest512;
    }

    public void setSignature512(String signature512) {
        this.signature512 = signature512;
    }

    public List<String> getCertificates() {
        return certificates;
    }

    @JSONField(name = "correct_digest")
    public String getDigest() {
        return digest;
    }

    @JSONField(name = "correct_signature")
    public String getSignature() {
        return signature;
    }

    @JSONField(name = "correct_digest512")
    public String getDigest512() {
        return digest512;
    }

    @JSONField(name = "correct_signature512")
    public String getSignature512() {
        return signature512;
    }
}
