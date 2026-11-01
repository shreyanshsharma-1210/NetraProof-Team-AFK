package com.emergency.patient.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.emergency.patient.R;
import com.emergency.patient.db.AdherenceLogEntity;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.MedicationDao;
import com.emergency.patient.db.MedicationEntity;
import com.emergency.patient.db.MedicationScheduleEntity;
import com.emergency.patient.scheduling.DosageParser;
import com.emergency.patient.scheduling.MissedDoseHandler;
import com.emergency.patient.scheduling.ScheduleData;
import com.emergency.patient.scheduling.MedicationWorker;
import com.emergency.patient.security.TokenManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MedicationSyncService — Foreground service that maintains a live Firestore
 * snapshot listener on the patient's medication collection.
 *
 * Firestore path:  users/{patientUid}/medications/{medicationId}
 *
 * On any ADD / MODIFY / REMOVE event the service:
 *   1. Syncs Room DB (insert / update / delete)
 *   2. Cancels and regenerates WorkManager reminder workers
 *   3. Creates a PENDING AdherenceLogEntity for each new schedule slot
 *
 * Survives app restart (started from DashboardActivity) and
 * network reconnects (Firestore SDK handles re-attachment).
 */
public class MedicationSyncService extends Service {

    private static final String TAG = "MedSyncService";
    public static final String CHANNEL_ID = "med_sync_channel";
    public static final int NOTIFICATION_ID = 3001;

    public static final String ACTION_START = "com.emergency.patient.action.START_MED_SYNC";
    public static final String ACTION_STOP  = "com.emergency.patient.action.STOP_MED_SYNC";

