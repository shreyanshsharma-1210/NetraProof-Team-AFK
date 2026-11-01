package com.emergency.patient.network.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;

public class QualityGateResponse implements Serializable {

    @SerializedName("acceptable")
    private boolean acceptable;

    @SerializedName("rejection_reason")
    private String rejectionReason;

    @SerializedName("message")
    private String message;

    @SerializedName("focus_score")
    private double focusScore;

    @SerializedName("illumination_score")
    private double illuminationScore;

    @SerializedName("field_of_view_score")
    private double fieldOfViewScore;

    @SerializedName("centering_score")
    private double centeringScore;

    @SerializedName("contrast_score")
    private double contrastScore;

    @SerializedName("all_rejection_reasons")
    private List<String> allRejectionReasons;

    public boolean isAcceptable() {
        return acceptable;
    }

    public void setAcceptable(boolean acceptable) {
        this.acceptable = acceptable;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public double getFocusScore() {
        return focusScore;
    }

    public void setFocusScore(double focusScore) {
        this.focusScore = focusScore;
    }

    public double getIlluminationScore() {
        return illuminationScore;
    }

    public void setIlluminationScore(double illuminationScore) {
        this.illuminationScore = illuminationScore;
    }

    public double getFieldOfViewScore() {
        return fieldOfViewScore;
    }

    public void setFieldOfViewScore(double fieldOfViewScore) {
        this.fieldOfViewScore = fieldOfViewScore;
    }

    public double getCenteringScore() {
        return centeringScore;
    }

    public void setCenteringScore(double centeringScore) {
        this.centeringScore = centeringScore;
    }

    public double getContrastScore() {
        return contrastScore;
    }

    public void setContrastScore(double contrastScore) {
        this.contrastScore = contrastScore;
    }

    public List<String> getAllRejectionReasons() {
        return allRejectionReasons;
    }

    public void setAllRejectionReasons(List<String> allRejectionReasons) {
        this.allRejectionReasons = allRejectionReasons;
    }
}

