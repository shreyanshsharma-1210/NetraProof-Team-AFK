package com.emergency.patient.network.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.Map;

public class ScreenResponse implements Serializable {

    @SerializedName("status")
    private String status;

    @SerializedName("clinical_status")
    private String clinicalStatus;

    @SerializedName("is_actionable")
    private boolean isActionable;

    @SerializedName("patient_id")
    private String patientId;

    @SerializedName("eye_laterality")
    private String eyeLaterality;

    @SerializedName("timestamp")
    private String timestamp;

    @SerializedName("quality_gate")
    private QualityGateResponse qualityGate;

    @SerializedName("grade")
    private int grade;

    @SerializedName(value = "grade_label", alternate = {"gradeLabel"})
    private String gradeLabel;

    @SerializedName(value = "is_referable", alternate = {"isReferable"})
    private boolean isReferable;

    @SerializedName("referable_probability")
    private double referableProbability;

    @SerializedName(value = "raw_regression_score", alternate = {"rawScore", "score"})
    private double rawScore;

    @SerializedName("recommendation")
    private String recommendation;

    @SerializedName("guidance")
    private String guidance;

    @SerializedName("preview_image_base64")
    private String previewImageBase64;

    @SerializedName("medical_disclaimer")
    private String medicalDisclaimer;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getClinicalStatus() {
        return clinicalStatus;
    }

    public void setClinicalStatus(String clinicalStatus) {
        this.clinicalStatus = clinicalStatus;
    }

    public boolean isActionable() {
        return isActionable;
    }

    public void setActionable(boolean actionable) {
        isActionable = actionable;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getEyeLaterality() {
        return eyeLaterality;
    }

    public void setEyeLaterality(String eyeLaterality) {
        this.eyeLaterality = eyeLaterality;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public QualityGateResponse getQualityGate() {
        return qualityGate;
    }

    public void setQualityGate(QualityGateResponse qualityGate) {
        this.qualityGate = qualityGate;
    }

    public int getGrade() {
        return grade;
    }

    public void setGrade(int grade) {
        this.grade = grade;
    }

    public String getGradeLabel() {
        return gradeLabel;
    }

    public void setGradeLabel(String gradeLabel) {
        this.gradeLabel = gradeLabel;
    }

    public boolean isReferable() {
        return isReferable;
    }

    public void setReferable(boolean referable) {
        isReferable = referable;
    }

    public double getReferableProbability() {
        return referableProbability;
    }

    public void setReferableProbability(double referableProbability) {
        this.referableProbability = referableProbability;
    }

    public double getRawScore() {
        return rawScore;
    }

    public void setRawScore(double rawScore) {
        this.rawScore = rawScore;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getGuidance() {
        return guidance;
    }

    public void setGuidance(String guidance) {
        this.guidance = guidance;
    }

    public String getPreviewImageBase64() {
        return previewImageBase64;
    }

    public void setPreviewImageBase64(String previewImageBase64) {
        this.previewImageBase64 = previewImageBase64;
    }

    public String getMedicalDisclaimer() {
        return medicalDisclaimer;
    }

    public void setMedicalDisclaimer(String medicalDisclaimer) {
        this.medicalDisclaimer = medicalDisclaimer;
    }
}

