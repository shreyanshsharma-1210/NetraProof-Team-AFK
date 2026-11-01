package com.emergency.patient.models.diabetes360;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * SymptomRecord — User-reported neurological / podiatric symptom.
 *
 * Supports Numbness, Tingling, Pain, Burning sensation, or custom symptoms.
 * Tracks location, side, status (Active, Improved, Resolved), and date.
 * Does not assert an unverified diagnostic claim of diabetic neuropathy.
 */
public class SymptomRecord implements Serializable {

    public enum SymptomType {
        NUMBNESS("Numbness / Loss of Sensation"),
        TINGLING("Tingling / Pins & Needles"),
        BURNING("Burning Sensation"),
        PAIN("Foot / Nerve Pain"),
        COLD_FEET("Persistent Cold Sensation"),
        OTHER("Other Symptom");

        private final String displayName;

        SymptomType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private String id;
    private String patientUuid;
    private SymptomType symptomType;
    private String bodyLocation; // e.g. "Toes", "Sole", "Heel", "Both feet"
    private String side; // "Left", "Right", "Bilateral"
    private long dateMillis;
    private String userDescription;
    private String status; // "Active", "Improving", "Resolved"
    private String notes;
    private long createdAtMillis;

    public SymptomRecord() {
        this.createdAtMillis = System.currentTimeMillis();
    }

    public SymptomRecord(String id, String patientUuid, SymptomType symptomType, String bodyLocation,
                         String side, long dateMillis, String userDescription, String status, String notes) {
        this.id = id;
        this.patientUuid = patientUuid;
        this.symptomType = symptomType;
        this.bodyLocation = bodyLocation;
        this.side = side != null ? side : "Bilateral";
        this.dateMillis = dateMillis;
        this.userDescription = userDescription;
        this.status = status != null ? status : "Active";
        this.notes = notes;
        this.createdAtMillis = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }

    public SymptomType getSymptomType() { return symptomType; }
    public void setSymptomType(SymptomType symptomType) { this.symptomType = symptomType; }

    public String getBodyLocation() { return bodyLocation != null ? bodyLocation : "Feet"; }
    public void setBodyLocation(String bodyLocation) { this.bodyLocation = bodyLocation; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public long getDateMillis() { return dateMillis; }
    public void setDateMillis(long dateMillis) { this.dateMillis = dateMillis; }

    public String getUserDescription() { return userDescription; }
    public void setUserDescription(String userDescription) { this.userDescription = userDescription; }

    public String getStatus() { return status != null ? status : "Active"; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(long createdAtMillis) { this.createdAtMillis = createdAtMillis; }

    public String getFormattedDate() {
        if (dateMillis <= 0) return "Date not recorded";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(dateMillis));
    }
}

