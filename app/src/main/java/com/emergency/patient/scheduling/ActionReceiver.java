package com.emergency.patient.scheduling;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.emergency.patient.db.AdherenceLogDao;
import com.emergency.patient.db.AdherenceLogEntity;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.MedicationScheduleEntity;
import com.emergency.patient.network.FirebaseRepository;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ActionReceiver — Handles TAKEN / SNOOZED / DISMISSED actions from
 * medication reminder notifications.
 */
public class ActionReceiver extends BroadcastReceiver {

    private static final String TAG         = "ActionReceiver";
    private static final int    SNOOZE_MINS = 10;

    /** Broadcast sent after any TAKE / SNOOZE / DISMISS action so open screens refresh instantly. */
    public static final String ACTION_SCHEDULE_UPDATED = "com.emergency.patient.SCHEDULE_UPDATED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        // Immediately stop alarm audio
        MedicationAudioPlayer.stop();

        String action          = intent.getStringExtra("action");
        if (action == null && intent.getAction() != null) {
            if (intent.getAction().endsWith("TAKEN")) action = "TAKEN";
            else if (intent.getAction().endsWith("SNOOZED")) action = "SNOOZED";
            else if (intent.getAction().endsWith("DISMISSED")) action = "DISMISSED";
        }
        if (action == null) return;

        int    scheduleId      = intent.getIntExtra("scheduleId",    -1);
        int    notifId         = intent.getIntExtra("notifId",       -1);
        int    adherenceLogId  = intent.getIntExtra("adherenceLogId", -1);
        int    medicationId    = intent.getIntExtra("medicationId",   -1);
        String firestoreId     = intent.getStringExtra("firestoreId");
        String patientUid      = intent.getStringExtra("patientUid");
        long   scheduledMillis = intent.getLongExtra("scheduledMillis", 0L);
        int    snoozeCount     = intent.getIntExtra("snoozeCount",    0);
        String drugName        = intent.getStringExtra("drug");
        String imageUri        = intent.getStringExtra("imageUri");
        String dosage          = intent.getStringExtra("dosage");

        if (notifId <= 0) {
            notifId = scheduleId > 0 ? scheduleId : Math.abs((drugName != null ? drugName : "").hashCode() % 100000) + 1;
        }

