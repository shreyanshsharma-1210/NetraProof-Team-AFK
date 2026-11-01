package com.emergency.patient.models.diabetes360;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Hba1cRecord — Patient or laboratory recorded Glycated Hemoglobin (HbA1c) test result.
 * Preserves supplied unit (% or mmol/mol), test date, and lab metadata.
 */
public class Hba1cRecord implements Serializable {

    private String id;
    private String patientUuid;
    private double value;
    private String unit; // "%" (default) or "mmol/mol"
    private long testDateMillis;
    private String source; // "Laboratory Report", "Self-Reported", "Doctor Consultation"
    private String labName;
    private String notes;
    private String attachmentPath;

    public Hba1cRecord() {
        this.unit = "%";
        this.testDateMillis = System.currentTimeMillis();
        this.source = "Laboratory Report";
    }

    public Hba1cRecord(String patientUuid, double value, String unit, long testDateMillis,
                       String source, String labName, String notes, String attachmentPath) {
        this.patientUuid = patientUuid;
        this.value = value;
        this.unit = (unit != null && !unit.trim().isEmpty()) ? unit.trim() : "%";
        this.testDateMillis = testDateMillis > 0 ? testDateMillis : System.currentTimeMillis();
        this.source = (source != null && !source.trim().isEmpty()) ? source.trim() : "Laboratory Report";
        this.labName = labName;
        this.notes = notes;
        this.attachmentPath = attachmentPath;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public String getUnit() { return unit != null ? unit : "%"; }
    public void setUnit(String unit) { this.unit = unit; }

    public long getTestDateMillis() { return testDateMillis; }
    public void setTestDateMillis(long testDateMillis) { this.testDateMillis = testDateMillis; }

    public String getSource() { return source != null ? source : "Laboratory Report"; }
    public void setSource(String source) { this.source = source; }

    public String getLabName() { return labName; }
    public void setLabName(String labName) { this.labName = labName; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getAttachmentPath() { return attachmentPath; }
    public void setAttachmentPath(String attachmentPath) { this.attachmentPath = attachmentPath; }

    public String getFormattedValue() {
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    public String getFormattedValueWithUnit() {
        return getFormattedValue() + (unit.equals("%") ? "%" : " " + unit);
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(testDateMillis));
    }
}

