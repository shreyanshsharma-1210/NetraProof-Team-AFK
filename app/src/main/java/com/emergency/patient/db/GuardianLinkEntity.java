package com.emergency.patient.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * GuardianLinkEntity — Represents a guardian-patient relationship.
 *
 * Stored locally so the app knows which guardian accounts have write
 * permission to this patient's medication list.
 *
 * Firestore mirror: guardian_links/{linkId}
 *
 * Supported permissions (comma-separated in permissionsJson):
 *   MEDICATION_READ, MEDICATION_WRITE, ADHERENCE_VIEW
 */
@Entity(tableName = "guardian_links")
public class GuardianLinkEntity {

    @PrimaryKey
    @NonNull
    public String linkId = "";

    /** Firebase UID of the guardian. */
    @NonNull
    public String guardianUid = "";

    /** Firebase UID of the patient this guardian is linked to. */
    @NonNull
    public String patientUid = "";

    /** Comma-separated permission tokens, e.g. "MEDICATION_READ,MEDICATION_WRITE". */
    public String permissionsJson;

    /** Unix timestamp (ms) when this link was created. */
    public long createdAt;

    /** Whether this link is currently active. */
    public boolean active;

    public GuardianLinkEntity() {
        this.createdAt = System.currentTimeMillis();
        this.active = true;
    }

    public boolean hasPermission(String permission) {
        if (permissionsJson == null || permissionsJson.isEmpty()) return false;
        return permissionsJson.contains(permission);
    }
}
