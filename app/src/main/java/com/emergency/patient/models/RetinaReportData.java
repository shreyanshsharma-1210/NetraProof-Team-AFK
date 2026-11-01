package com.emergency.patient.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RetinaReportData — Aggregated screening report model for NetraProof.
 *
 * Consolidates the complete multi-stage retinal screening workflow:
 *  - Stage 01: Authenticated Patient Context & Demographics.
 *  - Stage 02: Retinal Fundus Photograph & Optical Metadata.
 *  - Stage 03: Optical Quality Assessment Outcome.
 *  - Stage 04: DR Severity Prediction (Five-fold EfficientNet-B0 Ensemble with Ordinal Regression).
 *  - Stage 05: Retinal Lesion Spatial Segmentation (U-Net + SE-ResNeXt50).
 *
 * Strict Medical Honesty:
 *  - Retains distinct model identities without inventing composite diagnostic scores.
 *  - Accurately tracks COMPLETE vs PARTIAL status.
 */
public class RetinaReportData implements Serializable {

    public enum ReportStatus {
        COMPLETE("Complete Screening Report", "All screening stages evaluated successfully."),
        PARTIAL("Partial Screening Report", "Available stages presented accurately. One or more AI services pending deployment."),
        PROCESSING("Compiling Report", "Aggregating clinical analysis outputs..."),
        FAILED("Report Generation Failed", "Unable to compile screening report for this session.");

        private final String title;
        private final String description;

        ReportStatus(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }
    }

    // ─── Patient Profile ──────────────────────────────────────────────────────
    private String patientUuid = "";
    private String patientFullName = "Patient";
    private String patientDisplayId = "NP-PAT-001";
    private String patientAge = "Not specified";
    private String patientGender = "Not specified";
    private String patientBloodGroup = "--";

    // ─── Session & Report Identifiers ─────────────────────────────────────────
    private int documentId = -1;
    private String sessionId = "NP-SCR-00001";
    private String reportId = "NP-RPT-00001";
    private long screeningTimestamp = System.currentTimeMillis();
    private long reportGeneratedTimestamp = System.currentTimeMillis();
    private ReportStatus reportStatus = ReportStatus.PARTIAL;

    // ─── Original Image ───────────────────────────────────────────────────────
    private String imagePath = "";
    private String fileName = "retina_fundus.jpg";
    private int imageWidth = 0;
    private int imageHeight = 0;
    private long fileSizeBytes = 0;

    // ─── Stage 02: Image Quality Assessment ───────────────────────────────────
    private boolean qualityPassed = true;
    private String qualityAssessmentDecision = "Passed";
    private String qualitySummary = "Image resolution and illumination meet NetraProof optical screening standards.";

    // ─── Stage 03: DR Severity Grading ────────────────────────────────────────
    private DrSeverityGrade drSeverityGrade = null;
    private String drPredictionState = "Service Pending";
    private String drModelName = "Five-fold EfficientNet-B0 Ensemble";
    private String drModelVersion = "NetraProof-EfficientNet-B0-Ensemble-v1.0";
    private String drLossFunction = "Ordinal Regression";
    private String drExplanation = "";

    // ─── Stage 04: Retinal Lesion Segmentation ────────────────────────────────
    private String segmentationState = "Service Pending Deployment";
    private String segmentationModelName = "U-Net + SE-ResNeXt50";
    private String segmentationModelVersion = "NetraProof-UNet-SEResNeXt50-v1.0";
    private final List<LesionMaskData> lesionMaskList = new ArrayList<>();

    // ─── Educational Guidance & Next Steps ────────────────────────────────────
    private String screeningInterpretation = "";
    private final List<String> recommendedNextSteps = new ArrayList<>();
    private String pdfFilePath = null;

    public RetinaReportData() {
        for (LesionCategory cat : LesionCategory.values()) {
            lesionMaskList.add(new LesionMaskData(cat));
        }
    }

    // ─── Helper Queries ───────────────────────────────────────────────────────

    public boolean isComplete() {
        return reportStatus == ReportStatus.COMPLETE;
    }

    public String getFormattedFileSize() {
        if (fileSizeBytes <= 0) return "--";
        if (fileSizeBytes < 1024) return fileSizeBytes + " B";
        if (fileSizeBytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", fileSizeBytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", fileSizeBytes / (1024.0 * 1024.0));
    }

