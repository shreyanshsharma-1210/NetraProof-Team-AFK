package com.emergency.patient.scheduling;

import android.app.NotificationManager;
import android.content.Context;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.emergency.patient.R;
import com.emergency.patient.db.AdherenceLogDao;
import com.emergency.patient.db.AdherenceLogEntity;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.network.FirebaseRepository;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * MedicationAlarmActivity — Full-screen overlay shown when a medication dose is due.
 *
 * Displays medicine image, name, dosage, and three action buttons:
 *   TAKE / SNOOZE / DISMISS
 *
 * Works over the lock-screen and when the app is fully closed.
 * All DB writes are identical to ActionReceiver so adherence is tracked correctly.
 */
public class MedicationAlarmActivity extends AppCompatActivity {

    private static final String TAG = "MedAlarmActivity";
    private static final int SNOOZE_MINS = 10;

    // Intent extras (mirrored from NotificationHelper / ActionReceiver)
    public static final String EXTRA_DRUG_NAME         = "drug";
    public static final String EXTRA_SCHEDULE_ID       = "scheduleId";
    public static final String EXTRA_ADHERENCE_LOG_ID  = "adherenceLogId";
    public static final String EXTRA_MEDICATION_ID     = "medicationId";
    public static final String EXTRA_FIRESTORE_ID      = "firestoreId";
    public static final String EXTRA_PATIENT_UID       = "patientUid";
    public static final String EXTRA_SCHEDULED_MILLIS  = "scheduledMillis";
    public static final String EXTRA_SNOOZE_COUNT      = "snoozeCount";
    public static final String EXTRA_IMAGE_URI         = "imageUri";
    public static final String EXTRA_DOSAGE            = "dosage";

