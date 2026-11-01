package com.emergency.patient.scheduling;

import android.content.Context;
import android.util.Log;

import com.emergency.patient.db.AdherenceLogDao;
import com.emergency.patient.db.AdherenceLogEntity;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.MedicationScheduleEntity;
import com.emergency.patient.db.ScheduleDao;
import com.emergency.patient.network.FirebaseRepository;

import java.util.Calendar;
import java.util.List;

/**
 * AdherenceStateValidator — Single source of truth for all adherence-state
 * transition rules when a medication reminder time is edited by the patient.
 *
 * Rules enforced:
 *
 *  RULE 1  TAKEN is immutable for the same-day occurrence.
 *          Edit is rejected with a human-readable reason.
 *
 *  RULE 2  MISSED → PENDING recovery.
 *          If the new time is in the future and the dose has not been TAKEN,
 *          the schedule row moves back to PENDING and the adherence log is
 *          updated in-place (no new row created) and re-synced to Firestore.
 *
 *  RULE 3  Future-day rows (status PENDING) are always editable freely —
 *          no special adherence action needed, WorkManager rescheduling only.
 *
 *  RULE 4  SNOOZED / DISMISSED rows on the current day behave like PENDING:
 *          the patient is allowed to pick a new time and get a fresh reminder.
 *
 * Must be called from a background thread — performs Room I/O.
 */
public final class AdherenceStateValidator {

    private static final String TAG = "AdherenceStateValidator";

    // ── Public result object ──────────────────────────────────────────────────

    public enum EditVerdict {
        /** Proceed: update time, reschedule WorkManager, sync Firestore. */
        ALLOWED,
        /** Proceed + recover MISSED → PENDING in Room and Firestore. */
        ALLOWED_MISSED_RECOVERY,
        /** Hard block: dose already TAKEN. Surface message to user. */
        BLOCKED_TAKEN,
        /** Hard block: new time is in the past (same-day edit). */
        BLOCKED_PAST_TIME
    }

    public static final class ValidationResult {
        public final EditVerdict verdict;
        public final String      message;

        ValidationResult(EditVerdict verdict, String message) {
            this.verdict = verdict;
            this.message = message;
        }
    }

    private AdherenceStateValidator() {}

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Validate whether the given schedule row may be edited to {@code newTimeMillis}.
     *
     * @param context       Application context (for Room access)
     * @param schedule      The MedicationScheduleEntity row being edited
     * @param newTimeMillis Proposed new reminder epoch millis chosen by the user
     * @return              {@link ValidationResult} with verdict + display message
     */
    public static ValidationResult validate(
            Context context,
            MedicationScheduleEntity schedule,
            long newTimeMillis) {

        String status = schedule.status != null ? schedule.status.toUpperCase() : "PENDING";
        long   now    = System.currentTimeMillis();

        // ── RULE 1: TAKEN is forever immutable for this occurrence ─────────────
        if ("TAKEN".equals(status)) {
            return new ValidationResult(
                EditVerdict.BLOCKED_TAKEN,
                "This dose has already been marked as taken and cannot be modified for today."
            );
        }

        // ── Cross-check: look up adherence log for this exact slot ─────────────
        // (The schedule row status is authoritative but the log may differ if
        //  ActionReceiver updated the log and Room was written out of order.)
        AdherenceLogEntity log = findAdherenceLog(context, schedule);
        if (log != null && "TAKEN".equals(log.actionType)) {
            return new ValidationResult(
                EditVerdict.BLOCKED_TAKEN,
                "This dose has already been marked as taken and cannot be modified for today."
            );
        }

        // ── RULE 2 (past-time guard): new time must be in the future ───────────
        // Only enforce for same-day edits; future-day times are always valid.
        if (isSameDay(schedule.timeMillis, now) && newTimeMillis <= now) {
            return new ValidationResult(
                EditVerdict.BLOCKED_PAST_TIME,
                "Please choose a future time for today's reminder."
            );
        }

        // ── RULE 2: MISSED → PENDING recovery ─────────────────────────────────
        if ("MISSED".equals(status) && newTimeMillis > now) {
            return new ValidationResult(
                EditVerdict.ALLOWED_MISSED_RECOVERY,
                "Reminder recovered: rescheduled to new time."
            );
        }

        // ── RULE 3/4: PENDING / SNOOZED / DISMISSED — freely editable ─────────
        return new ValidationResult(EditVerdict.ALLOWED, "");
    }

