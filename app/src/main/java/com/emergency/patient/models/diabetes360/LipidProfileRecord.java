package com.emergency.patient.models.diabetes360;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * LipidProfileRecord — User-provided lipid laboratory panel.
 *
 * Supports Total Cholesterol, LDL, HDL, and Triglycerides.
 * Allows partial profiles when the original lab contains only a subset.
 * Preserves supplied units and lab sources.
 */
public class LipidProfileRecord implements Serializable {

    private String id;
    private String patientUuid;
    private Double totalCholesterol; // null if not in test
    private Double ldl;
    private Double hdl;
    private Double triglycerides;
    private String unit = "mg/dL"; // e.g. "mg/dL" or "mmol/L"
    private long testDateMillis;
    private String source; // "Manual Entry", "Uploaded Report", "Connected Lab"
    private String labName;
    private String notes;
    private String attachmentPath;
    private long createdAtMillis;

    public LipidProfileRecord() {
        this.createdAtMillis = System.currentTimeMillis();
    }

    public LipidProfileRecord(String id, String patientUuid, Double totalCholesterol, Double ldl,
                              Double hdl, Double triglycerides, String unit, long testDateMillis,
                              String source, String labName, String notes, String attachmentPath) {
        this.id = id;
        this.patientUuid = patientUuid;
        this.totalCholesterol = totalCholesterol;
        this.ldl = ldl;
        this.hdl = hdl;
        this.triglycerides = triglycerides;
        this.unit = unit != null && !unit.isEmpty() ? unit : "mg/dL";
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

    public Double getTotalCholesterol() { return totalCholesterol; }
    public void setTotalCholesterol(Double totalCholesterol) { this.totalCholesterol = totalCholesterol; }

    public Double getLdl() { return ldl; }
    public void setLdl(Double ldl) { this.ldl = ldl; }

    public Double getHdl() { return hdl; }
    public void setHdl(Double hdl) { this.hdl = hdl; }

    public Double getTriglycerides() { return triglycerides; }
    public void setTriglycerides(Double triglycerides) { this.triglycerides = triglycerides; }

    public String getUnit() { return unit != null ? unit : "mg/dL"; }
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
        if (testDateMillis <= 0) return "Date not recorded";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(testDateMillis));
    }

    public String getSummaryText() {
        StringBuilder sb = new StringBuilder();
        if (totalCholesterol != null) sb.append("Total: ").append(formatVal(totalCholesterol)).append(" ");
        if (ldl != null) sb.append("• LDL: ").append(formatVal(ldl)).append(" ");
        if (hdl != null) sb.append("• HDL: ").append(formatVal(hdl)).append(" ");
        if (triglycerides != null) sb.append("• TG: ").append(formatVal(triglycerides));
        if (sb.length() == 0) return "Partial lipid panel";
        return sb.toString().trim() + " (" + getUnit() + ")";
    }

    private String formatVal(Double d) {
        if (d == null) return "--";
        if (d == Math.floor(d)) return String.format(Locale.getDefault(), "%.0f", d);
        return String.format(Locale.getDefault(), "%.1f", d);
    }
}

