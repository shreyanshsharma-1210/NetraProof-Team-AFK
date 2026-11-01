package com.emergency.patient.models.diabetes360;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * KidneyRecord — User-provided laboratory measurement for renal health.
 *
 * Supports eGFR, Serum Creatinine, UACR, and other kidney biomarkers.
 * Preserves original units, dates, and sources without synthetic clinical alterations.
 */
public class KidneyRecord implements Serializable {

    public enum TestType {
        EGFR("eGFR (Estimated GFR)", "mL/min/1.73 m²"),
        SERUM_CREATININE("Serum Creatinine", "mg/dL"),
        UACR("UACR (Urine Albumin/Creatinine)", "mg/g"),
        OTHER("Kidney Lab Test", "");

        private final String displayName;
        private final String defaultUnit;

        TestType(String displayName, String defaultUnit) {
            this.displayName = displayName;
            this.defaultUnit = defaultUnit;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDefaultUnit() {
            return defaultUnit;
        }
    }

    private String id;
    private String patientUuid;
    private TestType testType;
    private double value;
    private String unit;
    private long testDateMillis;
    private String source; // "Manual Entry", "Uploaded Report", "Connected Lab"
    private String labName;
    private String notes;
    private String attachmentPath;
    private long createdAtMillis;

    public KidneyRecord() {
        this.createdAtMillis = System.currentTimeMillis();
    }

    public KidneyRecord(String id, String patientUuid, TestType testType, double value, String unit,
                        long testDateMillis, String source, String labName, String notes, String attachmentPath) {
        this.id = id;
        this.patientUuid = patientUuid;
        this.testType = testType;
        this.value = value;
        this.unit = unit;
        this.testDateMillis = testDateMillis;
        this.source = source != null ? source : "Manual Entry";
        this.labName = labName;
        this.notes = notes;
        this.attachmentPath = attachmentPath;
        this.createdAtMillis = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }

    public TestType getTestType() { return testType; }
    public void setTestType(TestType testType) { this.testType = testType; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public String getUnit() { return unit != null ? unit : ""; }
    public void setUnit(String unit) { this.unit = unit; }

    public long getTestDateMillis() { return testDateMillis; }
    public void setTestDateMillis(long testDateMillis) { this.testDateMillis = testDateMillis; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getLabName() { return labName; }
    public void setLabName(String labName) { this.labName = labName; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getAttachmentPath() { return attachmentPath; }
    public void setAttachmentPath(String attachmentPath) { this.attachmentPath = attachmentPath; }

    public long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(long createdAtMillis) { this.createdAtMillis = createdAtMillis; }

    public String getFormattedDate() {
        if (testDateMillis <= 0) return "Date not provided";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(testDateMillis));
    }

    public String getFormattedValueWithUnit() {
        String valStr = (value == Math.floor(value)) ? String.format(Locale.getDefault(), "%.0f", value) : String.format(Locale.getDefault(), "%.2f", value);
        return valStr + (unit != null && !unit.isEmpty() ? " " + unit : "");
    }
}

