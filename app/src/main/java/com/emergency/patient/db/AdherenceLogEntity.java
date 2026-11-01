package com.emergency.patient.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * AdherenceLogEntity — Records every patient interaction with a medication reminder.
 *
 * Written locally on every TAKE / SNOOZE / DISMISS / MISSED action and
 * subsequently synced to Firestore at:  medication_logs/{logId}
 *
 * Supported statuses: TAKEN, SNOOZED, DISMISSED, MISSED, PENDING
 */
@Entity(tableName = "adherence_logs")
public class AdherenceLogEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Firebase UID of the patient. */
    @NonNull
    public String patientUid = "";

    /** Local Room medication primary key. */
    public int medicationId;

    /** Firestore document ID of the medication (may be null for OCR-only entries). */
    public String medicationFirestoreId;

    /** Display name of the medication (denormalised for log readability). */
    public String medicationName;

    /** Scheduled reminder time in millis. */
    public long scheduledTimeMillis;

    /** Action taken: TAKEN | SNOOZED | DISMISSED | MISSED | PENDING */
    @NonNull
    public String actionType = "PENDING";

    /** Actual timestamp (ms) when the patient acted on the notification. */
    public long actedAt;

    /** Number of times this specific dose slot has been snoozed. */
    public int snoozeCount;

    /** Device wall-clock millis at action time (useful for cross-timezone audits). */
    public long deviceTimestamp;

    /** Firestore document ID assigned after successful cloud sync. Null = not yet synced. */
    public String firestoreLogId;

    /** Whether this log entry has been successfully pushed to Firestore. */
    public boolean synced;

    public AdherenceLogEntity() {
        this.deviceTimestamp = System.currentTimeMillis();
        this.synced = false;
    }
}
