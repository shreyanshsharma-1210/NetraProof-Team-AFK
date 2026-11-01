package com.emergency.patient.models.diabetes360;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Diabetes360Data — Master container for patient-specific Diabetes360 records.
 * Serialized atomically to isolated internal patient storage.
 */
public class Diabetes360Data implements Serializable {

    private String patientUuid;
    private List<KidneyRecord> kidneyRecords = new ArrayList<>();
    private List<BloodPressureRecord> bloodPressureRecords = new ArrayList<>();
    private List<LipidProfileRecord> lipidProfileRecords = new ArrayList<>();
    private List<FootCheckRecord> footCheckRecords = new ArrayList<>();
    private List<SymptomRecord> symptomRecords = new ArrayList<>();
    private List<FootConcernRecord> footConcernRecords = new ArrayList<>();
    private List<GlucoseRecord> glucoseRecords = new ArrayList<>();
    private List<Hba1cRecord> hba1cRecords = new ArrayList<>();
    private List<HealthReminder> reminders = new ArrayList<>();
    private DiabetesContext diabetesContext = new DiabetesContext();
    private long lastModifiedMillis = System.currentTimeMillis();

    public Diabetes360Data() {}

    public Diabetes360Data(String patientUuid) {
        this.patientUuid = patientUuid;
        this.diabetesContext = new DiabetesContext();
        this.diabetesContext.setPatientUuid(patientUuid);
    }

    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }

    public List<KidneyRecord> getKidneyRecords() {
        if (kidneyRecords == null) kidneyRecords = new ArrayList<>();
        return kidneyRecords;
    }
    public void setKidneyRecords(List<KidneyRecord> kidneyRecords) { this.kidneyRecords = kidneyRecords; }

    public List<BloodPressureRecord> getBloodPressureRecords() {
        if (bloodPressureRecords == null) bloodPressureRecords = new ArrayList<>();
        return bloodPressureRecords;
    }
    public void setBloodPressureRecords(List<BloodPressureRecord> bloodPressureRecords) { this.bloodPressureRecords = bloodPressureRecords; }

    public List<LipidProfileRecord> getLipidProfileRecords() {
        if (lipidProfileRecords == null) lipidProfileRecords = new ArrayList<>();
        return lipidProfileRecords;
    }
    public void setLipidProfileRecords(List<LipidProfileRecord> lipidProfileRecords) { this.lipidProfileRecords = lipidProfileRecords; }

    public List<FootCheckRecord> getFootCheckRecords() {
        if (footCheckRecords == null) footCheckRecords = new ArrayList<>();
        return footCheckRecords;
    }
    public void setFootCheckRecords(List<FootCheckRecord> footCheckRecords) { this.footCheckRecords = footCheckRecords; }

    public List<SymptomRecord> getSymptomRecords() {
        if (symptomRecords == null) symptomRecords = new ArrayList<>();
        return symptomRecords;
    }
    public void setSymptomRecords(List<SymptomRecord> symptomRecords) { this.symptomRecords = symptomRecords; }

    public List<FootConcernRecord> getFootConcernRecords() {
        if (footConcernRecords == null) footConcernRecords = new ArrayList<>();
        return footConcernRecords;
    }
    public void setFootConcernRecords(List<FootConcernRecord> footConcernRecords) { this.footConcernRecords = footConcernRecords; }

    public List<GlucoseRecord> getGlucoseRecords() {
        if (glucoseRecords == null) glucoseRecords = new ArrayList<>();
        return glucoseRecords;
    }
    public void setGlucoseRecords(List<GlucoseRecord> glucoseRecords) { this.glucoseRecords = glucoseRecords; }

    public List<Hba1cRecord> getHba1cRecords() {
        if (hba1cRecords == null) hba1cRecords = new ArrayList<>();
        return hba1cRecords;
    }
    public void setHba1cRecords(List<Hba1cRecord> hba1cRecords) { this.hba1cRecords = hba1cRecords; }

    public List<HealthReminder> getReminders() {
        if (reminders == null) reminders = new ArrayList<>();
        return reminders;
    }
    public void setReminders(List<HealthReminder> reminders) { this.reminders = reminders; }

    public DiabetesContext getDiabetesContext() {
        if (diabetesContext == null) diabetesContext = new DiabetesContext();
        return diabetesContext;
    }
    public void setDiabetesContext(DiabetesContext diabetesContext) { this.diabetesContext = diabetesContext; }

    public long getLastModifiedMillis() { return lastModifiedMillis; }
    public void setLastModifiedMillis(long lastModifiedMillis) { this.lastModifiedMillis = lastModifiedMillis; }
}

