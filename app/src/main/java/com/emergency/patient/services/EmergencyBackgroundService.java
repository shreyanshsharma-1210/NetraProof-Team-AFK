package com.emergency.patient.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

/**
 * EmergencyBackgroundService — Persistent Medical ID QR & SOS notification disabled per user request.
 */
public class EmergencyBackgroundService extends Service {

    public static final String CHANNEL_ID = "emergency_service_channel";
    public static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_START_SERVICE = "com.emergency.patient.action.START_EMERGENCY_SERVICE";
    private static final String ACTION_STOP_SERVICE = "com.emergency.patient.action.STOP_EMERGENCY_SERVICE";

    @Override
    public void onCreate() {
        super.onCreate();
        cancelAndStop();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        cancelAndStop();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelNotification(this);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        cancelNotification(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void cancelAndStop() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {}
        cancelNotification(this);
        stopSelf();
    }

    private static void cancelNotification(Context context) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(NOTIFICATION_ID);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    nm.deleteNotificationChannel(CHANNEL_ID);
                }
            }
        } catch (Exception ignored) {}
    }

    /** Call from any Activity to start the service (no-op; cancels persistent notification). */
    public static void start(Context context) {
        stop(context);
    }

    /** Stop the service and dismiss any persistent notification. */
    public static void stop(Context context) {
        if (context == null) return;
        cancelNotification(context);
        try {
            Intent intent = new Intent(context, EmergencyBackgroundService.class);
            intent.setAction(ACTION_STOP_SERVICE);
            context.stopService(intent);
        } catch (Exception ignored) {}

        // Cancel any pending restart alarms
        try {
            Intent restartIntent = new Intent(context, EmergencyBackgroundService.class);
            restartIntent.setAction(ACTION_START_SERVICE);
            PendingIntent restartPendingIntent = PendingIntent.getService(
                    context,
                    2001,
                    restartIntent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (restartPendingIntent != null) {
                android.app.AlarmManager alarmManager = (android.app.AlarmManager) context
                        .getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null) {
                    alarmManager.cancel(restartPendingIntent);
                }
            }
        } catch (Exception ignored) {}
    }
}