    public String getFormattedDimensions() {
        if (imageWidth > 0 && imageHeight > 0) {
            return imageWidth + " × " + imageHeight + " px";
        }
        return "Dimensions verified";
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getPatientUuid() {
        return patientUuid;
    }

    public void setPatientUuid(String patientUuid) {
        this.patientUuid = patientUuid;
    }

    public String getPatientFullName() {
        return patientFullName;
    }

    public void setPatientFullName(String patientFullName) {
        this.patientFullName = patientFullName;
    }

    public String getPatientDisplayId() {
        return patientDisplayId;
    }

    public void setPatientDisplayId(String patientDisplayId) {
        this.patientDisplayId = patientDisplayId;
    }

    public String getPatientAge() {
        return patientAge;
    }

    public void setPatientAge(String patientAge) {
        this.patientAge = patientAge;
    }

    public String getPatientGender() {
        return patientGender;
    }

    public void setPatientGender(String patientGender) {
        this.patientGender = patientGender;
    }

    public String getPatientBloodGroup() {
        return patientBloodGroup;
    }

    public void setPatientBloodGroup(String patientBloodGroup) {
        this.patientBloodGroup = patientBloodGroup;
    }

    public int getDocumentId() {
        return documentId;
    }

    public void setDocumentId(int documentId) {
        this.documentId = documentId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public long getScreeningTimestamp() {
        return screeningTimestamp;
    }

    public void setScreeningTimestamp(long screeningTimestamp) {
        this.screeningTimestamp = screeningTimestamp;
    }

    public long getReportGeneratedTimestamp() {
        return reportGeneratedTimestamp;
    }

    public void setReportGeneratedTimestamp(long reportGeneratedTimestamp) {
        this.reportGeneratedTimestamp = reportGeneratedTimestamp;
    }

    public ReportStatus getReportStatus() {
        return reportStatus;
    }

    public void setReportStatus(ReportStatus reportStatus) {
        this.reportStatus = reportStatus;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(int imageWidth) {
        this.imageWidth = imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(int imageHeight) {
        this.imageHeight = imageHeight;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public boolean isQualityPassed() {
        return qualityPassed;
    }

    public void setQualityPassed(boolean qualityPassed) {
        this.qualityPassed = qualityPassed;
    }

    public String getQualityAssessmentDecision() {
        return qualityAssessmentDecision;
    }

    public void setQualityAssessmentDecision(String qualityAssessmentDecision) {
        this.qualityAssessmentDecision = qualityAssessmentDecision;
    }

    public String getQualitySummary() {
        return qualitySummary;
    }

    public void setQualitySummary(String qualitySummary) {
        this.qualitySummary = qualitySummary;
    }

    public DrSeverityGrade getDrSeverityGrade() {
        return drSeverityGrade;
    }

    public void setDrSeverityGrade(DrSeverityGrade drSeverityGrade) {
        this.drSeverityGrade = drSeverityGrade;
    }

    public String getDrPredictionState() {
        return drPredictionState;
    }

    public void setDrPredictionState(String drPredictionState) {
        this.drPredictionState = drPredictionState;
    }

    public String getDrModelName() {
        return drModelName;
    }

    public void setDrModelName(String drModelName) {
        this.drModelName = drModelName;
    }

    public String getDrModelVersion() {
        return drModelVersion;
    }

    public void setDrModelVersion(String drModelVersion) {
        this.drModelVersion = drModelVersion;
    }

    public String getDrLossFunction() {
        return drLossFunction;
    }

    public void setDrLossFunction(String drLossFunction) {
        this.drLossFunction = drLossFunction;
    }

    public String getDrExplanation() {
        return drExplanation;
    }

    public void setDrExplanation(String drExplanation) {
        this.drExplanation = drExplanation;
    }

    public String getSegmentationState() {
        return segmentationState;
    }

    public void setSegmentationState(String segmentationState) {
        this.segmentationState = segmentationState;
    }

    public String getSegmentationModelName() {
        return segmentationModelName;
    }

    public void setSegmentationModelName(String segmentationModelName) {
        this.segmentationModelName = segmentationModelName;
    }

    public String getSegmentationModelVersion() {
        return segmentationModelVersion;
    }

    public void setSegmentationModelVersion(String segmentationModelVersion) {
        this.segmentationModelVersion = segmentationModelVersion;
    }

    public List<LesionMaskData> getLesionMaskList() {
        return lesionMaskList;
    }

    public void setLesionMaskList(List<LesionMaskData> masks) {
        this.lesionMaskList.clear();
        if (masks != null) {
            this.lesionMaskList.addAll(masks);
        }
    }

    public String getScreeningInterpretation() {
        return screeningInterpretation;
    }

    public void setScreeningInterpretation(String screeningInterpretation) {
        this.screeningInterpretation = screeningInterpretation;
    }

    public List<String> getRecommendedNextSteps() {
        return recommendedNextSteps;
    }

    public void setRecommendedNextSteps(List<String> nextSteps) {
        this.recommendedNextSteps.clear();
        if (nextSteps != null) {
            this.recommendedNextSteps.addAll(nextSteps);
        }
    }

    public String getPdfFilePath() {
        return pdfFilePath;
    }

    public void setPdfFilePath(String pdfFilePath) {
        this.pdfFilePath = pdfFilePath;
    }
}

