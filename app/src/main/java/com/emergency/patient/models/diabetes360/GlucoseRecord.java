package com.emergency.patient.models.diabetes360;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * GlucoseRecord — Patient-recorded blood glucose measurement.
 * Strictly preserves value, unit, context, source, and timestamps without synthetic estimation.
 */
public class GlucoseRecord implements Serializable {

    public enum ReadingContext {
        FASTING("Fasting (Before Breakfast)"),
        BEFORE_MEAL("Before Meal"),
        AFTER_MEAL("After Meal (Postprandial)"),
        RANDOM("Random"),
        BEDTIME("Bedtime"),
        OTHER("Other / Not Specified");

        private final String displayName;

        ReadingContext(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static ReadingContext fromString(String val) {
            if (val == null) return OTHER;
            for (ReadingContext c : values()) {
                if (c.name().equalsIgnoreCase(val) || c.displayName.equalsIgnoreCase(val)) {
                    return c;
                }
            }
            if (val.toLowerCase(Locale.ROOT).contains("fast")) return FASTING;
            if (val.toLowerCase(Locale.ROOT).contains("post") || val.toLowerCase(Locale.ROOT).contains("after")) return AFTER_MEAL;
            if (val.toLowerCase(Locale.ROOT).contains("before")) return BEFORE_MEAL;
            if (val.toLowerCase(Locale.ROOT).contains("bed")) return BEDTIME;
            return RANDOM;
        }
    }

    private String id;
    private String patientUuid;
    private double value;
    private String unit; // "mg/dL" (default) or "mmol/L"
    private long dateMillis;
    private String measurementTime; // e.g. "08:30 AM"
    private ReadingContext readingContext;
    private String source; // e.g., "Patient Log", "Glucometer Sync", "Lab Report"
    private String notes;

    public GlucoseRecord() {
        this.unit = "mg/dL";
        this.readingContext = ReadingContext.RANDOM;
        this.dateMillis = System.currentTimeMillis();
        this.source = "Patient Log";
    }

    public GlucoseRecord(String patientUuid, double value, String unit, long dateMillis,
                         String measurementTime, ReadingContext readingContext, String source, String notes) {
        this.patientUuid = patientUuid;
        this.value = value;
        this.unit = (unit != null && !unit.trim().isEmpty()) ? unit.trim() : "mg/dL";
        this.dateMillis = dateMillis > 0 ? dateMillis : System.currentTimeMillis();
        this.measurementTime = measurementTime;
        this.readingContext = readingContext != null ? readingContext : ReadingContext.RANDOM;
        this.source = (source != null && !source.trim().isEmpty()) ? source.trim() : "Patient Log";
        this.notes = notes;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public String getUnit() { return unit != null ? unit : "mg/dL"; }
    public void setUnit(String unit) { this.unit = unit; }

    public long getDateMillis() { return dateMillis; }
    public void setDateMillis(long dateMillis) { this.dateMillis = dateMillis; }

    public String getMeasurementTime() { return measurementTime; }
    public void setMeasurementTime(String measurementTime) { this.measurementTime = measurementTime; }

    public ReadingContext getReadingContext() { return readingContext != null ? readingContext : ReadingContext.RANDOM; }
    public void setReadingContext(ReadingContext readingContext) { this.readingContext = readingContext; }

    public String getSource() { return source != null ? source : "Patient Log"; }
    public void setSource(String source) { this.source = source; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getFormattedValue() {
        if (value == (long) value) {
            return String.format(Locale.getDefault(), "%d", (long) value);
        } else {
            return String.format(Locale.getDefault(), "%.1f", value);
        }
    }

    public String getFormattedValueWithUnit() {
        return getFormattedValue() + " " + getUnit();
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(dateMillis));
    }

    public String getFormattedTimestamp() {
        String dateStr = getFormattedDate();
        if (measurementTime != null && !measurementTime.trim().isEmpty()) {
            return dateStr + ", " + measurementTime;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return dateStr + ", " + sdf.format(new Date(dateMillis));
    }
}

