package com.emergency.patient.models.diabetes360;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * FootCheckRecord — User-performed routine foot inspection record.
 * Tracks date, side, skin observation, user notes, and photo references.
 */
public class FootCheckRecord implements Serializable {

    private String id;
    private String patientUuid;
    private long checkDateMillis;
    private String side; // "Left Foot", "Right Foot", "Both Feet"
    private String skinCondition; // e.g. "Normal / Intact", "Dry / Cracked", "Callus / Corn"
    private String woundOrConcern; // e.g. "None observed", "Blister", "Skin Break"
    private String changesObserved;
    private String notes;
    private String photoPath;
    private String source; // "Self-Check", "Clinician Exam"
    private long createdAtMillis;

    public FootCheckRecord() {
        this.createdAtMillis = System.currentTimeMillis();
    }

    public FootCheckRecord(String id, String patientUuid, long checkDateMillis, String side,
                           String skinCondition, String woundOrConcern, String changesObserved,
                           String notes, String photoPath, String source) {
        this.id = id;
        this.patientUuid = patientUuid;
        this.checkDateMillis = checkDateMillis;
        this.side = side != null ? side : "Both Feet";
        this.skinCondition = skinCondition != null ? skinCondition : "Intact";
        this.woundOrConcern = woundOrConcern != null ? woundOrConcern : "None";
        this.changesObserved = changesObserved;
        this.notes = notes;
        this.photoPath = photoPath;
        this.source = source != null ? source : "Self-Check";
        this.createdAtMillis = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }

    public long getCheckDateMillis() { return checkDateMillis; }
    public void setCheckDateMillis(long checkDateMillis) { this.checkDateMillis = checkDateMillis; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public String getSkinCondition() { return skinCondition; }
    public void setSkinCondition(String skinCondition) { this.skinCondition = skinCondition; }

    public String getWoundOrConcern() { return woundOrConcern; }
    public void setWoundOrConcern(String woundOrConcern) { this.woundOrConcern = woundOrConcern; }

    public String getChangesObserved() { return changesObserved; }
    public void setChangesObserved(String changesObserved) { this.changesObserved = changesObserved; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(long createdAtMillis) { this.createdAtMillis = createdAtMillis; }

    public String getFormattedDate() {
        if (checkDateMillis <= 0) return "Date not recorded";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(checkDateMillis));
    }
}

