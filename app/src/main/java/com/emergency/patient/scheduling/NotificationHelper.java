package com.emergency.patient.scheduling;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.emergency.patient.R;

public class NotificationHelper {

    /**
     * Versioned channel ID for medication dose reminders.
     *
     * Android persists channel settings (sound, vibration) after creation and
     * will NOT update them if the channel already exists with the same ID.
     * Incrementing the suffix (v2, v3…) forces a new channel with fresh settings,
     * ensuring the custom MP3 sound is always correctly attached.
     *
     * Old channel "med_reminder_channel" is deleted on createChannels() to keep
     * the Settings UI clean.
     */
    public static final String MED_REMINDER_CHANNEL_ID = "med_reminder_channel_v6";

    /** Legacy expiry channel — no custom sound change needed. */
    private static final String MED_EXPIRY_CHANNEL_ID = "med_channel";

    /** Obsolete channel IDs — deleted on channel creation to avoid Settings clutter. */
    private static final String MED_REMINDER_CHANNEL_ID_V1 = "med_reminder_channel";
    private static final String MED_REMINDER_CHANNEL_ID_V2 = "med_reminder_channel_v2";
    private static final String MED_REMINDER_CHANNEL_ID_V3 = "med_reminder_channel_v3";
    private static final String MED_REMINDER_CHANNEL_ID_V4 = "med_reminder_channel_v4";

    // ── Channel Bootstrap ──────────────────────────────────────────────────────

    /**
     * Creates all notification channels. Safe to call repeatedly.
     */
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager mgr = context.getSystemService(NotificationManager.class);
        if (mgr == null) return;

        // Delete obsolete channels so they vanish from Settings
        mgr.deleteNotificationChannel(MED_REMINDER_CHANNEL_ID_V1);
        mgr.deleteNotificationChannel(MED_REMINDER_CHANNEL_ID_V2);
        mgr.deleteNotificationChannel(MED_REMINDER_CHANNEL_ID_V3);
        mgr.deleteNotificationChannel(MED_REMINDER_CHANNEL_ID_V4);
        mgr.deleteNotificationChannel("med_reminder_channel_v5");
        mgr.deleteNotificationChannel("emergency_service_channel");

        // ── Dose Reminder Channel (custom MP3 sound with USAGE_ALARM) ─────────
        Uri soundUri = Uri.parse(
            "android.resource://" + context.getPackageName() + "/" + R.raw.medication_reminder
        );
        android.util.Log.d("NotificationHelper", "Sound URI: " + soundUri);
        AudioAttributes audioAttr = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
            .build();

        NotificationChannel reminderChannel = new NotificationChannel(
            MED_REMINDER_CHANNEL_ID,
            "Medication Dose Reminders",
            NotificationManager.IMPORTANCE_HIGH
        );
        reminderChannel.setDescription("Alerts you when it is time to take your medicine");
        reminderChannel.enableVibration(true);
        reminderChannel.setVibrationPattern(new long[]{0, 400, 200, 400});
        reminderChannel.setSound(null, null);
        reminderChannel.setShowBadge(true);
        reminderChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        mgr.createNotificationChannel(reminderChannel);

