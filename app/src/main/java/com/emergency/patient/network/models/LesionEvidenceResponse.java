package com.emergency.patient.network.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.Map;

public class LesionEvidenceResponse implements Serializable {

    @SerializedName("status")
    private String status;

    @SerializedName("threshold")
    private double threshold;

    @SerializedName("masks")
    private Map<String, String> masks; // Base64 transparent PNG per lesion class (e.g., EX, HE, MA, CTW)

    @SerializedName("coverage")
    private Map<String, Object> coverage;

    @SerializedName("message")
    private String message;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public Map<String, String> getMasks() {
        return masks;
    }

    public void setMasks(Map<String, String> masks) {
        this.masks = masks;
    }

    public Map<String, Object> getCoverage() {
        return coverage;
    }

    public void setCoverage(Map<String, Object> coverage) {
        this.coverage = coverage;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

