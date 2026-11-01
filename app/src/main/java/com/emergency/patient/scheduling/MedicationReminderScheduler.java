package com.emergency.patient.scheduling;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.emergency.patient.db.MedicationEntity;
import com.emergency.patient.db.MedicationScheduleEntity;

import java.util.concurrent.TimeUnit;

public class MedicationReminderScheduler {
    private static final String TAG = "MedReminderScheduler";

    /**
     * Schedules an exact AlarmManager alarm to ring at the exact time
     * plus a WorkManager fallback.
     */
    public static void scheduleDoseReminder(Context context, MedicationScheduleEntity schedule, MedicationEntity med) {
        if (schedule == null || schedule.timeMillis <= System.currentTimeMillis()) return;

        long triggerTime = schedule.timeMillis;
        String drugName = schedule.drugName != null ? schedule.drugName : (med != null ? med.name : "Medication");

        // 1. AlarmManager exact alarm
        try {
            AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmMgr != null) {
                Intent intent = new Intent(context, DoseAlarmReceiver.class);
                intent.putExtra("scheduleId", schedule.id);
                intent.putExtra("drug", drugName);
                intent.putExtra("scheduledTimeMillis", triggerTime);

                int requestCode = schedule.id > 0 ? schedule.id : (int) (triggerTime % 100000);
                PendingIntent pi = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                );

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
                } else {
                    alarmMgr.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pi);
                }
                Log.d(TAG, "Scheduled exact AlarmManager for " + drugName + " at " + triggerTime);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule AlarmManager exact alarm", e);
        }

        // 2. WorkManager fallback
        try {
            long delay = triggerTime - System.currentTimeMillis();
            if (delay > 0) {
                Data data = new Data.Builder()
                        .putString("drug", drugName)
                        .putInt("scheduleId", schedule.id)
                        .putInt("medicationId", med != null ? med.id : -1)
                        .putLong("scheduledTimeMillis", triggerTime)
                        .putString("dosage", med != null && med.dosage != null ? med.dosage : "")
                        .putString("imageUri", med != null && med.imageUri != null ? med.imageUri : "")
                        .build();

                OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(MedicationWorker.class)
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setInputData(data)
                        .addTag("dose_" + schedule.id)
                        .addTag("med_" + drugName)
                        .build();

                WorkManager.getInstance(context).enqueue(work);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule WorkManager fallback", e);
        }
    }

    public static void cancelDoseReminder(Context context, MedicationScheduleEntity schedule) {
        if (schedule == null) return;
        try {
            AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmMgr != null) {
                Intent intent = new Intent(context, DoseAlarmReceiver.class);
                int requestCode = schedule.id > 0 ? schedule.id : (int) (schedule.timeMillis % 100000);
                PendingIntent pi = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_NO_CREATE | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                );
                if (pi != null) {
                    alarmMgr.cancel(pi);
                    pi.cancel();
                }
            }
            WorkManager.getInstance(context).cancelAllWorkByTag("dose_" + schedule.id);
        } catch (Exception e) {
            Log.e(TAG, "Failed to cancel reminder", e);
        }
    }
}