        // ── Expiry Alert Channel (default sound) ─────────────────────────────
        NotificationChannel expiryChannel = new NotificationChannel(
            MED_EXPIRY_CHANNEL_ID,
            "Medication Expiry Alerts",
            NotificationManager.IMPORTANCE_HIGH
        );
        expiryChannel.setDescription("Notifies when a medication is about to expire");
        mgr.createNotificationChannel(expiryChannel);
    }

    // ── Dose Reminder Notification ─────────────────────────────────────────────

    /**
     * Full-featured dose reminder with medicine image (BigPictureStyle), dosage
     * instructions, custom MP3 sound, and TAKE / SNOOZE / DISMISS actions.
     *
     * This method MUST be called from a background thread because it loads
     * the medicine bitmap synchronously via NotificationImageLoader.
     *
     * @param context          Application context
     * @param drugName         Display name of the medication
     * @param dosage           Dosage string e.g. "500mg twice daily" (shown in body)
     * @param imageUri         Local image path/URI from MedicationEntity.imageUri (may be null)
     * @param scheduleId       Room MedicationScheduleEntity PK (used as notification ID)
     * @param adherenceLogId   Local AdherenceLogEntity PK passed to ActionReceiver
     * @param medicationId     Local MedicationEntity PK
     * @param firestoreId      Firestore medication doc ID (empty for OCR-only records)
     * @param patientUid       Firebase UID of the patient
     * @param scheduledMillis  Epoch millis of the scheduled dose time
     * @param snoozeCount      Number of times this slot has already been snoozed
     */
    public static void showDoseReminder(
            Context context,
            String drugName,
            String dosage,
            String imageUri,
            int scheduleId,
            int adherenceLogId,
            int medicationId,
            String firestoreId,
            String patientUid,
            long scheduledMillis,
            int snoozeCount) {

        if (!hasNotificationPermission(context)) return;
        createChannels(context);

        int notifId = scheduleId > 0 ? scheduleId : Math.abs((drugName != null ? drugName : "").hashCode() % 100000) + 1;
        int baseReq = notifId * 10;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        PendingIntent takenPending = PendingIntent.getBroadcast(
            context, baseReq,
            buildActionIntent(context, "TAKEN", scheduleId, adherenceLogId,
                medicationId, firestoreId, patientUid, scheduledMillis, snoozeCount, drugName, imageUri, dosage, notifId),
            flags);

        PendingIntent snoozePending = PendingIntent.getBroadcast(
            context, baseReq + 1,
            buildActionIntent(context, "SNOOZED", scheduleId, adherenceLogId,
                medicationId, firestoreId, patientUid, scheduledMillis, snoozeCount, drugName, imageUri, dosage, notifId),
            flags);

        PendingIntent dismissPending = PendingIntent.getBroadcast(
            context, baseReq + 2,
            buildActionIntent(context, "DISMISSED", scheduleId, adherenceLogId,
                medicationId, firestoreId, patientUid, scheduledMillis, snoozeCount, drugName, imageUri, dosage, notifId),
            flags);

        // Content intent to open reminders screen on click
        Intent openIntent = new Intent(context, com.emergency.patient.activities.NotificationsRemindersActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPending = PendingIntent.getActivity(
            context, notifId, openIntent, flags);

        // ── Notification body text ────────────────────────────────────────────
        String contentText = "It is time to take your " + drugName;
        String dosageStr   = (dosage != null && !dosage.isEmpty()) ? dosage : "";
        String expandedText = "It is time to take your " + drugName
            + (dosageStr.isEmpty() ? "" : "\nDosage: " + dosageStr)
            + "\nTap TAKE when done or SNOOZE for 10 minutes.";

        // ── Builder base ──────────────────────────────────────────────────────
        MedicationAudioPlayer.play(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MED_REMINDER_CHANNEL_ID)
            .setContentTitle("Time to take your medicine \uD83D\uDC8A")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .setDeleteIntent(dismissPending)
            .setVibrate(new long[]{0, 400, 200, 400})
            .addAction(0, "\u2705 TAKE",    takenPending)
            .addAction(0, "\u23F0 SNOOZE",  snoozePending)
            .addAction(0, "\u2716 DISMISS", dismissPending);

        // ── Load medicine image (synchronous — caller must be on bg thread) ───
        Bitmap medicineBitmap = NotificationImageLoader.load(context, imageUri);

        if (medicineBitmap != null) {
            builder.setStyle(new NotificationCompat.BigPictureStyle()
                .bigPicture(medicineBitmap)
                .bigLargeIcon((Bitmap) null)
                .setBigContentTitle("Time to take your medicine \uD83D\uDC8A")
                .setSummaryText(drugName
                    + (dosageStr.isEmpty() ? "" : "  \u00B7  " + dosageStr)));
            builder.setLargeIcon(medicineBitmap);
        } else {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(expandedText));
        }

        NotificationManagerCompat.from(context).notify(notifId, builder.build());
    }

    /**
     * Full-featured dose reminder WITH a full-screen PendingIntent.
     *
     * Android uses the full-screen intent to launch the activity directly over
     * the lock-screen / other apps.  The regular notification still appears in
     * the status bar as a fallback for devices / configurations where the
     * full-screen launch is suppressed.
     */
    public static void showDoseReminderWithFullScreen(
            Context context,
            String drugName,
            String dosage,
            String imageUri,
            int scheduleId,
            int adherenceLogId,
            int medicationId,
            String firestoreId,
            String patientUid,
            long scheduledMillis,
            int snoozeCount,
            android.app.PendingIntent fullScreenPendingIntent) {

        if (!hasNotificationPermission(context)) return;
        createChannels(context);

        int notifId = scheduleId > 0 ? scheduleId : Math.abs((drugName != null ? drugName : "").hashCode() % 100000) + 1;
        int baseReq = notifId * 10;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        PendingIntent takenPending = PendingIntent.getBroadcast(
            context, baseReq,
            buildActionIntent(context, "TAKEN", scheduleId, adherenceLogId,
                medicationId, firestoreId, patientUid, scheduledMillis, snoozeCount, drugName, imageUri, dosage, notifId),
            flags);

        PendingIntent snoozePending = PendingIntent.getBroadcast(
            context, baseReq + 1,
            buildActionIntent(context, "SNOOZED", scheduleId, adherenceLogId,
                medicationId, firestoreId, patientUid, scheduledMillis, snoozeCount, drugName, imageUri, dosage, notifId),
            flags);

        PendingIntent dismissPending = PendingIntent.getBroadcast(
            context, baseReq + 2,
            buildActionIntent(context, "DISMISSED", scheduleId, adherenceLogId,
                medicationId, firestoreId, patientUid, scheduledMillis, snoozeCount, drugName, imageUri, dosage, notifId),
            flags);

        // Content intent to open reminders screen on click
        Intent openIntent = new Intent(context, com.emergency.patient.activities.NotificationsRemindersActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPending = PendingIntent.getActivity(
            context, notifId, openIntent, flags);

        String contentText  = "It is time to take your " + drugName;
        String dosageStr    = (dosage != null && !dosage.isEmpty()) ? dosage : "";
        String expandedText = "It is time to take your " + drugName
            + (dosageStr.isEmpty() ? "" : "\nDosage: " + dosageStr)
            + "\nTap TAKE when done or SNOOZE for 10 minutes.";

        MedicationAudioPlayer.play(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MED_REMINDER_CHANNEL_ID)
            .setContentTitle("Time to take your medicine \uD83D\uDC8A")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .setDeleteIntent(dismissPending)
            .setVibrate(new long[]{0, 400, 200, 400})
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "\u2705 TAKE",    takenPending)
            .addAction(0, "\u23F0 SNOOZE",  snoozePending)
            .addAction(0, "\u2716 DISMISS", dismissPending);

        Bitmap medicineBitmap = NotificationImageLoader.load(context, imageUri);
        if (medicineBitmap != null) {
            builder.setStyle(new NotificationCompat.BigPictureStyle()
                .bigPicture(medicineBitmap)
                .bigLargeIcon((Bitmap) null)
                .setBigContentTitle("Time to take your medicine \uD83D\uDC8A")
                .setSummaryText(drugName + (dosageStr.isEmpty() ? "" : "  \u00B7  " + dosageStr)));
            builder.setLargeIcon(medicineBitmap);
        } else {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(expandedText));
        }

        NotificationManagerCompat.from(context).notify(notifId, builder.build());
    }

    /**
     * Backward-compatible overload — no image, no dosage.
     * Called by legacy code paths (MedicationWorker v1 data format).
     */
    public static void showDoseReminder(
            Context context,
            String drugName,
            int scheduleId,
            int adherenceLogId,
            int medicationId,
            String firestoreId,
            String patientUid,
            long scheduledMillis) {
        showDoseReminder(context, drugName, "", null,
            scheduleId, adherenceLogId, medicationId,
            firestoreId, patientUid, scheduledMillis, 0);
    }

    /**
     * Legacy overload — used by old callers that only had drugName + scheduleId.
     */
    public static void showNotification(Context context, String drugName, int scheduleId) {
        showDoseReminder(context, drugName, scheduleId, -1, -1, "", "", 0L);
    }

    // ── Expiry Notification ────────────────────────────────────────────────────

    public static void showExpiryNotification(Context context, String drugName, int daysRemaining) {
        if (!hasNotificationPermission(context)) return;
        createChannels(context);

        String title   = "Medication Expiring Soon!";
        String content = (daysRemaining == 0)
            ? drugName + " expires today! Please dispose safely."
            : drugName + " expires in " + daysRemaining + " day" + (daysRemaining == 1 ? "" : "s") + ".";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MED_EXPIRY_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(content));

        int notificationId = 100000 + Math.abs(drugName.hashCode());
        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Intent buildActionIntent(
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
            String dosage,
            int notifId) {

        Intent intent = new Intent(context, ActionReceiver.class);
        intent.setPackage(context.getPackageName());
        intent.setAction("com.emergency.patient.ACTION_" + action);
        intent.setData(Uri.parse("content://emergency/dose/" + notifId + "/" + action));
        intent.putExtra("action",          action);
        intent.putExtra("scheduleId",      scheduleId);
        intent.putExtra("notifId",         notifId);
        intent.putExtra("adherenceLogId",  adherenceLogId);
        intent.putExtra("medicationId",    medicationId);
        intent.putExtra("firestoreId",     firestoreId);
        intent.putExtra("patientUid",      patientUid);
        intent.putExtra("scheduledMillis", scheduledMillis);
        intent.putExtra("snoozeCount",     snoozeCount);
        intent.putExtra("drug",            drugName);
        intent.putExtra("imageUri",        imageUri);
        intent.putExtra("dosage",          dosage);
        return intent;
    }

    private static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}

