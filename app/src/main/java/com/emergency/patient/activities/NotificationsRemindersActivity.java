package com.emergency.patient.activities;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.MedicationScheduleEntity;
import com.emergency.patient.models.diabetes360.Diabetes360Data;
import com.emergency.patient.models.diabetes360.Diabetes360Repository;
import com.emergency.patient.models.diabetes360.HealthReminder;
import com.emergency.patient.scheduling.NotificationHelper;
import com.emergency.patient.security.TokenManager;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * NotificationsRemindersActivity — NetraProof Notifications & Reminders Module (Page 14).
 *
 * Provides a unified scheduling hub for:
 *  - Prescription medication dose timings (from Room DB medication_schedule).
 *  - Clinical screening reminders (Retinal screening, Kidney labs, HbA1c tests, Foot checks).
 *  - Interactive reminder state transitions (Pending / Completed) and deletion.
 *  - Android notification channel diagnostics and test alert triggering.
 */
public class NotificationsRemindersActivity extends AppCompatActivity {

    private ImageView btnRemindersBack;
    private TextView tvRemindersHeaderSubtitle;
    private MaterialButton btnHeaderAddReminder;

    // Filter Tabs
    private TextView tabFilterAll, tabFilterMeds, tabFilterTests;
    private int currentFilter = 0; // 0: All, 1: Meds, 2: Tests

    // Today's Doses
    private View cardTodayMedicationsSection;
    private TextView tvTodayDosesCount, tvEmptyTodayDoses;
    private LinearLayout layoutTodayDosesList;

    // Health Reminders
    private TextView tvHealthRemindersCount, tvEmptyHealthReminders;
    private LinearLayout layoutHealthRemindersList;

    // Diagnostics
    private TextView tvNotificationStatusNotice;
    private MaterialButton btnSendTestNotification;

    // Quick Add Medicine Buttons
    private MaterialButton btnAddMedicationDose, btnEmptyAddMedicine;
    private View layoutEmptyTodayDosesContainer;

    private Uri currentCameraPhotoUri;
    private ActivityResultLauncher<Uri> takeCameraPictureLauncher;
    private ActivityResultLauncher<String> pickGalleryImageLauncher;

