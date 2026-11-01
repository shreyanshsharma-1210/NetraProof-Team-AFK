package com.emergency.patient.network.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.Map;

public class HealthResponse implements Serializable {

    @SerializedName("status")
    private String status;

    @SerializedName("service")
    private String service;

    @SerializedName("timestamp")
    private String timestamp;

    @SerializedName("components")
    private Map<String, Boolean> components;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Boolean> getComponents() {
        return components;
    }

    public void setComponents(Map<String, Boolean> components) {
        this.components = components;
    }

    public boolean isHealthy() {
        return "healthy".equalsIgnoreCase(status);
    }
}

