package com.emergency.patient.scheduling;

import android.content.Context;
import android.util.Log;

import com.emergency.patient.db.AdherenceLogEntity;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.network.FirebaseRepository;

import java.util.Calendar;
import java.util.List;

/**
 * MissedDoseHandler — Detects schedule slots whose reminder time has already
 * passed at the moment a medication is added or synced, and records them as
 * MISSED adherence entries instead of silently discarding them.
 *
 * Logic:
 *   For every dose time string (e.g. "09:00") on the current day:
 *     - If the computed Calendar time < System.currentTimeMillis()  →  MISSED
 *     - Otherwise the slot is in the future and is handled by WorkManager normally
 *
 *   A MISSED entry is:
 *     1. Inserted into the local Room adherence_logs table immediately
 *     2. Queued for Firestore sync so guardians can see it
 *     3. The corresponding MedicationScheduleEntity row is stamped MISSED
 *
 * Safety rules:
 *   - Never fires a notification for a MISSED slot (avoids spam on first launch)
 *   - De-duplicates: checks whether an adherence log for this (medicationId,
 *     scheduledTimeMillis) already exists before inserting a second one
 *   - Never throws — all errors are caught and logged
 *   - Must be called from a background thread (Room access)
 */
public final class MissedDoseHandler {

    private static final String TAG = "MissedDoseHandler";

    private MissedDoseHandler() {}

    /**
     * Inspect all dose times for today and record any that are already in the
     * past as MISSED adherence log entries.
     *
     * @param context         Application context
     * @param medicationId    Local Room MedicationEntity.id
     * @param firestoreId     Firestore medication document ID (may be null/empty)
     * @param medicationName  Display name of the medication
     * @param dosage          Dosage string (parsed for times via DosageParser)
     * @param patientUid      Firebase UID of the patient
     */
    public static void recordMissedSlotsForToday(
            Context context,
            int medicationId,
            String firestoreId,
            String medicationName,
            String dosage,
            String patientUid) {

        if (medicationName == null || patientUid == null) return;

        ScheduleData schedule = DosageParser.parse(dosage);
        if (schedule == null || schedule.times == null || schedule.times.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (String timeStr : schedule.times) {
            try {
                String[] parts = timeStr.split(":");
                int hour   = Integer.parseInt(parts[0].trim());
                int minute = Integer.parseInt(parts[1].trim());

                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                long slotMillis = cal.getTimeInMillis();

                // Only mark MISSED if the slot was today AND already passed
                if (slotMillis >= now) continue;

                // De-duplicate: skip if we already have a log for this slot
                if (logAlreadyExists(context, medicationId, slotMillis)) {
                    Log.d(TAG, "Adherence log already exists for " + medicationName
                        + " at " + timeStr + " — skipping");
                    continue;
                }

                // Insert MISSED adherence log
                AdherenceLogEntity log = new AdherenceLogEntity();
                log.patientUid            = patientUid;
                log.medicationId          = medicationId;
                log.medicationFirestoreId = (firestoreId != null) ? firestoreId : "";
                log.medicationName        = medicationName;
                log.scheduledTimeMillis   = slotMillis;
                log.actionType            = "MISSED";
                log.actedAt               = now;
                log.deviceTimestamp       = now;
                log.snoozeCount           = 0;
                log.synced                = false;

                long logId = AppDatabaseProvider.getInstance(context)
                    .adherenceLogDao().insertLog(log);
                log.id = (int) logId;

                Log.d(TAG, "Recorded MISSED dose: " + medicationName
                    + " scheduled=" + timeStr + " logId=" + logId);

                // Update corresponding schedule row to MISSED (best-effort, by time match)
                markScheduleRowMissed(context, medicationName, slotMillis);

                // Sync to Firestore (non-blocking; retried later if offline)
                final AdherenceLogEntity finalLog = log;
                FirebaseRepository.getInstance(context)
                    .syncAdherenceLog(context, finalLog, null);

            } catch (Exception e) {
                Log.e(TAG, "Error processing missed slot for time=" + timeStr, e);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns true if an adherence log already exists for this medication at
     * exactly this scheduled time. Prevents duplicate MISSED entries on
     * repeated calls (app restart, re-sync).
     */
    private static boolean logAlreadyExists(Context context, int medicationId, long scheduledMillis) {
        try {
            List<AdherenceLogEntity> existing =
                AppDatabaseProvider.getInstance(context)
                    .adherenceLogDao().getLogsForMedication(medicationId);
            for (AdherenceLogEntity e : existing) {
                if (e.scheduledTimeMillis == scheduledMillis) return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "De-dup check failed", e);
        }
        return false;
    }

    /**
     * Stamps the MedicationScheduleEntity row whose drugName + timeMillis match
     * this slot as MISSED. Does nothing if no row is found.
     */
    private static void markScheduleRowMissed(Context context, String drugName, long slotMillis) {
        try {
            com.emergency.patient.db.MedicationScheduleEntity row =
                AppDatabaseProvider.getInstance(context)
                    .scheduleDao().getByDrugNameAndTime(drugName, slotMillis);
            if (row != null) {
                AppDatabaseProvider.getInstance(context)
                    .scheduleDao().updateStatus(row.id, "MISSED");
            }
        } catch (Exception e) {
            Log.e(TAG, "markScheduleRowMissed failed for " + drugName, e);
        }
    }
}