    private String patientUuid;
    private ActivityResultLauncher<String> requestNotificationPermissionLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications_reminders);

        patientUuid = TokenManager.getUUID(this);
        if (patientUuid == null || patientUuid.trim().isEmpty()) {
            patientUuid = "default_patient";
        }

        initViews();
        setupPermissionLauncher();
        setupClickListeners();
        checkNotificationPermissionStatus();
        loadRemindersData();
        com.emergency.patient.utils.NetraProfileHeaderHelper.setup(this);
    }

    private final android.content.BroadcastReceiver scheduleUpdateReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadRemindersData();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        checkNotificationPermissionStatus();
        loadRemindersData();
        com.emergency.patient.utils.NetraProfileHeaderHelper.setup(this);
        try {
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                    .registerReceiver(scheduleUpdateReceiver, new android.content.IntentFilter(com.emergency.patient.scheduling.ActionReceiver.ACTION_SCHEDULE_UPDATED));
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                    .unregisterReceiver(scheduleUpdateReceiver);
        } catch (Exception ignored) {}
    }

    private void initViews() {
        btnRemindersBack = findViewById(R.id.btnRemindersBack);
        tvRemindersHeaderSubtitle = findViewById(R.id.tvRemindersHeaderSubtitle);
        btnHeaderAddReminder = findViewById(R.id.btnHeaderAddReminder);

        tabFilterAll = findViewById(R.id.tabFilterAll);
        tabFilterMeds = findViewById(R.id.tabFilterMeds);
        tabFilterTests = findViewById(R.id.tabFilterTests);

        cardTodayMedicationsSection = findViewById(R.id.cardTodayMedicationsSection);
        tvTodayDosesCount = findViewById(R.id.tvTodayDosesCount);
        tvEmptyTodayDoses = findViewById(R.id.tvEmptyTodayDoses);
        layoutTodayDosesList = findViewById(R.id.layoutTodayDosesList);

        tvHealthRemindersCount = findViewById(R.id.tvHealthRemindersCount);
        tvEmptyHealthReminders = findViewById(R.id.tvEmptyHealthReminders);
        layoutHealthRemindersList = findViewById(R.id.layoutHealthRemindersList);

        tvNotificationStatusNotice = findViewById(R.id.tvNotificationStatusNotice);
        btnSendTestNotification = findViewById(R.id.btnSendTestNotification);

        btnAddMedicationDose = findViewById(R.id.btnAddMedicationDose);
        btnEmptyAddMedicine = findViewById(R.id.btnEmptyAddMedicine);
        layoutEmptyTodayDosesContainer = findViewById(R.id.layoutEmptyTodayDosesContainer);
    }

    private void setupPermissionLauncher() {
        requestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    checkNotificationPermissionStatus();
                    if (isGranted) {
                        Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Notifications disabled. Reminders will only appear inside the app.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        takeCameraPictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && currentCameraPhotoUri != null) {
                        Intent intent = new Intent(this, MedicationOcrActivity.class);
                        intent.putExtra(MedicationOcrActivity.EXTRA_IMAGE_URI, currentCameraPhotoUri.toString());
                        startActivity(intent);
                    }
                }
        );

        pickGalleryImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        Intent intent = new Intent(this, MedicationOcrActivity.class);
                        intent.putExtra(MedicationOcrActivity.EXTRA_IMAGE_URI, uri.toString());
                        startActivity(intent);
                    }
                }
        );
    }

    private void setupClickListeners() {
        btnRemindersBack.setOnClickListener(v -> finish());
        btnHeaderAddReminder.setOnClickListener(v -> showNewReminderChooser());

        if (btnAddMedicationDose != null) {
            btnAddMedicationDose.setOnClickListener(v -> showAddMedicationOptionsDialog());
        }
        if (btnEmptyAddMedicine != null) {
            btnEmptyAddMedicine.setOnClickListener(v -> showAddMedicationOptionsDialog());
        }

        tabFilterAll.setOnClickListener(v -> selectFilterTab(0));
        tabFilterMeds.setOnClickListener(v -> selectFilterTab(1));
        tabFilterTests.setOnClickListener(v -> selectFilterTab(2));

        btnSendTestNotification.setOnClickListener(v -> triggerTestNotification());
    }

    private void selectFilterTab(int index) {
        currentFilter = index;
        tabFilterAll.setBackgroundResource(index == 0 ? R.drawable.bg_mode_pill_selected : R.drawable.bg_mode_pill_unselected);
        tabFilterAll.setTextColor(ContextCompat.getColor(this, index == 0 ? android.R.color.white : R.color.netra_text_secondary));

        tabFilterMeds.setBackgroundResource(index == 1 ? R.drawable.bg_mode_pill_selected : R.drawable.bg_mode_pill_unselected);
        tabFilterMeds.setTextColor(ContextCompat.getColor(this, index == 1 ? android.R.color.white : R.color.netra_text_secondary));

        tabFilterTests.setBackgroundResource(index == 2 ? R.drawable.bg_mode_pill_selected : R.drawable.bg_mode_pill_unselected);
        tabFilterTests.setTextColor(ContextCompat.getColor(this, index == 2 ? android.R.color.white : R.color.netra_text_secondary));

        loadRemindersData();
    }

    private void checkNotificationPermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                tvNotificationStatusNotice.setText("Notification permission is not granted. Tap below to enable audible dose and screening alerts.");
                btnSendTestNotification.setText("Enable Notifications");
                btnSendTestNotification.setOnClickListener(v -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                });
                return;
            }
        }
        tvNotificationStatusNotice.setText("System notifications are active. Audible chimes and lockscreen alerts will fire when your scheduled times arrive.");
        btnSendTestNotification.setText("Send Test Notification");
        btnSendTestNotification.setOnClickListener(v -> triggerTestNotification());
    }

    private void triggerTestNotification() {
        NotificationHelper.createChannels(this);
        new Thread(() -> {
            List<MedicationScheduleEntity> doses = AppDatabaseProvider.getInstance(this).scheduleDao().getAll();
            String testDrug = "Multivitamin";
            int testScheduleId = 1001;
            String dosage = "1 tablet with water";
            if (!doses.isEmpty()) {
                MedicationScheduleEntity first = doses.get(0);
                if (first.drugName != null && !first.drugName.isEmpty()) {
                    testDrug = first.drugName;
                }
                testScheduleId = first.id;
            }
            NotificationHelper.showDoseReminder(
                    this,
                    testDrug,
                    dosage,
                    null,
                    testScheduleId,
                    -1,
                    testScheduleId,
                    "",
                    patientUuid != null ? patientUuid : "",
                    System.currentTimeMillis(),
                    0
            );
            runOnUiThread(() -> Toast.makeText(this, "Test reminder sent with TAKE, SNOOZE & DISMISS buttons!", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void loadRemindersData() {
        new Thread(() -> {
            // 1. Auto-mark pending doses whose scheduled time has passed (or past days) as MISSED
            try {
                long now = System.currentTimeMillis();
                AppDatabaseProvider.getInstance(this).scheduleDao().markPendingBeforeAsMissed(now);
            } catch (Exception ignored) {}

            // 2. Load Today's Medication Doses from Room DB
            List<MedicationScheduleEntity> doses = new ArrayList<>();
            try {
                doses = AppDatabaseProvider.getInstance(this).scheduleDao().getAll();
            } catch (Exception ignored) {}

            // 3. Load Health Reminders from Diabetes360
            Diabetes360Data data = Diabetes360Repository.getInstance().getData(this, patientUuid);
            List<HealthReminder> allReminders = data.getReminders();

            final List<MedicationScheduleEntity> finalDoses = doses;
            runOnUiThread(() -> {
                populateDoses(finalDoses);
                populateHealthReminders(allReminders);
            });
        }).start();
    }

    private void populateDoses(List<MedicationScheduleEntity> doses) {
        if (currentFilter == 2) {
            // "Screening" tab selected - hide medicines section
            cardTodayMedicationsSection.setVisibility(View.GONE);
            return;
        }

        cardTodayMedicationsSection.setVisibility(View.VISIBLE);
        layoutTodayDosesList.removeAllViews();

        Calendar calStart = Calendar.getInstance();
        calStart.set(Calendar.HOUR_OF_DAY, 0);
        calStart.set(Calendar.MINUTE, 0);
        calStart.set(Calendar.SECOND, 0);
        calStart.set(Calendar.MILLISECOND, 0);
        long startToday = calStart.getTimeInMillis();

        Calendar calEnd = Calendar.getInstance();
        calEnd.set(Calendar.HOUR_OF_DAY, 23);
        calEnd.set(Calendar.MINUTE, 59);
        calEnd.set(Calendar.SECOND, 59);
        calEnd.set(Calendar.MILLISECOND, 999);
        long endToday = calEnd.getTimeInMillis();

        List<MedicationScheduleEntity> filtered = new ArrayList<>();
        for (MedicationScheduleEntity d : doses) {
            if ("DELETED".equalsIgnoreCase(d.status)) continue;

            // Only show today's doses under "Today's Medication Doses"
            boolean isToday = (d.timeMillis >= startToday && d.timeMillis <= endToday);
            if (!isToday) continue;

            // Always keep all of today's medication doses right here with badge (Taken, Missed, or Pending)
            filtered.add(d);
        }

        Collections.sort(filtered, (a, b) -> Long.compare(a.timeMillis, b.timeMillis));

        tvTodayDosesCount.setText(filtered.size() + (filtered.size() == 1 ? " dose" : " doses"));

        if (filtered.isEmpty()) {
            if (layoutEmptyTodayDosesContainer != null) layoutEmptyTodayDosesContainer.setVisibility(View.VISIBLE);
            tvEmptyTodayDoses.setVisibility(View.VISIBLE);
            return;
        }

        if (layoutEmptyTodayDosesContainer != null) layoutEmptyTodayDosesContainer.setVisibility(View.GONE);
        tvEmptyTodayDoses.setVisibility(View.GONE);
        SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        for (MedicationScheduleEntity dose : filtered) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutTodayDosesList, false);
            ImageView iv = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvTime = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);

            boolean isTaken = "TAKEN".equalsIgnoreCase(dose.status);
            boolean isMissed = "MISSED".equalsIgnoreCase(dose.status);

            iv.setImageResource(R.drawable.ic_shield_check_mint);

            if (isTaken) {
                iv.setColorFilter(ContextCompat.getColor(this, R.color.netra_success));
                tvStatus.setText("✓ Taken");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_success));
            } else if (isMissed) {
                iv.setColorFilter(ContextCompat.getColor(this, R.color.color_emergency_red));
                tvStatus.setText("⚠ Missed");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.color_emergency_red));
            } else {
                iv.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));
                tvStatus.setText("Pending");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));
            }

            tvTitle.setText(dose.drugName != null ? dose.drugName : "Prescribed Medication");
            tvTime.setText("Scheduled: " + (dose.timeMillis > 0 ? timeFmt.format(new Date(dose.timeMillis)) : "Daily"));

            row.setOnClickListener(v -> showDoseActionDialog(dose));
            row.setOnLongClickListener(v -> {
                showDoseActionDialog(dose);
                return true;
            });

            layoutTodayDosesList.addView(row);
        }
    }

    private void showDoseActionDialog(MedicationScheduleEntity dose) {
        SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        String timeStr = dose.timeMillis > 0 ? timeFmt.format(new Date(dose.timeMillis)) : "Today";
        String drugName = dose.drugName != null ? dose.drugName : "Medication";

        List<String> optionsList = new ArrayList<>();
        List<Runnable> actionsList = new ArrayList<>();

        if ("TAKEN".equalsIgnoreCase(dose.status)) {
            optionsList.add("⏳ Mark as Not Taken (Pending)");
            actionsList.add(() -> updateDoseStatus(dose, "PENDING", "Marked as pending"));

            optionsList.add("❌ Mark as Missed");
            actionsList.add(() -> updateDoseStatus(dose, "MISSED", "Marked as missed"));
        } else if ("MISSED".equalsIgnoreCase(dose.status)) {
            optionsList.add("✅ Mark as Taken (Taken Later)");
            actionsList.add(() -> updateDoseStatus(dose, "TAKEN", "Recorded dose as taken"));

            optionsList.add("⏳ Mark as Pending");
            actionsList.add(() -> updateDoseStatus(dose, "PENDING", "Marked as pending"));
        } else { // PENDING
            optionsList.add("✅ Mark as Taken");
            actionsList.add(() -> updateDoseStatus(dose, "TAKEN", "Recorded dose as taken"));

            optionsList.add("❌ Mark as Missed");
            actionsList.add(() -> updateDoseStatus(dose, "MISSED", "Marked as missed"));
        }

        optionsList.add("⏰ Edit Reminder Time");
        actionsList.add(() -> promptEditReminderTime(dose));

        optionsList.add("🗑️ Delete This Dose");
        actionsList.add(() -> deleteSingleDose(dose));

        optionsList.add("🗑️ Delete All 30-Day Schedules");
        actionsList.add(() -> deleteAllDosesForDrug(dose.drugName));

        CharSequence[] items = optionsList.toArray(new CharSequence[0]);

        String statusLabel = "TAKEN".equalsIgnoreCase(dose.status) ? "Taken" :
                ("MISSED".equalsIgnoreCase(dose.status) ? "Missed" : "Pending");

        new AlertDialog.Builder(this)
                .setTitle(drugName + " • " + timeStr + " (" + statusLabel + ")")
                .setItems(items, (dialog, which) -> {
                    if (which >= 0 && which < actionsList.size()) {
                        actionsList.get(which).run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateDoseStatus(MedicationScheduleEntity dose, String newStatus, String toastMsg) {
        new Thread(() -> {
            dose.status = newStatus;
            AppDatabaseProvider.getInstance(this).scheduleDao().update(dose);
            runOnUiThread(() -> {
                Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
                loadRemindersData();
            });
        }).start();
    }

    private void deleteSingleDose(MedicationScheduleEntity dose) {
        new Thread(() -> {
            com.emergency.patient.scheduling.MedicationReminderScheduler.cancelDoseReminder(this, dose);
            AppDatabaseProvider.getInstance(this).scheduleDao().deleteById(dose.id);
            runOnUiThread(() -> {
                Toast.makeText(this, "Dose deleted", Toast.LENGTH_SHORT).show();
                loadRemindersData();
            });
        }).start();
    }

    private void deleteAllDosesForDrug(String drugName) {
        if (drugName == null || drugName.isEmpty()) return;
        new Thread(() -> {
            List<MedicationScheduleEntity> list = AppDatabaseProvider.getInstance(this).scheduleDao().getByDrugName(drugName);
            for (MedicationScheduleEntity item : list) {
                com.emergency.patient.scheduling.MedicationReminderScheduler.cancelDoseReminder(this, item);
            }
            AppDatabaseProvider.getInstance(this).scheduleDao().deleteByDrugName(drugName);
            runOnUiThread(() -> {
                Toast.makeText(this, "Deleted all scheduled doses for " + drugName, Toast.LENGTH_SHORT).show();
                loadRemindersData();
            });
        }).start();
    }

    private void promptEditReminderTime(MedicationScheduleEntity dose) {
        Calendar cal = Calendar.getInstance();
        if (dose.timeMillis > 0) {
            cal.setTimeInMillis(dose.timeMillis);
        }
        int initialHour = cal.get(Calendar.HOUR_OF_DAY);
        int initialMinute = cal.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, newHour, newMinute) -> {
            int hour12 = newHour % 12 == 0 ? 12 : newHour % 12;
            String amPm = newHour >= 12 ? "PM" : "AM";
            String newTimeFormatted = String.format(Locale.getDefault(), "%02d:%02d %s", hour12, newMinute, amPm);

            CharSequence[] scopeOptions = new CharSequence[]{
                    "📅 Update All 30-Day Schedules to " + newTimeFormatted,
                    "📌 Update Only Today's Dose to " + newTimeFormatted
            };

            new AlertDialog.Builder(this)
                    .setTitle("Apply New Time (" + newTimeFormatted + ")")
                    .setItems(scopeOptions, (dialog, which) -> {
                        if (which == 0) {
                            updateAllDoseTimesForDrug(dose.drugName, newHour, newMinute);
                        } else {
                            updateSingleDoseTime(dose, newHour, newMinute);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

        }, initialHour, initialMinute, false).show();
    }

    private void updateSingleDoseTime(MedicationScheduleEntity dose, int hour, int minute) {
        new Thread(() -> {
            // Cancel previous alarm
            com.emergency.patient.scheduling.MedicationReminderScheduler.cancelDoseReminder(this, dose);

            Calendar c = Calendar.getInstance();
            if (dose.timeMillis > 0) {
                c.setTimeInMillis(dose.timeMillis);
            }
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            dose.timeMillis = c.getTimeInMillis();

            // Re-evaluate pending vs missed if not taken
            if (!"TAKEN".equalsIgnoreCase(dose.status)) {
                dose.status = (dose.timeMillis > System.currentTimeMillis()) ? "PENDING" : "MISSED";
            }

            AppDatabaseProvider.getInstance(this).scheduleDao().update(dose);

            // Fetch drug details if available
            com.emergency.patient.db.MedicationEntity med = null;
            try {
                med = AppDatabaseProvider.getInstance(this).medicationDao().findByName(dose.drugName);
            } catch (Exception ignored) {}

            // Re-schedule alarm if future and not taken
            if (!"TAKEN".equalsIgnoreCase(dose.status) && dose.timeMillis > System.currentTimeMillis()) {
                com.emergency.patient.scheduling.MedicationReminderScheduler.scheduleDoseReminder(this, dose, med);
            }

            int h12 = hour % 12 == 0 ? 12 : hour % 12;
            String amPm = hour >= 12 ? "PM" : "AM";
            String toastTime = String.format(Locale.getDefault(), "%02d:%02d %s", h12, minute, amPm);

            runOnUiThread(() -> {
                Toast.makeText(this, "Reminder set for " + toastTime + " ⏰", Toast.LENGTH_SHORT).show();
                loadRemindersData();
            });
        }).start();
    }

    private void updateAllDoseTimesForDrug(String drugName, int hour, int minute) {
        if (drugName == null || drugName.isEmpty()) return;
        new Thread(() -> {
            com.emergency.patient.db.MedicationEntity med = null;
            try {
                med = AppDatabaseProvider.getInstance(this).medicationDao().findByName(drugName);
            } catch (Exception ignored) {}

            List<MedicationScheduleEntity> list = AppDatabaseProvider.getInstance(this).scheduleDao().getByDrugName(drugName);
            long now = System.currentTimeMillis();
            for (MedicationScheduleEntity item : list) {
                com.emergency.patient.scheduling.MedicationReminderScheduler.cancelDoseReminder(this, item);

                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(item.timeMillis);
                c.set(Calendar.HOUR_OF_DAY, hour);
                c.set(Calendar.MINUTE, minute);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                item.timeMillis = c.getTimeInMillis();

                // Re-evaluate pending vs missed if not taken
                if (!"TAKEN".equalsIgnoreCase(item.status)) {
                    item.status = (item.timeMillis > now) ? "PENDING" : "MISSED";
                }
                AppDatabaseProvider.getInstance(this).scheduleDao().update(item);

                // Re-schedule alarm if in future and not taken
                if (!"TAKEN".equalsIgnoreCase(item.status) && item.timeMillis > now) {
                    com.emergency.patient.scheduling.MedicationReminderScheduler.scheduleDoseReminder(this, item, med);
                }
            }

            int h12 = hour % 12 == 0 ? 12 : hour % 12;
            String amPm = hour >= 12 ? "PM" : "AM";
            String toastTime = String.format(Locale.getDefault(), "%02d:%02d %s", h12, minute, amPm);

            runOnUiThread(() -> {
                Toast.makeText(this, "Updated all schedules for " + drugName + " to " + toastTime + " ⏰", Toast.LENGTH_SHORT).show();
                loadRemindersData();
            });
        }).start();
    }

    private void populateHealthReminders(List<HealthReminder> reminders) {
        if (currentFilter == 1) {
            // "Medicines" tab selected - hide screening section
            layoutHealthRemindersList.removeAllViews();
            tvHealthRemindersCount.setText("0");
            tvEmptyHealthReminders.setVisibility(View.GONE);
            return;
        }

        layoutHealthRemindersList.removeAllViews();

        List<HealthReminder> filtered = new ArrayList<>();
        for (HealthReminder r : reminders) {
            filtered.add(r);
        }

        Collections.sort(filtered, (a, b) -> Long.compare(a.getTargetDateMillis(), b.getTargetDateMillis()));
        tvHealthRemindersCount.setText(filtered.size() + (filtered.size() == 1 ? " alert" : " alerts"));

        if (filtered.isEmpty()) {
            tvEmptyHealthReminders.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyHealthReminders.setVisibility(View.GONE);

        for (HealthReminder rem : filtered) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutHealthRemindersList, false);
            ImageView iv = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvTime = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);

            iv.setImageResource(getReminderIcon(rem.getCategory()));
            iv.setColorFilter(ContextCompat.getColor(this, rem.isCompleted() ? R.color.netra_success : R.color.netra_primary_teal));

            tvTitle.setText(rem.getTitle());
            tvTime.setText("Due: " + rem.getFormattedDueDate() + " • " + rem.getFrequency());

            tvStatus.setText(rem.isCompleted() ? "Completed" : "Active");
            tvStatus.setTextColor(ContextCompat.getColor(this, rem.isCompleted() ? R.color.netra_success : R.color.netra_warning));

            row.setOnClickListener(v -> showReminderOptionsDialog(rem));
            layoutHealthRemindersList.addView(row);
        }
    }

    private int getReminderIcon(HealthReminder.Category cat) {
        if (cat == null) return R.drawable.ic_calendar_check;
        switch (cat) {
            case RETINA: return R.drawable.ic_action_retina;
            case KIDNEY: return R.drawable.ic_metric_kidney;
            case HEART: return R.drawable.ic_blood_pressure;
            case FOOT: return R.drawable.ic_metric_foot;
            case GLUCOSE: return R.drawable.ic_metric_glucose;
            default: return R.drawable.ic_calendar_check;
        }
    }

    private void showReminderOptionsDialog(HealthReminder rem) {
        new AlertDialog.Builder(this)
                .setTitle(rem.getTitle())
                .setMessage("Reminder Details:\n" +
                        "• Category: " + rem.getCategory().getDisplayName() + "\n" +
                        "• Due Date: " + rem.getFormattedDueDate() + "\n" +
                        "• Frequency: " + rem.getFrequency() +
                        (rem.getNotes() != null && !rem.getNotes().isEmpty() ? "\n• Notes: " + rem.getNotes() : ""))
                .setPositiveButton(rem.isCompleted() ? "Mark Active" : "Mark Completed", (d, w) -> {
                    Diabetes360Repository.getInstance().toggleReminderCompletion(this, rem.getId());
                    loadRemindersData();
                })
                .setNegativeButton("Delete Reminder", (d, w) -> {
                    Diabetes360Repository.getInstance().deleteReminder(this, rem.getId());
                    Toast.makeText(this, "Reminder deleted.", Toast.LENGTH_SHORT).show();
                    loadRemindersData();
                })
                .setNeutralButton("Close", null)
                .show();
    }

    private void showNewReminderChooser() {
        CharSequence[] options = new CharSequence[]{
                "💊 Add Medication / Insulin (Set daily dose times & alarms)",
                "🩺 Schedule Clinical Screening / Lab Test (Retina, kidney, foot)"
        };

        new AlertDialog.Builder(this)
                .setTitle("Schedule Reminder")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showAddMedicationOptionsDialog();
                    } else {
                        showAddReminderModal();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddMedicationOptionsDialog() {
        CharSequence[] options = new CharSequence[]{
                "✏️ Enter Medicine Manually (Name, dosage & exact times)",
                "📷 Take Photo of Medicine Strip / Box",
                "🖼️ Choose Image from Gallery"
        };

        new AlertDialog.Builder(this)
                .setTitle("Add Medication")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, MedicationOcrActivity.class);
                        intent.putExtra(MedicationOcrActivity.EXTRA_IS_MANUAL, true);
                        startActivity(intent);
                    } else if (which == 1) {
                        launchCameraCapture();
                    } else if (which == 2) {
                        pickGalleryImageLauncher.launch("image/*");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void launchCameraCapture() {
        try {
            java.io.File photoFile = new java.io.File(getCacheDir(), "med_scan_" + System.currentTimeMillis() + ".jpg");
            currentCameraPhotoUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    photoFile
            );
            takeCameraPictureLauncher.launch(currentCameraPhotoUri);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to access camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddReminderModal() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_health_reminder, null);
        Spinner spCategory = v.findViewById(R.id.spinnerReminderCategory);
        EditText etTitle = v.findViewById(R.id.etReminderTitle);
        TextView tvDate = v.findViewById(R.id.tvReminderDate);
        TextView tvTime = v.findViewById(R.id.tvReminderTime);
        Spinner spFreq = v.findViewById(R.id.spinnerFrequency);
        EditText etNotes = v.findViewById(R.id.etReminderNotes);

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Retinal Screening", "Kidney Lab Test (eGFR/UACR)", "Heart & Blood Pressure Check", "Daily Foot Inspection", "Blood Glucose / HbA1c", "Medication Dose", "Doctor Consultation"});
        spCategory.setAdapter(catAdapter);

        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Once", "Daily", "Weekly", "Monthly", "Every 3 Months", "Yearly"});
        spFreq.setAdapter(freqAdapter);

        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1); // default tomorrow
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);

        final long[] selectedMillis = {cal.getTimeInMillis()};
        final int[] selHour = {9};
        final int[] selMinute = {0};

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(cal.getTime()));

        tvDate.setOnClickListener(view -> {
            new DatePickerDialog(this, (p, y, m, d) -> {
                cal.set(Calendar.YEAR, y);
                cal.set(Calendar.MONTH, m);
                cal.set(Calendar.DAY_OF_MONTH, d);
                cal.set(Calendar.HOUR_OF_DAY, selHour[0]);
                cal.set(Calendar.MINUTE, selMinute[0]);
                cal.set(Calendar.SECOND, 0);
                selectedMillis[0] = cal.getTimeInMillis();
                tvDate.setText(sdf.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        if (tvTime != null) {
            tvTime.setText("09:00 AM");
            tvTime.setOnClickListener(view -> {
                new TimePickerDialog(this, (tp, h, m) -> {
                    selHour[0] = h;
                    selMinute[0] = m;
                    cal.set(Calendar.HOUR_OF_DAY, h);
                    cal.set(Calendar.MINUTE, m);
                    cal.set(Calendar.SECOND, 0);
                    selectedMillis[0] = cal.getTimeInMillis();
                    int h12 = h % 12;
                    if (h12 == 0) h12 = 12;
                    tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d %s", h12, m, (h >= 12 ? "PM" : "AM")));
                }, selHour[0], selMinute[0], false).show();
            });
        }

        new AlertDialog.Builder(this)
                .setTitle("Schedule Health Reminder")
                .setView(v)
                .setPositiveButton("Save Reminder", (dialog, which) -> {
                    String title = etTitle.getText().toString().trim();
                    if (title.isEmpty()) {
                        title = (String) spCategory.getSelectedItem();
                    }

                    int catIdx = spCategory.getSelectedItemPosition();
                    HealthReminder.Category cat = HealthReminder.Category.RETINA;
                    if (catIdx == 0) cat = HealthReminder.Category.RETINA;
                    else if (catIdx == 1) cat = HealthReminder.Category.KIDNEY;
                    else if (catIdx == 2) cat = HealthReminder.Category.HEART;
                    else if (catIdx == 3) cat = HealthReminder.Category.FOOT;
                    else if (catIdx >= 4) cat = HealthReminder.Category.GLUCOSE;

                    String freq = (String) spFreq.getSelectedItem();
                    String notes = etNotes.getText().toString().trim();

                    HealthReminder rem = new HealthReminder(cat, title, selectedMillis[0], freq, notes);
                    Diabetes360Repository.getInstance().addReminder(this, rem);

                    // If it is a medication dose, also register in Room schedule with exact selected time
                    if (catIdx == 5) {
                        final String medTitle = title;
                        final long time = selectedMillis[0];
                        new Thread(() -> {
                            AppDatabaseProvider.getInstance(this).scheduleDao().insert(
                                    new MedicationScheduleEntity(medTitle, time, "PENDING")
                            );
                        }).start();
                    }

                    Toast.makeText(this, "Reminder scheduled: " + title, Toast.LENGTH_SHORT).show();
                    loadRemindersData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

