package com.emergency.patient.models.diabetes360;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * BloodPressureRecord — User-provided cardiovascular blood pressure reading.
 * Preserves systolic, diastolic, mmHg unit, timestamps, and data provenance.
 */
public class BloodPressureRecord implements Serializable {

    private String id;
    private String patientUuid;
    private int systolic;
    private int diastolic;
    private String unit = "mmHg";
    private long measurementDateMillis;
    private String source; // "Manual Entry", "Connected Monitor", "Clinical Report"
    private String notes;
    private long createdAtMillis;

    public BloodPressureRecord() {
        this.createdAtMillis = System.currentTimeMillis();
    }

    public BloodPressureRecord(String id, String patientUuid, int systolic, int diastolic,
                               long measurementDateMillis, String source, String notes) {
        this.id = id;
        this.patientUuid = patientUuid;
        this.systolic = systolic;
        this.diastolic = diastolic;
        this.unit = "mmHg";
        this.measurementDateMillis = measurementDateMillis;
        this.source = source != null ? source : "Manual Entry";
        this.notes = notes;
        this.createdAtMillis = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }

    public int getSystolic() { return systolic; }
    public void setSystolic(int systolic) { this.systolic = systolic; }

    public int getDiastolic() { return diastolic; }
    public void setDiastolic(int diastolic) { this.diastolic = diastolic; }

    public String getUnit() { return unit != null ? unit : "mmHg"; }
    public void setUnit(String unit) { this.unit = unit; }

    public long getMeasurementDateMillis() { return measurementDateMillis; }
    public void setMeasurementDateMillis(long measurementDateMillis) { this.measurementDateMillis = measurementDateMillis; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(long createdAtMillis) { this.createdAtMillis = createdAtMillis; }

    public String getFormattedReading() {
        return systolic + "/" + diastolic + " " + getUnit();
    }

    public String getFormattedDate() {
        if (measurementDateMillis <= 0) return "Date not recorded";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        return sdf.format(new Date(measurementDateMillis));
    }
}

