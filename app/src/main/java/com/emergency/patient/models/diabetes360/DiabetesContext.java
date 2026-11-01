package com.emergency.patient.models.diabetes360;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * DiabetesContext — User-provided optional medical background for diabetes self-management.
 * Covers voluntary diabetes classification, duration, and doctor check-up dates.
 */
public class DiabetesContext implements Serializable {

    private String patientUuid;
    private String diabetesType; // e.g. "Type 2 Diabetes", "Type 1 Diabetes", "Prediabetes", "Gestational", "Not specified"
    private int durationYears;
    private long lastCheckupMillis;
    private String relevantHistoryNotes;
    private long lastUpdatedMillis;

    public DiabetesContext() {
        this.diabetesType = "Not specified";
        this.durationYears = 0;
        this.lastCheckupMillis = 0;
        this.relevantHistoryNotes = "";
        this.lastUpdatedMillis = System.currentTimeMillis();
    }

    public DiabetesContext(String patientUuid, String diabetesType, int durationYears,
                           long lastCheckupMillis, String relevantHistoryNotes) {
        this.patientUuid = patientUuid;
        this.diabetesType = diabetesType != null ? diabetesType : "Not specified";
        this.durationYears = durationYears;
        this.lastCheckupMillis = lastCheckupMillis;
        this.relevantHistoryNotes = relevantHistoryNotes;
        this.lastUpdatedMillis = System.currentTimeMillis();
    }

    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }

    public String getDiabetesType() { return diabetesType != null ? diabetesType : "Not specified"; }
    public void setDiabetesType(String diabetesType) { this.diabetesType = diabetesType; }

    public int getDurationYears() { return durationYears; }
    public void setDurationYears(int durationYears) { this.durationYears = durationYears; }

    public long getLastCheckupMillis() { return lastCheckupMillis; }
    public void setLastCheckupMillis(long lastCheckupMillis) { this.lastCheckupMillis = lastCheckupMillis; }

    public String getRelevantHistoryNotes() { return relevantHistoryNotes != null ? relevantHistoryNotes : ""; }
    public void setRelevantHistoryNotes(String relevantHistoryNotes) { this.relevantHistoryNotes = relevantHistoryNotes; }

    public long getLastUpdatedMillis() { return lastUpdatedMillis; }
    public void setLastUpdatedMillis(long lastUpdatedMillis) { this.lastUpdatedMillis = lastUpdatedMillis; }

    public String getFormattedLastCheckup() {
        if (lastCheckupMillis <= 0) return "Not recorded";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(lastCheckupMillis));
    }
}

