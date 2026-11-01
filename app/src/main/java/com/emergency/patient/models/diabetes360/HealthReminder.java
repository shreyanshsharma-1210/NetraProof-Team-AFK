package com.emergency.patient.models.diabetes360;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * HealthReminder — User-scheduled health task or appointment reminder.
 * Covers kidney labs, blood pressure checks, foot inspections, glucose logs, or retinal screenings.
 */
public class HealthReminder implements Serializable {

    public enum Category {
        KIDNEY("Kidney Health"),
        HEART("Heart & Vascular"),
        FOOT("Foot & Nerve Care"),
        GLUCOSE("Glucose / HbA1c"),
        RETINA("Retinal Screening");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private String id;
    private String patientUuid;
    private Category category;
    private String title;
    private long targetDateMillis;
    private String frequency; // "One-Time", "Daily", "Weekly", "Monthly", "Every 6 Months"
    private String notes;
    private boolean isCompleted;
    private long createdAtMillis;

    public HealthReminder() {
        this.createdAtMillis = System.currentTimeMillis();
    }

    public HealthReminder(String id, String patientUuid, Category category, String title,
                          long targetDateMillis, String frequency, String notes) {
        this.id = id;
        this.patientUuid = patientUuid;
        this.category = category;
        this.title = title;
        this.targetDateMillis = targetDateMillis;
        this.frequency = frequency != null ? frequency : "One-Time";
        this.notes = notes;
        this.isCompleted = false;
        this.createdAtMillis = System.currentTimeMillis();
    }

    public HealthReminder(Category category, String title, long targetDateMillis, String frequency, String notes) {
        this("REM-" + java.util.UUID.randomUUID().toString().substring(0, 8), null, category, title, targetDateMillis, frequency, notes);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public long getTargetDateMillis() { return targetDateMillis; }
    public void setTargetDateMillis(long targetDateMillis) { this.targetDateMillis = targetDateMillis; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }

    public long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(long createdAtMillis) { this.createdAtMillis = createdAtMillis; }

    public String getFormattedDueDate() {
        if (targetDateMillis <= 0) return "Date not set";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(targetDateMillis));
    }
}