    private String drugName;
    private int    scheduleId;
    private int    adherenceLogId;
    private int    medicationId;
    private String firestoreId;
    private String patientUid;
    private long   scheduledMillis;
    private int    snoozeCount;
    private String imageUri;
    private String dosage;
    // Audio managed by MedicationAudioPlayer

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Show over lock-screen and turn on screen ─────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_medication_alarm);

        // ── Play alarm audio via MedicationAudioPlayer ────────────────────────
        MedicationAudioPlayer.play(this);

        // ── Read intent extras ────────────────────────────────────────────────
        Intent intent = getIntent();
        drugName        = intent.getStringExtra(EXTRA_DRUG_NAME);
        scheduleId      = intent.getIntExtra(EXTRA_SCHEDULE_ID,      -1);
        adherenceLogId  = intent.getIntExtra(EXTRA_ADHERENCE_LOG_ID, -1);
        medicationId    = intent.getIntExtra(EXTRA_MEDICATION_ID,    -1);
        firestoreId     = intent.getStringExtra(EXTRA_FIRESTORE_ID);
        patientUid      = intent.getStringExtra(EXTRA_PATIENT_UID);
        scheduledMillis = intent.getLongExtra(EXTRA_SCHEDULED_MILLIS, 0L);
        snoozeCount     = intent.getIntExtra(EXTRA_SNOOZE_COUNT,      0);
        imageUri        = intent.getStringExtra(EXTRA_IMAGE_URI);
        dosage          = intent.getStringExtra(EXTRA_DOSAGE);

        // ── Bind UI ───────────────────────────────────────────────────────────
        TextView tvDrugName   = findViewById(R.id.tvDrugName);
        TextView tvDosage     = findViewById(R.id.tvDosage);
        TextView tvAlarmTime  = findViewById(R.id.tvAlarmTime);
        TextView tvSnoozeCount = findViewById(R.id.tvSnoozeCount);
        ImageView ivMedicine  = findViewById(R.id.ivMedicineImage);

        if (drugName != null) tvDrugName.setText(drugName);

        if (dosage != null && !dosage.isEmpty()) {
            tvDosage.setText(dosage);
            tvDosage.setVisibility(View.VISIBLE);
        } else {
            tvDosage.setVisibility(View.GONE);
        }

        // Scheduled time display
        if (scheduledMillis > 0) {
            String timeStr = new SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(new Date(scheduledMillis));
            tvAlarmTime.setText("Scheduled at " + timeStr);
        }

        // Snooze badge
        if (snoozeCount > 0) {
            tvSnoozeCount.setText("Snoozed " + snoozeCount + " time" + (snoozeCount > 1 ? "s" : ""));
            tvSnoozeCount.setVisibility(View.VISIBLE);
        }

        // Load medicine image on background thread
        if (imageUri != null && !imageUri.isEmpty()) {
            new Thread(() -> {
                Bitmap bmp = loadBitmap(imageUri);
                if (bmp != null) {
                    new Handler(Looper.getMainLooper()).post(() -> ivMedicine.setImageBitmap(bmp));
                }
            }).start();
        }

        // ── Button listeners ──────────────────────────────────────────────────
        findViewById(R.id.btnTake).setOnClickListener(v -> handleAction("TAKEN"));
        findViewById(R.id.btnSnooze).setOnClickListener(v -> handleAction("SNOOZED"));
        findViewById(R.id.btnDismiss).setOnClickListener(v -> handleAction("DISMISSED"));
    }

    private void handleAction(String action) {
        // 1. Immediately stop audio
        MedicationAudioPlayer.stop();

        // 2. Immediately cancel notification from shade
        int notifId = scheduleId > 0 ? scheduleId : Math.abs((drugName != null ? drugName : "").hashCode() % 100000) + 1;
        try {
            NotificationManagerCompat.from(this).cancel(notifId);
            if (scheduleId > 0 && scheduleId != notifId) {
                NotificationManagerCompat.from(this).cancel(scheduleId);
            }
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(notifId);
                if (scheduleId > 0 && scheduleId != notifId) {
                    nm.cancel(scheduleId);
                }
            }
        } catch (Exception ignored) {}

        // 3. Instant toast feedback
        String drugDisplay = (drugName != null && !drugName.isEmpty()) ? drugName : "Medication";
        if ("TAKEN".equalsIgnoreCase(action)) {
            Toast.makeText(getApplicationContext(), "✓ Marked " + drugDisplay + " as taken!", Toast.LENGTH_SHORT).show();
        } else if ("SNOOZED".equalsIgnoreCase(action)) {
            Toast.makeText(getApplicationContext(), "⏰ " + drugDisplay + " snoozed for 10 minutes", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), drugDisplay + " marked as missed", Toast.LENGTH_SHORT).show();
        }

        // 4. Immediately close this alarm screen so user is never stuck
        finish();

        // 5. Run DB and Firestore sync in background thread
        final int finalScheduleId = scheduleId;
        final int finalAdherenceLogId = adherenceLogId;
        final int finalMedicationId = medicationId;
        final String finalFirestoreId = firestoreId;
        final String finalPatientUid = patientUid;
        final long finalScheduledMillis = scheduledMillis;
        final int finalSnoozeCount = snoozeCount;
        final String finalDrugName = drugName;
        final String finalImageUri = imageUri;
        final String finalDosage = dosage;

        new Thread(() -> {
            try {
                long now = System.currentTimeMillis();

                // 1. Update Room schedule row status
                if (finalScheduleId != -1) {
                    AppDatabaseProvider.getInstance(getApplicationContext())
                        .scheduleDao().updateStatus(finalScheduleId, action);
                } else if (finalDrugName != null && !finalDrugName.isEmpty()) {
                    try {
                        java.util.Calendar c1 = java.util.Calendar.getInstance();
                        c1.set(java.util.Calendar.HOUR_OF_DAY, 0); c1.set(java.util.Calendar.MINUTE, 0); c1.set(java.util.Calendar.SECOND, 0);
                        java.util.Calendar c2 = java.util.Calendar.getInstance();
                        c2.set(java.util.Calendar.HOUR_OF_DAY, 23); c2.set(java.util.Calendar.MINUTE, 59); c2.set(java.util.Calendar.SECOND, 59);
                        java.util.List<com.emergency.patient.db.MedicationScheduleEntity> list = AppDatabaseProvider.getInstance(getApplicationContext()).scheduleDao().getByDrugName(finalDrugName);
                        for (com.emergency.patient.db.MedicationScheduleEntity s : list) {
                            if (s.timeMillis >= c1.getTimeInMillis() && s.timeMillis <= c2.getTimeInMillis()) {
                                s.status = action;
                                AppDatabaseProvider.getInstance(getApplicationContext()).scheduleDao().update(s);
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // 2. Upsert AdherenceLogEntity
                AdherenceLogDao logDao = AppDatabaseProvider.getInstance(getApplicationContext()).adherenceLogDao();
                AdherenceLogEntity log = finalAdherenceLogId != -1 ? logDao.getById(finalAdherenceLogId) : null;

                if (log == null) {
                    log = new AdherenceLogEntity();
                    log.medicationId          = finalMedicationId;
                    log.medicationFirestoreId = finalFirestoreId != null ? finalFirestoreId : "";
                    log.medicationName        = finalDrugName != null ? finalDrugName : "";
                    log.patientUid            = finalPatientUid != null ? finalPatientUid : "";
                    log.scheduledTimeMillis   = finalScheduledMillis;
                    log.snoozeCount           = 0;
                }

                log.actionType      = action;
                log.actedAt         = now;
                log.deviceTimestamp = now;

                if ("SNOOZED".equals(action)) {
                    log.snoozeCount = finalSnoozeCount + 1;
                }

                int localAdherenceId;
                if (log.id == 0) {
                    localAdherenceId = (int) logDao.insertLog(log);
                    log.id = localAdherenceId;
                } else {
                    logDao.updateAction(log.id, log.actionType, log.actedAt, log.snoozeCount);
                    localAdherenceId = log.id;
                }

                // 3. Sync to Firestore
                final AdherenceLogEntity finalLog = log;
                FirebaseRepository.getInstance(getApplicationContext()).syncAdherenceLog(getApplicationContext(), finalLog, null);

                // 4. SNOOZE: schedule exact alarm + worker 10 minutes from now
                if ("SNOOZED".equals(action) && finalDrugName != null) {
                    long snoozeTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(SNOOZE_MINS);
                    com.emergency.patient.db.MedicationScheduleEntity snoozeSchedule = new com.emergency.patient.db.MedicationScheduleEntity(finalDrugName, snoozeTime, "PENDING");
                    snoozeSchedule.id = finalScheduleId > 0 ? finalScheduleId : (int) (snoozeTime % 100000);
                    MedicationReminderScheduler.scheduleDoseReminder(getApplicationContext(), snoozeSchedule, null);
                }

                // Notify any open screens to refresh immediately
                LocalBroadcastManager.getInstance(getApplicationContext())
                    .sendBroadcast(new Intent(ActionReceiver.ACTION_SCHEDULE_UPDATED));

                Log.d(TAG, "Alarm action=" + action + " drug=" + finalDrugName);

            } catch (Exception e) {
                Log.e(TAG, "Error handling alarm action: " + action, e);
            }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MedicationAudioPlayer.stop();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MedicationAudioPlayer.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MedicationAudioPlayer.stop();
    }

    @Nullable
    private Bitmap loadBitmap(String uriString) {
        try {
            if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                Uri uri = Uri.parse(uriString);
                return BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
            } else {
                return BitmapFactory.decodeFile(new File(uriString).getAbsolutePath());
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load medicine image: " + uriString, e);
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        // Swallowing back-press forces user to explicitly choose an action
        // (same UX as a real alarm clock)
    }
}