    /**
     * Apply the MISSED → PENDING recovery:
     *   1. Update Room schedule row status to PENDING
     *   2. Update the matching AdherenceLogEntity actionType to PENDING
     *   3. Re-sync the updated log to Firestore (update existing doc if synced,
     *      otherwise push as new to avoid duplicates)
     *
     * Must be called from a background thread.
     *
     * @param context       Application context
     * @param schedule      The schedule row (already updated with new timeMillis by caller)
     * @param patientUid    Firebase UID of the patient
     */
    public static void applyMissedRecovery(
            Context context,
            MedicationScheduleEntity schedule,
            String patientUid) {

        try {
            long now = System.currentTimeMillis();

            // 1. Stamp schedule row as PENDING
            AppDatabaseProvider.getInstance(context)
                .scheduleDao().updateStatus(schedule.id, "PENDING");
            Log.d(TAG, "MISSED→PENDING: scheduleId=" + schedule.id);

            // 2. Find and update adherence log
            AdherenceLogEntity log = findAdherenceLog(context, schedule);

            if (log != null) {
                // Update in place — preserve original actedAt and history
                log.actionType      = "PENDING";
                log.actedAt         = 0; // not yet acted on
                log.deviceTimestamp = now;
                // Update scheduledTimeMillis to the new time so future
                // de-dup checks work correctly
                log.scheduledTimeMillis = schedule.timeMillis;
                log.synced          = false; // force re-sync
                AppDatabaseProvider.getInstance(context)
                    .adherenceLogDao().updateLog(log);

                Log.d(TAG, "MISSED→PENDING: updated adherence log id=" + log.id);

                // 3. Re-sync to Firestore
                // Use update if we already have a firestoreLogId; otherwise push new
                final AdherenceLogEntity finalLog = log;
                if (log.firestoreLogId != null && !log.firestoreLogId.isEmpty()) {
                    FirebaseRepository.getInstance(context)
                        .updateAdherenceLog(context, finalLog, null);
                } else {
                    FirebaseRepository.getInstance(context)
                        .syncAdherenceLog(context, finalLog, null);
                }
            } else {
                // No existing log found — create a fresh PENDING entry
                AdherenceLogEntity newLog = new AdherenceLogEntity();
                newLog.patientUid           = patientUid != null ? patientUid : "";
                newLog.medicationId         = 0; // unknown at this point; best-effort
                newLog.medicationName       = schedule.drugName;
                newLog.scheduledTimeMillis  = schedule.timeMillis;
                newLog.actionType           = "PENDING";
                newLog.actedAt              = 0;
                newLog.deviceTimestamp      = now;
                newLog.synced               = false;

                long newId = AppDatabaseProvider.getInstance(context)
                    .adherenceLogDao().insertLog(newLog);
                newLog.id = (int) newId;

                FirebaseRepository.getInstance(context)
                    .syncAdherenceLog(context, newLog, null);

                Log.d(TAG, "MISSED→PENDING: created new adherence log id=" + newId);
            }
        } catch (Exception e) {
            Log.e(TAG, "applyMissedRecovery failed for scheduleId=" + schedule.id, e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Finds the adherence log whose scheduledTimeMillis most closely matches
     * the schedule row's original timeMillis (within a 60-second window to
     * tolerate any Calendar millisecond rounding).
     */
    static AdherenceLogEntity findAdherenceLog(
            Context context, MedicationScheduleEntity schedule) {
        try {
            AdherenceLogDao dao = AppDatabaseProvider.getInstance(context).adherenceLogDao();
            AdherenceLogEntity exact = dao.getByScheduledTime(schedule.timeMillis);
            if (exact != null) return exact;

            // Fuzzy fallback: ±60 seconds for any rounding drift
            List<AdherenceLogEntity> candidates =
                dao.getLogsForMedication(schedule.drugName);
            for (AdherenceLogEntity e : candidates) {
                if (Math.abs(e.scheduledTimeMillis - schedule.timeMillis) <= 60_000L) {
                    return e;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "findAdherenceLog failed", e);
        }
        return null;
    }

    private static boolean isSameDay(long millis1, long millis2) {
        Calendar c1 = Calendar.getInstance(); c1.setTimeInMillis(millis1);
        Calendar c2 = Calendar.getInstance(); c2.setTimeInMillis(millis2);
        return c1.get(Calendar.YEAR)        == c2.get(Calendar.YEAR)
            && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }
}
