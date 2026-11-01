package com.emergency.patient.models;

import java.io.Serializable;

/**
 * RetinaQualityDimension — Represents an individual optical or clinical quality
 * dimension evaluated on an uploaded retinal fundus photograph.
 *
 * Supported clinical dimensions:
 *  1. Image sharpness / focus
 *  2. Illumination and brightness
 *  3. Contrast
 *  4. Retinal field visibility
 *  5. Blur and motion artifacts
 *  6. Reflections or glare
 *  7. Obstruction or incomplete retinal visibility
 */
public class RetinaQualityDimension implements Serializable {

    public enum Status {
        GOOD("Good"),
        ACCEPTABLE("Acceptable"),
        POOR("Poor"),
        NOT_ASSESSED("Not Assessed");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private String name;
    private String technicalDescription;
    private Status status;
    private String scoreOrValue; // Numerical or categorical result from actual model (null if not assessed)
    private String issueDescription; // Specific issue detected by backend/model

    public RetinaQualityDimension(String name, String technicalDescription) {
        this.name = name;
        this.technicalDescription = technicalDescription;
        this.status = Status.NOT_ASSESSED;
        this.scoreOrValue = null;
        this.issueDescription = null;
    }

    public RetinaQualityDimension(String name, String technicalDescription, Status status, String scoreOrValue, String issueDescription) {
        this.name = name;
        this.technicalDescription = technicalDescription;
        this.status = status != null ? status : Status.NOT_ASSESSED;
        this.scoreOrValue = scoreOrValue;
        this.issueDescription = issueDescription;
    }

    public String getName() {
        return name;
    }

    public String getTechnicalDescription() {
        return technicalDescription;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getScoreOrValue() {
        return scoreOrValue;
    }

    public void setScoreOrValue(String scoreOrValue) {
        this.scoreOrValue = scoreOrValue;
    }

    public String getIssueDescription() {
        return issueDescription;
    }

    public void setIssueDescription(String issueDescription) {
        this.issueDescription = issueDescription;
    }
}

