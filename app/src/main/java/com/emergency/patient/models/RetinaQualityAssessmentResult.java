package com.emergency.patient.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RetinaQualityAssessmentResult — Data model encapsulating the complete optical
 * and clinical quality evaluation of a retinal fundus image.
 *
 * Distinguishes strictly between:
 *  - File validation (readable bitmap, valid format/dimensions)
 *  - Real AI image quality assessment (sharpness, illumination, artifacts)
 *  - Clinical suitability for downstream screening (DR grading & lesion segmentation gates)
 */
public class RetinaQualityAssessmentResult implements Serializable {

    public enum OverallState {
        PENDING("Assessment Pending", "Your image is ready for assessment. Waiting for the quality-analysis service."),
        IN_PROGRESS("Assessment In Progress", "Evaluating retinal image sharpness, illumination, contrast, and field centration."),
        PASSED("Image Quality Passed", "The image has passed the configured quality checks and can proceed to the next screening stage."),
        NEEDS_IMPROVEMENT("Image Quality Needs Improvement", "Some image-quality issues were detected. Please review the recommendations before continuing."),
        FAILED("Unable to Assess Image", "The quality assessment could not be completed. Please try again."),
        SERVICE_UNAVAILABLE("Assessment Pending Integration", "The NetraProof automated quality assessment model is not yet connected to this client. Downstream AI stages remain locked.");

        private final String defaultTitle;
        private final String defaultDescription;

        OverallState(String defaultTitle, String defaultDescription) {
            this.defaultTitle = defaultTitle;
            this.defaultDescription = defaultDescription;
        }

        public String getDefaultTitle() {
            return defaultTitle;
        }

        public String getDefaultDescription() {
            return defaultDescription;
        }
    }

    private String sessionId;
    private String patientUuid;
    private String imagePath;
    private String fileName;
    private int imageWidth;
    private int imageHeight;
    private long fileSizeBytes;
    private OverallState overallState;
    private String stateTitle;
    private String stateDescription;
    private long assessmentTimestamp;
    private boolean backendConnected;
    private String failureReason;

    private final List<RetinaQualityDimension> dimensions = new ArrayList<>();
    private final List<String> detectedIssues = new ArrayList<>();
    private final List<String> recommendations = new ArrayList<>();

    public RetinaQualityAssessmentResult() {
        this.overallState = OverallState.PENDING;
        this.stateTitle = overallState.getDefaultTitle();
        this.stateDescription = overallState.getDefaultDescription();
        this.assessmentTimestamp = 0;
        this.backendConnected = false;
        initDefaultDimensions();
    }

    private void initDefaultDimensions() {
        dimensions.clear();
        dimensions.add(new RetinaQualityDimension("Image Sharpness & Focus", "Spatial resolution and sharpness of retinal vascular bifurcations."));
        dimensions.add(new RetinaQualityDimension("Illumination & Brightness", "Evenness of illumination across macular, peripapillary, and peripheral zones."));
        dimensions.add(new RetinaQualityDimension("Contrast & Dynamic Range", "Visual separation between retinal background and microvascular structures."));
        dimensions.add(new RetinaQualityDimension("Retinal Field Visibility", "Centration and visibility of optic disc, macula, and major vascular arcades."));
        dimensions.add(new RetinaQualityDimension("Blur & Motion Artifacts", "Absence of camera shake, eye saccade, or defocus blur."));
        dimensions.add(new RetinaQualityDimension("Reflections & Glare", "Absence of anterior segment corneal reflections or flash halos occluding the retina."));
        dimensions.add(new RetinaQualityDimension("Obstruction & Incomplete Field", "Absence of eyelashes, eyelid droop (ptosis), or pupil-edge vignetting."));
    }

    /**
     * Determines whether downstream AI screening models (DR Grading and Lesion Segmentation)
     * are clinically unlocked.
     *
     * CLINICAL RULE: Downstream models must NEVER run unless the quality gate explicitly PASSED.
     */
    public boolean isDownstreamUnlocked() {
        return overallState == OverallState.PASSED;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getPatientUuid() {
        return patientUuid;
    }

    public void setPatientUuid(String patientUuid) {
        this.patientUuid = patientUuid;
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

    public OverallState getOverallState() {
        return overallState;
    }

    public void setOverallState(OverallState overallState) {
        this.overallState = overallState;
        if (overallState != null) {
            this.stateTitle = overallState.getDefaultTitle();
            this.stateDescription = overallState.getDefaultDescription();
        }
    }

    public String getStateTitle() {
        return stateTitle;
    }

    public void setStateTitle(String stateTitle) {
        this.stateTitle = stateTitle;
    }

    public String getStateDescription() {
        return stateDescription;
    }

    public void setStateDescription(String stateDescription) {
        this.stateDescription = stateDescription;
    }

    public long getAssessmentTimestamp() {
        return assessmentTimestamp;
    }

    public void setAssessmentTimestamp(long assessmentTimestamp) {
        this.assessmentTimestamp = assessmentTimestamp;
    }

    public boolean isBackendConnected() {
        return backendConnected;
    }

    public void setBackendConnected(boolean backendConnected) {
        this.backendConnected = backendConnected;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public List<RetinaQualityDimension> getDimensions() {
        return dimensions;
    }

    public void setDimensions(List<RetinaQualityDimension> newDimensions) {
        dimensions.clear();
        if (newDimensions != null) {
            dimensions.addAll(newDimensions);
        }
    }

    public List<String> getDetectedIssues() {
        return Collections.unmodifiableList(detectedIssues);
    }

    public void addDetectedIssue(String issue) {
        if (issue != null && !issue.trim().isEmpty() && !detectedIssues.contains(issue)) {
            detectedIssues.add(issue.trim());
        }
    }

    public void clearDetectedIssues() {
        detectedIssues.clear();
    }

    public List<String> getRecommendations() {
        return Collections.unmodifiableList(recommendations);
    }

    public void addRecommendation(String recommendation) {
        if (recommendation != null && !recommendation.trim().isEmpty() && !recommendations.contains(recommendation)) {
            recommendations.add(recommendation.trim());
        }
    }

    public void clearRecommendations() {
        recommendations.clear();
    }
}