    private FirebaseFirestore firestore;
    private ListenerRegistration medicationListener;
    private final Executor executor = Executors.newSingleThreadExecutor();

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        firestore = FirebaseFirestore.getInstance();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification());
        attachListener();
        return START_STICKY; // Restart if killed — critical for live sync
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (medicationListener != null) {
            medicationListener.remove();
            medicationListener = null;
            Log.d(TAG, "Firestore medication listener detached.");
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ── Firestore Listener ─────────────────────────────────────────────────────

    private void attachListener() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "No authenticated Firebase user — skipping sync listener.");
            return;
        }

        String patientUid = user.getUid();
        Log.d(TAG, "Attaching Firestore medication listener for uid=" + patientUid);

        if (medicationListener != null) {
            medicationListener.remove();
        }

        medicationListener = firestore
            .collection("users")
            .document(patientUid)
            .collection("medications")
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    Log.e(TAG, "Medication listener error: " + error.getMessage());
                    return; // SDK will retry on reconnect automatically
                }
                if (snapshots == null) return;

                for (DocumentChange change : snapshots.getDocumentChanges()) {
                    String docId = change.getDocument().getId();
                    switch (change.getType()) {
                        case ADDED:
                        case MODIFIED:
                            executor.execute(() -> handleUpsert(patientUid, docId, change.getDocument().getData()));
                            break;
                        case REMOVED:
                            executor.execute(() -> handleRemove(patientUid, docId));
                            break;
                    }
                }
            });
    }

    // ── Sync Handlers ─────────────────────────────────────────────────────────

    /**
     * Upsert a medication from Firestore into Room DB and regenerate reminders.
     */
    private void handleUpsert(String patientUid, String firestoreId, java.util.Map<String, Object> data) {
        if (data == null) return;

        MedicationDao dao = AppDatabaseProvider.getInstance(this).medicationDao();

        // Check for duplicate by firestoreId
        MedicationEntity existing = dao.findByFirestoreId(firestoreId);

        MedicationEntity entity = (existing != null) ? existing : new MedicationEntity();
        entity.patientUuid = patientUid;
        entity.firestoreId = firestoreId;
        entity.lastSyncedAt = System.currentTimeMillis();
        entity.syncSource = "guardian";
        entity.updatedByGuardian = true;

        entity.name       = getStr(data, "name");
        entity.brandName  = getStr(data, "brandName");
        entity.dosage     = getStr(data, "dosage");
        entity.expiry     = getStr(data, "expiry");

        // Guardian-synced medications may carry an imageUri if uploaded via guardian app
        String syncedImageUri = getStr(data, "imageUri");
        if (!syncedImageUri.isEmpty()) {
            entity.imageUri = syncedImageUri;
        }

        if (existing != null) {
            dao.updateMedication(entity);
            Log.d(TAG, "Updated synced medication: " + entity.name);
            // Cancel old workers before regenerating
            WorkManager.getInstance(this).cancelAllWorkByTag("med_" + entity.name);
        } else {
            long insertedId = dao.insertMedication(entity);
            entity.id = (int) insertedId;
            Log.d(TAG, "Inserted synced medication: " + entity.name + " id=" + insertedId);
        }

        // Regenerate schedule and reminders
        regenerateReminders(entity);
    }

    /**
     * Remove a medication from Room DB and cancel all its pending workers.
     */
    private void handleRemove(String patientUid, String firestoreId) {
        MedicationDao dao = AppDatabaseProvider.getInstance(this).medicationDao();
        MedicationEntity existing = dao.findByFirestoreId(firestoreId);

        if (existing != null) {
            WorkManager.getInstance(this).cancelAllWorkByTag("med_" + existing.name);
            AppDatabaseProvider.getInstance(this).scheduleDao().deleteByDrugName(existing.name);
            dao.deleteByFirestoreId(firestoreId);
            Log.d(TAG, "Removed synced medication: " + existing.name);
        }
    }

    // ── Reminder Regeneration ─────────────────────────────────────────────────

    private void regenerateReminders(MedicationEntity entity) {
        ScheduleData schedule = DosageParser.parse(entity.dosage);
        if (schedule == null || schedule.times == null || schedule.times.isEmpty()) {
            Log.w(TAG, "No schedule parsed for: " + entity.name);
            return;
        }

        String localUuid = TokenManager.getUUID(this);
        String patientUid = (localUuid != null) ? localUuid : entity.patientUuid;

        // ── Record today-past slots as MISSED before scheduling futures ────────
        MissedDoseHandler.recordMissedSlotsForToday(
            this,
            entity.id,
            entity.firestoreId,
            entity.name,
            entity.dosage,
            patientUid
        );

        String safeImageUri  = entity.imageUri  != null ? entity.imageUri  : "";
        String safeDosage    = entity.dosage    != null ? entity.dosage    : "";
        String safeFirestore = entity.firestoreId != null ? entity.firestoreId : "";

        for (String timeStr : schedule.times) {
            try {
                String[] parts = timeStr.split(":");
                int hour   = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                for (int day = 0; day < 30; day++) {
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_YEAR, day);
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, minute);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);

                    long occurrenceMillis = cal.getTimeInMillis();

                    // Skip past slots on day 0 — already handled as MISSED above
                    if (day == 0 && occurrenceMillis < System.currentTimeMillis()) continue;

                    // Insert schedule row
                    long rowId = AppDatabaseProvider.getInstance(this).scheduleDao()
                        .insert(new MedicationScheduleEntity(entity.name, occurrenceMillis, "PENDING"));

                    // Create PENDING adherence log entry for this future slot
                    AdherenceLogEntity log = new AdherenceLogEntity();
                    log.patientUid            = patientUid;
                    log.medicationId          = entity.id;
                    log.medicationFirestoreId = safeFirestore;
                    log.medicationName        = entity.name;
                    log.scheduledTimeMillis   = occurrenceMillis;
                    log.actionType            = "PENDING";
                    long adherenceLogId = AppDatabaseProvider.getInstance(this)
                        .adherenceLogDao().insertLog(log);

                    // Enqueue WorkManager worker (first 7 days only to limit memory)
                    if (day < 7) {
                        long delay = occurrenceMillis - System.currentTimeMillis();
                        Data workerData = new Data.Builder()
                            .putString("drug",              entity.name)
                            .putInt("scheduleId",           (int) rowId)
                            .putInt("adherenceLogId",       (int) adherenceLogId)
                            .putInt("medicationId",         entity.id)
                            .putString("firestoreId",       safeFirestore)
                            .putString("patientUid",        patientUid)
                            .putLong("scheduledTimeMillis", occurrenceMillis)
                            .putString("imageUri",          safeImageUri)
                            .putString("dosage",            safeDosage)
                            .putInt("snoozeCount",          0)
                            .build();

                        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(MedicationWorker.class)
                            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                            .setInputData(workerData)
                            .addTag("med_" + entity.name)
                            .build();

                        WorkManager.getInstance(this).enqueue(work);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to schedule reminder for " + timeStr, e);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getStr(java.util.Map<String, Object> data, String key) {
        Object val = data.get(key);
        return (val != null) ? val.toString() : "";
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Medication Sync",
                NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("Keeps medication data in sync with your care team");
            channel.setShowBadge(false);
            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(channel);
        }
    }

    private android.app.Notification buildForegroundNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sanjivani Sync Active")
            .setContentText("Medication data is being synced")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build();
    }

    // ── Static Helpers ────────────────────────────────────────────────────────

    public static void start(Context context) {
        Intent intent = new Intent(context, MedicationSyncService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, MedicationSyncService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }
}