        // 1. Immediately cancel the notification from status bar
        try {
            NotificationManagerCompat.from(context).cancel(notifId);
            if (scheduleId > 0 && scheduleId != notifId) {
                NotificationManagerCompat.from(context).cancel(scheduleId);
            }
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(notifId);
                if (scheduleId > 0 && scheduleId != notifId) {
                    nm.cancel(scheduleId);
                }
            }
        } catch (Exception ignored) {}

        // 2. Show instant toast feedback to user
        final String actionFinal = action;
        final String drugFinal = (drugName != null && !drugName.isEmpty()) ? drugName : "Medication";
        new Handler(Looper.getMainLooper()).post(() -> {
            if ("TAKEN".equalsIgnoreCase(actionFinal)) {
                Toast.makeText(context.getApplicationContext(), "✓ Marked " + drugFinal + " as taken!", Toast.LENGTH_SHORT).show();
            } else if ("SNOOZED".equalsIgnoreCase(actionFinal)) {
                Toast.makeText(context.getApplicationContext(), "⏰ " + drugFinal + " snoozed for 10 minutes", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context.getApplicationContext(), drugFinal + " marked as missed", Toast.LENGTH_SHORT).show();
            }
        });

        // 3. Process database update and adherence logging in background
        final String finalAction = action;
        new Thread(() -> {
            try {
                handleAction(context, finalAction, scheduleId, adherenceLogId,
                    medicationId, firestoreId, patientUid, scheduledMillis,
                    snoozeCount, drugName, imageUri, dosage);
            } catch (Exception e) {
                Log.e(TAG, "Error handling action: " + finalAction, e);
            }
        }).start();
    }

    private void handleAction(
            Context context,
            String action,
            int scheduleId,
            int adherenceLogId,
            int medicationId,
            String firestoreId,
            String patientUid,
            long scheduledMillis,
            int snoozeCount,
            String drugName,
            String imageUri,
            String dosage) {

        long now = System.currentTimeMillis();

        String scheduleStatus = "TAKEN".equalsIgnoreCase(action) ? "TAKEN" :
                ("SNOOZED".equalsIgnoreCase(action) ? "SNOOZED" : "MISSED");

        // 1. Update Room schedule row
        if (scheduleId != -1) {
            AppDatabaseProvider.getInstance(context)
                .scheduleDao().updateStatus(scheduleId, scheduleStatus);
        } else if (drugName != null && !drugName.isEmpty()) {
            try {
                Calendar c1 = Calendar.getInstance();
                c1.set(Calendar.HOUR_OF_DAY, 0); c1.set(Calendar.MINUTE, 0); c1.set(Calendar.SECOND, 0);
                Calendar c2 = Calendar.getInstance();
                c2.set(Calendar.HOUR_OF_DAY, 23); c2.set(Calendar.MINUTE, 59); c2.set(Calendar.SECOND, 59);
                List<MedicationScheduleEntity> list = AppDatabaseProvider.getInstance(context).scheduleDao().getByDrugName(drugName);
                for (MedicationScheduleEntity s : list) {
                    if (s.timeMillis >= c1.getTimeInMillis() && s.timeMillis <= c2.getTimeInMillis()) {
                        s.status = scheduleStatus;
                        AppDatabaseProvider.getInstance(context).scheduleDao().update(s);
                        scheduleId = s.id;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Upsert AdherenceLogEntity
        AdherenceLogDao logDao = AppDatabaseProvider.getInstance(context).adherenceLogDao();

        AdherenceLogEntity log = null;
        if (adherenceLogId != -1) {
            log = logDao.getById(adherenceLogId);
        }

        if (log == null) {
            log = new AdherenceLogEntity();
            log.medicationId          = medicationId;
            log.medicationFirestoreId = firestoreId != null ? firestoreId : "";
            log.medicationName        = (drugName != null) ? drugName : "";
            log.patientUid            = (patientUid != null) ? patientUid : "";
            log.scheduledTimeMillis   = scheduledMillis;
            log.snoozeCount           = 0;
        }

        log.actionType      = action;
        log.actedAt         = now;
        log.deviceTimestamp = now;

        if ("SNOOZED".equalsIgnoreCase(action)) {
            log.snoozeCount = snoozeCount + 1;
        }

        if (log.id == 0) {
            adherenceLogId = (int) logDao.insertLog(log);
            log.id = adherenceLogId;
        } else {
            logDao.updateAction(log.id, log.actionType, log.actedAt, log.snoozeCount);
        }

        // 3. Sync to Firestore (non-blocking)
        final AdherenceLogEntity finalLog = log;
        try {
            FirebaseRepository.getInstance(context).syncAdherenceLog(context, finalLog, null);
        } catch (Exception ignored) {}

        // 4. SNOOZE: schedule exact alarm + worker 10 minutes from now
        if ("SNOOZED".equalsIgnoreCase(action) && drugName != null && !drugName.isEmpty()) {
            long snoozeTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(SNOOZE_MINS);

            MedicationScheduleEntity snoozeSchedule = new MedicationScheduleEntity(drugName, snoozeTime, "PENDING");
            snoozeSchedule.id = scheduleId > 0 ? scheduleId : (int) (snoozeTime % 100000);
            MedicationReminderScheduler.scheduleDoseReminder(context, snoozeSchedule, null);

            Log.d(TAG, "Snoozed " + drugName + " for " + SNOOZE_MINS + " minutes");
        }

        // 5. Notify open screens to refresh immediately
        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(new Intent(ACTION_SCHEDULE_UPDATED));
        try {
            context.sendBroadcast(new Intent(ACTION_SCHEDULE_UPDATED));
        } catch (Exception ignored) {}

        Log.d(TAG, "Handled action=" + action + " drug=" + drugName);
    }
}
