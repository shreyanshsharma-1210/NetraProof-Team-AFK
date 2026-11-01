package com.emergency.patient.scheduling;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.MedicationEntity;

public class DoseAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "DoseAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        int scheduleId = intent.getIntExtra("scheduleId", -1);
        String drugName = intent.getStringExtra("drug");
        long scheduledMillis = intent.getLongExtra("scheduledTimeMillis", System.currentTimeMillis());

        if (scheduleId == -1 && drugName == null) return;

        Log.d(TAG, "DoseAlarmReceiver triggered for: " + drugName + " (scheduleId=" + scheduleId + ")");

        new Thread(() -> {
            try {
                // Ensure notification channels exist with sound & vibration
                NotificationHelper.createChannels(context);

                // Fetch drug details if available
                String dosage = "";
                String imageUri = "";
                int medicationId = -1;
                try {
                    MedicationEntity med = AppDatabaseProvider.getInstance(context).medicationDao().findByName(drugName);
                    if (med != null) {
                        dosage = med.dosage != null ? med.dosage : "";
                        imageUri = med.imageUri != null ? med.imageUri : "";
                        medicationId = med.id;
                    }
                } catch (Exception ignored) {}

                // Build full screen intent pointing at MedicationAlarmActivity
                Intent alarmIntent = new Intent(context, MedicationAlarmActivity.class);
                alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_DRUG_NAME, drugName);
                alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_SCHEDULE_ID, scheduleId);
                alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_MEDICATION_ID, medicationId);
                alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_SCHEDULED_MILLIS, scheduledMillis);
                alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_IMAGE_URI, imageUri);
                alarmIntent.putExtra(MedicationAlarmActivity.EXTRA_DOSAGE, dosage);

                PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                        context,
                        scheduleId > 0 ? scheduleId : (int) (System.currentTimeMillis() % 100000),
                        alarmIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                );

                NotificationHelper.showDoseReminderWithFullScreen(
                        context,
                        drugName != null ? drugName : "Medication",
                        dosage,
                        imageUri,
                        scheduleId,
                        -1,
                        medicationId,
                        "",
                        "",
                        scheduledMillis,
                        0,
                        fullScreenPendingIntent
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to fire dose notification", e);
            }
        }).start();
    }
}

