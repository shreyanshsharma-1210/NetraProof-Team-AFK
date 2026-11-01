package com.emergency.patient.models.diabetes360;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * FootConcernRecord — User-recorded foot concern (wound, blister, skin break, swelling).
 * Not an AI diagnosis; preserves original self-observation and photo references.
 */
public class FootConcernRecord implements Serializable {

    public enum ConcernType {
        WOUND("Skin Wound / Ulcer"),
        BLISTER("Blister / Friction Sore"),
        SKIN_BREAK("Cut / Crack / Fissure"),
        SWELLING("Localized Swelling / Edema"),
        REDNESS("Localized Redness / Warmth"),
        OTHER("Other Foot Concern");

        private final String displayName;

        ConcernType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private String id;
    private String patientUuid;
    private ConcernType concernType;
    private long dateNoticedMillis;
    private String side; // "Left Foot", "Right Foot", "Both"
    private String description;
    private String photoPath;
    private String notes;
    private String status; // "Active", "Healing", "Resolved", "Evaluated by Doctor"
    private long createdAtMillis;

    public FootConcernRecord() {
        this.createdAtMillis = System.currentTimeMillis();
    }

    public FootConcernRecord(String id, String patientUuid, ConcernType concernType, long dateNoticedMillis,
                             String side, String description, String photoPath, String notes, String status) {
        this.id = id;
        this.patientUuid = patientUuid;
        this.concernType = concernType;
        this.dateNoticedMillis = dateNoticedMillis;
        this.side = side != null ? side : "Right Foot";
        this.description = description;
        this.photoPath = photoPath;
        this.notes = notes;
        this.status = status != null ? status : "Active";
        this.createdAtMillis = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPatientUuid() { return patientUuid; }
    public void setPatientUuid(String patientUuid) { this.patientUuid = patientUuid; }

    public ConcernType getConcernType() { return concernType; }
    public void setConcernType(ConcernType concernType) { this.concernType = concernType; }

    public long getDateNoticedMillis() { return dateNoticedMillis; }
    public void setDateNoticedMillis(long dateNoticedMillis) { this.dateNoticedMillis = dateNoticedMillis; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getStatus() { return status != null ? status : "Active"; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(long createdAtMillis) { this.createdAtMillis = createdAtMillis; }

    public String getFormattedDate() {
        if (dateNoticedMillis <= 0) return "Date not recorded";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(dateNoticedMillis));
    }
}

