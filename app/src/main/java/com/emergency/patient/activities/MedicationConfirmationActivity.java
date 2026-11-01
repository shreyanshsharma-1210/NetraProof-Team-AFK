package com.emergency.patient.activities;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.MedicationEntity;
import com.emergency.patient.db.MedicationScheduleEntity;
import com.emergency.patient.ocr.DrugNormalizer;
import com.emergency.patient.ocr.NormalizationResult;
import com.emergency.patient.scheduling.CalendarService;
import com.emergency.patient.scheduling.GroqScheduleInterpreter;
import com.emergency.patient.scheduling.MissedDoseHandler;
import com.emergency.patient.scheduling.ScheduleData;
import com.emergency.patient.security.TokenManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MedicationConfirmationActivity extends AppCompatActivity {

    private String ocrBrand, ocrName, ocrDosage, ocrExpiry, ocrRawText, ocrImageUri;
    private TextView tvDrugName, tvBrandName, tvDosage, tvExpiry;
    private LinearLayout layoutSchedulePreview;
    private Button btnSave;
    private View btnAddReminderTime;
    private com.google.android.material.chip.ChipGroup chipGroupFrequency;
    private com.google.android.material.chip.Chip chipFreq1x, chipFreq2x, chipFreq3x, chipFreq4x;
    private NormalizationResult normResult;
    private ScheduleData scheduleData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medication_confirmation);

        // Get data from OCR Activity
        ocrBrand = getIntent().getStringExtra("ocr_brand");
        ocrName = getIntent().getStringExtra("ocr_name");
        ocrDosage = getIntent().getStringExtra("ocr_dosage");
        ocrExpiry = getIntent().getStringExtra("ocr_expiry");
        ocrRawText = getIntent().getStringExtra("ocr_raw_text");
        ocrImageUri = getIntent().getStringExtra("ocr_image_uri");

        bindViews();
        processData();
        setupListeners();
    }

    private void bindViews() {
        tvDrugName = findViewById(R.id.tv_confirm_drug_name);
        tvBrandName = findViewById(R.id.tv_confirm_brand_name);
        tvDosage = findViewById(R.id.tv_confirm_dosage);
        tvExpiry = findViewById(R.id.tv_confirm_expiry);
        layoutSchedulePreview = findViewById(R.id.layout_schedule_preview);
        btnSave = findViewById(R.id.btn_confirm_save);
        btnAddReminderTime = findViewById(R.id.btn_add_reminder_time);
        chipGroupFrequency = findViewById(R.id.chip_group_frequency);
        chipFreq1x = findViewById(R.id.chip_freq_1x);
        chipFreq2x = findViewById(R.id.chip_freq_2x);
        chipFreq3x = findViewById(R.id.chip_freq_3x);
        chipFreq4x = findViewById(R.id.chip_freq_4x);
    }

    private void processData() {
        // Normalize drug name
        normResult = DrugNormalizer.normalize(this, ocrName);
        tvDrugName.setText(normResult.name);
        tvBrandName.setText(ocrBrand != null && !ocrBrand.isEmpty() ? ocrBrand : "GENERIC");
        tvDosage.setText(ocrDosage);
        tvExpiry.setText(ocrExpiry != null && !ocrExpiry.isEmpty() ? ocrExpiry : "Not set");

        // Parse schedule
        new Thread(() -> {
            scheduleData = GroqScheduleInterpreter.parseWithFallback(ocrDosage);
            if (scheduleData == null) {
                scheduleData = new ScheduleData("DAILY", java.util.Collections.singletonList("09:00"), "1 tablet");
            }
            if (scheduleData.times == null || scheduleData.times.isEmpty()) {
                scheduleData.times = new java.util.ArrayList<>(java.util.Collections.singletonList("09:00"));
            }
            runOnUiThread(() -> {
                syncFrequencyChips();
                renderSchedulePreview();
            });
        }).start();
    }

    private void syncFrequencyChips() {
        if (chipGroupFrequency == null || scheduleData == null || scheduleData.times == null) return;
        int count = scheduleData.times.size();
        chipGroupFrequency.clearCheck();
        if (count == 1 && chipFreq1x != null) {
            chipFreq1x.setChecked(true);
        } else if (count == 2 && chipFreq2x != null) {
            chipFreq2x.setChecked(true);
        } else if (count == 3 && chipFreq3x != null) {
            chipFreq3x.setChecked(true);
        } else if (count == 4 && chipFreq4x != null) {
            chipFreq4x.setChecked(true);
        }
    }

    private void renderSchedulePreview() {
        layoutSchedulePreview.removeAllViews();
        if (scheduleData == null || scheduleData.times == null || scheduleData.times.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No daily reminders set. Tap '+ Add Reminder Time' below.");
            tv.setTextColor(getResources().getColor(R.color.color_text_secondary, getTheme()));
            tv.setPadding(0, 16, 0, 16);
            layoutSchedulePreview.addView(tv);
            return;
        }

        // Sort times chronologically
        java.util.Collections.sort(scheduleData.times, (t1, t2) -> t1.compareTo(t2));

        int totalCount = scheduleData.times.size();

        for (int i = 0; i < totalCount; i++) {
            final int index = i;
            String time24 = scheduleData.times.get(i);

            View item = getLayoutInflater().inflate(R.layout.item_confirm_schedule, layoutSchedulePreview, false);
            TextView tvHour = item.findViewById(R.id.tv_schedule_hour);
            TextView tvAmPm = item.findViewById(R.id.tv_schedule_ampm);
            TextView tvDoseLabel = item.findViewById(R.id.tv_schedule_dose_label);
            TextView tvTimeDetails = item.findViewById(R.id.tv_schedule_time_details);
            View cardItem = item.findViewById(R.id.card_schedule_item);
            View btnAdjust = item.findViewById(R.id.btn_adjust_time);
            View btnRemove = item.findViewById(R.id.btn_remove_time);

            // Parse hour & minute
            int hour = 9, min = 0;
            try {
                String[] parts = time24.split(":");
                hour = Integer.parseInt(parts[0]);
                min = Integer.parseInt(parts[1]);
            } catch (Exception ignored) {}

            String amPm = hour >= 12 ? "PM" : "AM";
            int hour12 = hour % 12;
            if (hour12 == 0) hour12 = 12;

            if (tvHour != null) tvHour.setText(String.format(Locale.getDefault(), "%02d:%02d", hour12, min));
            if (tvAmPm != null) tvAmPm.setText(amPm);
            if (tvDoseLabel != null) tvDoseLabel.setText(com.emergency.patient.scheduling.DosageParser.getSlotLabel(index, totalCount));
            if (tvTimeDetails != null) tvTimeDetails.setText("🔔 Reminder at " + com.emergency.patient.scheduling.DosageParser.formatTime12H(time24));

            // Time Picker on click
            final int finalHour = hour;
            final int finalMin = min;
            View.OnClickListener editClickListener = v -> showTimePickerForSlot(index, finalHour, finalMin);
            if (cardItem != null) cardItem.setOnClickListener(editClickListener);
            if (btnAdjust != null) btnAdjust.setOnClickListener(editClickListener);

            // Show remove button if more than 1 reminder
            if (btnRemove != null) {
                if (totalCount > 1) {
                    btnRemove.setVisibility(View.VISIBLE);
                    btnRemove.setOnClickListener(v -> removeReminderSlot(index));
                } else {
                    btnRemove.setVisibility(View.GONE);
                }
            }

            layoutSchedulePreview.addView(item);
        }
    }

    private void showTimePickerForSlot(int slotIndex, int initialHour, int initialMinute) {
        new android.app.TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String newTimeStr = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
            if (scheduleData != null && scheduleData.times != null && slotIndex < scheduleData.times.size()) {
                scheduleData.times.set(slotIndex, newTimeStr);
                java.util.Collections.sort(scheduleData.times);
                syncFrequencyChips();
                renderSchedulePreview();
            }
        }, initialHour, initialMinute, false).show();
    }

    private void showTimePickerForNewSlot() {
        // Default to a sensible next slot
        int defaultHour = 12;
        int defaultMin = 0;
        if (scheduleData != null && scheduleData.times != null && !scheduleData.times.isEmpty()) {
            try {
                String lastTime = scheduleData.times.get(scheduleData.times.size() - 1);
                String[] parts = lastTime.split(":");
                defaultHour = (Integer.parseInt(parts[0]) + 4) % 24;
            } catch (Exception ignored) {}
        }

        new android.app.TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String newTimeStr = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
            if (scheduleData == null) scheduleData = new ScheduleData();
            if (scheduleData.times == null) scheduleData.times = new java.util.ArrayList<>();
            if (!scheduleData.times.contains(newTimeStr)) {
                scheduleData.times.add(newTimeStr);
                java.util.Collections.sort(scheduleData.times);
                syncFrequencyChips();
                renderSchedulePreview();
            }
        }, defaultHour, defaultMin, false).show();
    }

    private void removeReminderSlot(int slotIndex) {
        if (scheduleData != null && scheduleData.times != null && slotIndex < scheduleData.times.size()) {
            scheduleData.times.remove(slotIndex);
            syncFrequencyChips();
            renderSchedulePreview();
        }
    }

    private void setupListeners() {
        btnSave.setOnClickListener(v -> saveMedication());

        if (btnAddReminderTime != null) {
            btnAddReminderTime.setOnClickListener(v -> showTimePickerForNewSlot());
        }

        if (chipFreq1x != null) {
            chipFreq1x.setOnClickListener(v -> setFrequency(1, "DAILY"));
        }
        if (chipFreq2x != null) {
            chipFreq2x.setOnClickListener(v -> setFrequency(2, "TWICE_DAILY"));
        }
        if (chipFreq3x != null) {
            chipFreq3x.setOnClickListener(v -> setFrequency(3, "THRICE_DAILY"));
        }
        if (chipFreq4x != null) {
            chipFreq4x.setOnClickListener(v -> setFrequency(4, "FOUR_TIMES_DAILY"));
        }
    }

    private void setFrequency(int timesPerDay, String freqName) {
        if (scheduleData == null) scheduleData = new ScheduleData();
        scheduleData.frequency = freqName;
        scheduleData.times = new java.util.ArrayList<>(com.emergency.patient.scheduling.DosageParser.getDefaultTimesForFrequency(timesPerDay));
        renderSchedulePreview();
    }

    private void saveMedication() {
        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        new Thread(() -> {
            try {
                String uuid = TokenManager.getUUID(this);
                if (uuid == null) uuid = "";

                MedicationEntity entity = new MedicationEntity();
                entity.patientUuid = uuid;
                entity.brandName = ocrBrand;
                entity.name = normResult.name;
                entity.dosage = ocrDosage;
                entity.expiry = ocrExpiry;
                entity.rawOcrText = ocrRawText;
                entity.ocrSource = "hybrid";

                if (ocrImageUri != null && !ocrImageUri.isEmpty()) {
                    entity.imageUri = saveImageToInternal(ocrImageUri);
                }

                // Insert into DB
                AppDatabaseProvider.getInstance(this).medicationDao().insertMedication(entity);

                // Calendar Integration
                CalendarService.addMedicationToCalendar(this, entity.name, scheduleData);

                // Schedule reminders
                scheduleReminders(entity);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Medication Added Successfully! ✅", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(this, NotificationsRemindersActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                });

            } catch (Exception e) {
                Log.e("ConfirmActivity", "Save failed", e);
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Confirm & Schedule");
                    Toast.makeText(this, "Failed to save medication", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void scheduleReminders(MedicationEntity entity) {
        if (scheduleData == null) return;

        String patientUid = "";
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser != null) patientUid = fbUser.getUid();

        // ── Step 1: record any today-past slots as MISSED before scheduling futures ──
        MissedDoseHandler.recordMissedSlotsForToday(
            this,
            entity.id,
            entity.firestoreId,
            entity.name,
            entity.dosage,
            patientUid
        );

        // ── Step 2: schedule future occurrences normally ───────────────────────
        for (String timeStr : scheduleData.times) {
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
                    long occurrenceTime = cal.getTimeInMillis();
                    String initialStatus = "PENDING";
                    MedicationScheduleEntity sch = new MedicationScheduleEntity(entity.name, occurrenceTime, initialStatus);
                    int rowId = (int) AppDatabaseProvider.getInstance(this).scheduleDao().insert(sch);
                    sch.id = rowId;

                    if (occurrenceTime > System.currentTimeMillis()) {
                        com.emergency.patient.scheduling.MedicationReminderScheduler.scheduleDoseReminder(this, sch, entity);
                    }
                }
            } catch (Exception e) {
                Log.e("ConfirmActivity", "Scheduling failed for " + timeStr, e);
            }
        }
    }

    private void scheduleWork(MedicationEntity entity, int scheduleId, long time, String patientUid) {
        long delay = time - System.currentTimeMillis();

        androidx.work.Data data = new androidx.work.Data.Builder()
            .putString("drug",               entity.name)
            .putInt("scheduleId",            scheduleId)
            .putInt("medicationId",          entity.id)
            .putString("firestoreId",        entity.firestoreId != null ? entity.firestoreId : "")
            .putString("patientUid",         patientUid)
            .putLong("scheduledTimeMillis",  time)
            .putString("imageUri",           entity.imageUri != null ? entity.imageUri : "")
            .putString("dosage",             entity.dosage  != null ? entity.dosage   : "")
            .putInt("snoozeCount",           0)
            .build();

        androidx.work.OneTimeWorkRequest work =
            new androidx.work.OneTimeWorkRequest.Builder(
                    com.emergency.patient.scheduling.MedicationWorker.class)
                .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("med_" + entity.name)
                .build();

        androidx.work.WorkManager.getInstance(this).enqueue(work);
    }

    private String saveImageToInternal(String uriStr) {
        try {
            Uri sourceUri = Uri.parse(uriStr);
            File internalDir = new File(getFilesDir(), "med_images");
            if (!internalDir.exists()) internalDir.mkdirs();

            File destFile = new File(internalDir, "med_" + System.currentTimeMillis() + ".jpg");
            
            try (FileInputStream inStream = (FileInputStream) getContentResolver().openInputStream(sourceUri);
                 FileOutputStream outStream = new FileOutputStream(destFile)) {
                FileChannel inChannel = inStream.getChannel();
                FileChannel outChannel = outStream.getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
            }
            return destFile.getAbsoluteFile().toString();
        } catch (Exception e) {
            Log.e("ConfirmActivity", "Image copy failed", e);
            return uriStr;
        }
    }
}
