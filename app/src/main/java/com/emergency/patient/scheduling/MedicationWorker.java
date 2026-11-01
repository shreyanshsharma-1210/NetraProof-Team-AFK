package com.emergency.patient.scheduling;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * MedicationWorker — WorkManager worker that fires a dose reminder notification.
 *
 * Reads all sync-aware fields from InputData and delegates to
 * NotificationHelper.showDoseReminder so that TAKE / SNOOZE / DISMISS
 * actions have full context for adherence logging.
 */
public class MedicationWorker extends Worker {

    public MedicationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String drugName        = getInputData().getString("drug");
        int    scheduleId      = getInputData().getInt("scheduleId",        -1);
        int    adherenceLogId  = getInputData().getInt("adherenceLogId",    -1);
        int    medicationId    = getInputData().getInt("medicationId",      -1);
        String firestoreId     = getInputData().getString("firestoreId");
        String patientUid      = getInputData().getString("patientUid");
        long   scheduledMillis = getInputData().getLong("scheduledTimeMillis", 0L);
        String imageUri        = getInputData().getString("imageUri");
        String dosage          = getInputData().getString("dosage");
        int    snoozeCount     = getInputData().getInt("snoozeCount",       0);

        if (drugName == null || scheduleId == -1) return Result.failure();

        // Update local schedule status to PENDING (re-arm for this slot)
        com.emergency.patient.db.AppDatabaseProvider.getInstance(getApplicationContext())
            .scheduleDao().updateStatus(scheduleId, "PENDING");

        // ── Build full-screen intent pointing at MedicationAlarmActivity ──────────
        // When user taps the notification, this intent launches the popup activity.
        Intent alarmIntent = new Intent(getApplicationContext(), MedicationAlarmActivity.class);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_DRUG_NAME,        drugName);
        alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_SCHEDULE_ID,      scheduleId);
        alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_ADHERENCE_LOG_ID, adherenceLogId);
        alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_MEDICATION_ID,    medicationId);
        alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_FIRESTORE_ID,     firestoreId  != null ? firestoreId  : "");
        alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_PATIENT_UID,      patientUid   != null ? patientUid   : "");
        alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_SCHEDULED_MILLIS, scheduledMillis);
        alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_SNOOZE_COUNT,     snoozeCount);
        alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_IMAGE_URI,        imageUri     != null ? imageUri     : "");
        alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_DOSAGE,           dosage       != null ? dosage       : "");

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
            getApplicationContext(),
            scheduleId,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // ── Show notification with full-screen intent ────────────────────────────
        // Notification plays the custom sound via the channel.
        // Full-screen intent launches the popup when the notification is tapped.
        NotificationHelper.showDoseReminderWithFullScreen(
            getApplicationContext(),
            drugName,
            dosage      != null ? dosage      : "",
            imageUri,
            scheduleId,
            adherenceLogId,
            medicationId,
            firestoreId != null ? firestoreId  : "",
            patientUid  != null ? patientUid   : "",
            scheduledMillis,
            snoozeCount,
            fullScreenPendingIntent
        );

        return Result.success();
    }
}
