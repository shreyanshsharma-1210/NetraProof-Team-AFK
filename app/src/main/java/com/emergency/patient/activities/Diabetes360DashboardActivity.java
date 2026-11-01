package com.emergency.patient.activities;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.HealthDocumentEntity;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.models.diabetes360.BloodPressureRecord;
import com.emergency.patient.models.diabetes360.Diabetes360Data;
import com.emergency.patient.models.diabetes360.Diabetes360Repository;
import com.emergency.patient.models.diabetes360.DiabetesContext;
import com.emergency.patient.models.diabetes360.FootCheckRecord;
import com.emergency.patient.models.diabetes360.GlucoseRecord;
import com.emergency.patient.models.diabetes360.Hba1cRecord;
import com.emergency.patient.models.diabetes360.HealthReminder;
import com.emergency.patient.models.diabetes360.KidneyRecord;
import com.emergency.patient.models.diabetes360.LipidProfileRecord;
import com.emergency.patient.models.diabetes360.SymptomRecord;
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
 * Diabetes360DashboardActivity — NetraProof Diabetes360 Central Dashboard (Page 07)
 *
 * Provides a unified, clinically responsible overview of:
 *  1. Kidney Health (Page 08 integration)
 *  2. Heart & Vascular Health (Page 09 integration)
 *  3. Foot & Nerve Care (Page 10 integration)
 *  4. Glucose & HbA1c Tracking
 *  5. Independent AI Retinal Screening status
 *  6. Recent Health Activity timeline
 *  7. Cross-module upcoming reminders
 *
 * Implements strict medical safety:
 *  - Zero synthetic risk scores
 *  - Preserves data provenance and independent biomarker integrity
 */
public class Diabetes360DashboardActivity extends AppCompatActivity {

    // Header & Greeting
    private ImageView btnBack;
    private FrameLayout btnNotifications, btnProfileAvatar;
    private TextView tvAvatarInitials, tvWelcomePatientName, tvCurrentDateString;
    private View viewNotifDot;

    // Context
    private View btnEditContext, layoutDiabetesContextBanner;
    private TextView tvDiabetesContextSummary;

    // Overview 4 Cards
    private View cardOverviewKidney, cardOverviewHeart, cardOverviewFoot, cardOverviewGlucose;
    private TextView tvOverviewKidneyValue, tvOverviewKidneyDate;
    private TextView tvOverviewHeartValue, tvOverviewHeartDate;
    private TextView tvOverviewFootValue, tvOverviewFootDate;
    private TextView tvOverviewGlucoseValue, tvOverviewGlucoseDate;

    // Retinal Screening Integration
    private View cardRetinalIntegration;
    private TextView badgeRetinaStatus, tvRetinaScreeningSummary;
    private MaterialButton btnStartNewScreening, btnViewRetinaReport;

    // Timeline & Reminders
    private TextView tvTimelineTotal, tvEmptyActivityTimeline;
    private LinearLayout layoutActivityContainer;

    private View btnAddNewReminder;
    private TextView tvEmptyRemindersNotice;
    private LinearLayout layoutRemindersContainer;

    private String patientUuid;

    private static class ActivityItem {
        long timestampMillis;
        String title;
        String source;
        String status;
        int iconRes;
        int iconTintRes;
        Runnable onClick;

        ActivityItem(long timestampMillis, String title, String source, String status, int iconRes, int iconTintRes, Runnable onClick) {
            this.timestampMillis = timestampMillis;
            this.title = title;
            this.source = source;
            this.status = status;
            this.iconRes = iconRes;
            this.iconTintRes = iconTintRes;
            this.onClick = onClick;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diabetes360_dashboard);

        patientUuid = TokenManager.getUUID(this);

        initViews();
        setupClickListeners();
        loadDashboard();
        com.emergency.patient.utils.NetraProfileHeaderHelper.setup(this);
        com.emergency.patient.utils.NetraBottomNavHelper.setup(this, com.emergency.patient.utils.NetraBottomNavHelper.TAB_DIABETES360);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboard();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnNotifications = findViewById(R.id.btnNotifications);
        btnProfileAvatar = findViewById(R.id.btnProfileAvatar);
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        tvWelcomePatientName = findViewById(R.id.tvWelcomePatientName);
        tvCurrentDateString = findViewById(R.id.tvCurrentDateString);
        viewNotifDot = findViewById(R.id.viewNotifDot);

        // Context
        btnEditContext = findViewById(R.id.btnEditContext);
        layoutDiabetesContextBanner = findViewById(R.id.layoutDiabetesContextBanner);
        tvDiabetesContextSummary = findViewById(R.id.tvDiabetesContextSummary);

        // 4 Overview Cards
        cardOverviewKidney = findViewById(R.id.cardOverviewKidney);
        cardOverviewHeart = findViewById(R.id.cardOverviewHeart);
        cardOverviewFoot = findViewById(R.id.cardOverviewFoot);
        cardOverviewGlucose = findViewById(R.id.cardOverviewGlucose);

        tvOverviewKidneyValue = findViewById(R.id.tvOverviewKidneyValue);
        tvOverviewKidneyDate = findViewById(R.id.tvOverviewKidneyDate);

        tvOverviewHeartValue = findViewById(R.id.tvOverviewHeartValue);
        tvOverviewHeartDate = findViewById(R.id.tvOverviewHeartDate);

        tvOverviewFootValue = findViewById(R.id.tvOverviewFootValue);
        tvOverviewFootDate = findViewById(R.id.tvOverviewFootDate);

        tvOverviewGlucoseValue = findViewById(R.id.tvOverviewGlucoseValue);
        tvOverviewGlucoseDate = findViewById(R.id.tvOverviewGlucoseDate);

        // Retinal Integration
        cardRetinalIntegration = findViewById(R.id.cardRetinalIntegration);
        badgeRetinaStatus = findViewById(R.id.badgeRetinaStatus);
        tvRetinaScreeningSummary = findViewById(R.id.tvRetinaScreeningSummary);
        btnStartNewScreening = findViewById(R.id.btnStartNewScreening);
        btnViewRetinaReport = findViewById(R.id.btnViewRetinaReport);

        // Timeline
        tvTimelineTotal = findViewById(R.id.tvTimelineTotal);
        tvEmptyActivityTimeline = findViewById(R.id.tvEmptyActivityTimeline);
        layoutActivityContainer = findViewById(R.id.layoutActivityContainer);

        // Reminders
        btnAddNewReminder = findViewById(R.id.btnAddNewReminder);
        tvEmptyRemindersNotice = findViewById(R.id.tvEmptyRemindersNotice);
        layoutRemindersContainer = findViewById(R.id.layoutRemindersContainer);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnNotifications.setOnClickListener(v -> startActivity(new Intent(this, NotificationsRemindersActivity.class)));
        btnProfileAvatar.setOnClickListener(v -> startActivity(new Intent(this, EditProfileActivity.class)));

        // Navigation to Module Pages
        cardOverviewKidney.setOnClickListener(v -> startActivity(new Intent(this, KidneyHealthActivity.class)));
        cardOverviewHeart.setOnClickListener(v -> startActivity(new Intent(this, HeartHealthActivity.class)));
        cardOverviewFoot.setOnClickListener(v -> startActivity(new Intent(this, FootNerveCareActivity.class)));

        cardOverviewGlucose.setOnClickListener(v -> startActivity(new Intent(this, GlucoseHba1cTrackingActivity.class)));

        // Context editor
        btnEditContext.setOnClickListener(v -> showEditContextDialog());
        layoutDiabetesContextBanner.setOnClickListener(v -> showEditContextDialog());

        // Retinal Screening Navigation
        btnStartNewScreening.setOnClickListener(v -> startActivity(new Intent(this, RetinaScreeningActivity.class)));
        btnViewRetinaReport.setOnClickListener(v -> startActivity(new Intent(this, RetinaScreeningReportActivity.class)));
        cardRetinalIntegration.setOnClickListener(v -> startActivity(new Intent(this, RetinaScreeningActivity.class)));

        btnAddNewReminder.setOnClickListener(v -> showAddReminderDialog());
    }

    private void loadDashboard() {
        // Date String
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault());
        tvCurrentDateString.setText(sdf.format(new Date()));

        // Authenticated Profile
        new Thread(() -> {
            PatientEntity patient = null;
            try {
                patient = AppDatabaseProvider.getInstance(this).patientDao().getPatient(patientUuid);
            } catch (Exception ignored) {}

            final PatientEntity finalPat = patient;
            runOnUiThread(() -> {
                if (finalPat != null && finalPat.fullName != null && !finalPat.fullName.trim().isEmpty()) {
                    String name = finalPat.fullName.trim();
                    tvWelcomePatientName.setText("Welcome, " + name);

                    String[] parts = name.split("\\s+");
                    if (parts.length >= 2 && parts[0].length() > 0 && parts[1].length() > 0) {
                        tvAvatarInitials.setText(("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase());
                    } else if (parts.length >= 1 && parts[0].length() > 0) {
                        tvAvatarInitials.setText(("" + parts[0].charAt(0)).toUpperCase());
                    }
                } else {
                    tvWelcomePatientName.setText("Welcome, Patient");
                    tvAvatarInitials.setText("NP");
                }
            });
        }).start();

        // Load Diabetes360 Data
        Diabetes360Data data = Diabetes360Repository.getInstance().getData(this, patientUuid);

        // 1. Diabetes Context Banner
        DiabetesContext ctx = data.getDiabetesContext();
        if (ctx != null && !"Not specified".equalsIgnoreCase(ctx.getDiabetesType())) {
            String durStr = ctx.getDurationYears() > 0 ? " • " + ctx.getDurationYears() + " yrs" : "";
            String chkStr = ctx.getLastCheckupMillis() > 0 ? " • Last exam: " + ctx.getFormattedLastCheckup() : "";
            tvDiabetesContextSummary.setText(ctx.getDiabetesType() + durStr + chkStr);
        } else {
            tvDiabetesContextSummary.setText("Optional health context: Diabetes classification not specified. Tap to configure.");
        }

        // 2. Kidney Overview Card
        if (!data.getKidneyRecords().isEmpty()) {
            KidneyRecord k = data.getKidneyRecords().get(0);
            tvOverviewKidneyValue.setText(k.getTestType().name() + ": " + k.getFormattedValueWithUnit());
            tvOverviewKidneyDate.setText("Recorded " + k.getFormattedDate() + " (" + k.getSource() + ")");
        } else {
            tvOverviewKidneyValue.setText("Test record missing");
            tvOverviewKidneyDate.setText("No kidney laboratory tests logged");
        }

        // 3. Heart & Vascular Overview Card
        if (!data.getBloodPressureRecords().isEmpty()) {
            BloodPressureRecord bp = data.getBloodPressureRecords().get(0);
            tvOverviewHeartValue.setText("BP: " + bp.getFormattedReading());
            tvOverviewHeartDate.setText("Recorded " + bp.getFormattedDate() + " (" + bp.getSource() + ")");
        } else if (!data.getLipidProfileRecords().isEmpty()) {
            LipidProfileRecord lip = data.getLipidProfileRecords().get(0);
            tvOverviewHeartValue.setText(lip.getSummaryText());
            tvOverviewHeartDate.setText("Tested " + lip.getFormattedDate());
        } else {
            tvOverviewHeartValue.setText("Test record missing");
            tvOverviewHeartDate.setText("No blood pressure or lipid records");
        }

        // 4. Foot & Nerve Overview Card
        if (!data.getFootCheckRecords().isEmpty()) {
            FootCheckRecord fc = data.getFootCheckRecords().get(0);
            tvOverviewFootValue.setText("Check: " + fc.getSide() + " (" + fc.getSkinCondition() + ")");
            tvOverviewFootDate.setText("Inspected " + fc.getFormattedDate());
        } else if (!data.getSymptomRecords().isEmpty()) {
            SymptomRecord sym = data.getSymptomRecords().get(0);
            tvOverviewFootValue.setText("Symptom: " + sym.getSymptomType().getDisplayName());
            tvOverviewFootDate.setText("Reported " + sym.getFormattedDate() + " • " + sym.getStatus());
        } else {
            tvOverviewFootValue.setText("No check recorded");
            tvOverviewFootDate.setText("No foot inspections or symptoms logged");
        }

        // 5. Glucose Overview Card
        SharedPreferences prefs = getSharedPreferences("netra_health_metrics", Context.MODE_PRIVATE);
        String gVal = prefs.getString("latest_glucose_value", null);
        String gTime = prefs.getString("latest_glucose_timestamp", null);
        if (gVal != null && !gVal.isEmpty()) {
            tvOverviewGlucoseValue.setText(gVal + " mg/dL");
            tvOverviewGlucoseDate.setText(gTime != null ? gTime : "Recent reading");
        } else {
            tvOverviewGlucoseValue.setText("Test record missing");
            tvOverviewGlucoseDate.setText("No blood glucose logged yet");
        }

        // 6. Timeline Population
        populateActivityTimeline(data);

        // 7. Reminders Population
        populateReminders(data.getReminders());
    }

    private void populateActivityTimeline(Diabetes360Data data) {
        layoutActivityContainer.removeAllViews();
        List<ActivityItem> items = new ArrayList<>();

        // From Kidney
        for (KidneyRecord k : data.getKidneyRecords()) {
            items.add(new ActivityItem(
                    k.getTestDateMillis(),
                    k.getTestType().getDisplayName() + ": " + k.getFormattedValueWithUnit(),
                    "Kidney Health • " + k.getSource(),
                    "Recorded",
                    R.drawable.ic_metric_kidney,
                    R.color.netra_primary_teal,
                    () -> startActivity(new Intent(this, KidneyHealthActivity.class))
            ));
        }

        // From BP
        for (BloodPressureRecord bp : data.getBloodPressureRecords()) {
            items.add(new ActivityItem(
                    bp.getMeasurementDateMillis(),
                    "Blood Pressure: " + bp.getFormattedReading(),
                    "Cardiovascular • " + bp.getSource(),
                    "Logged",
                    R.drawable.ic_blood_pressure,
                    R.color.netra_primary_teal,
                    () -> startActivity(new Intent(this, HeartHealthActivity.class))
            ));
        }

        // From Lipids
        for (LipidProfileRecord lip : data.getLipidProfileRecords()) {
            items.add(new ActivityItem(
                    lip.getTestDateMillis(),
                    "Lipid Profile: " + lip.getSummaryText(),
                    "Cardiovascular • " + lip.getSource(),
                    "Report",
                    R.drawable.ic_lipid,
                    R.color.netra_orange,
                    () -> startActivity(new Intent(this, HeartHealthActivity.class))
            ));
        }

        // From Foot Checks
        for (FootCheckRecord fc : data.getFootCheckRecords()) {
            items.add(new ActivityItem(
                    fc.getCheckDateMillis(),
                    "Foot Inspection: " + fc.getSide(),
                    "Foot & Nerve Care • " + fc.getSource(),
                    fc.getSkinCondition(),
                    R.drawable.ic_metric_foot,
                    R.color.netra_primary_teal,
                    () -> startActivity(new Intent(this, FootNerveCareActivity.class))
            ));
        }

        // From Symptoms
        for (SymptomRecord sym : data.getSymptomRecords()) {
            items.add(new ActivityItem(
                    sym.getDateMillis(),
                    "Symptom: " + sym.getSymptomType().getDisplayName(),
                    "Foot & Nerve Care • " + sym.getBodyLocation(),
                    sym.getStatus(),
                    R.drawable.ic_nerve,
                    R.color.netra_warning,
                    () -> startActivity(new Intent(this, FootNerveCareActivity.class))
            ));
        }

        // From Glucose Records
        for (GlucoseRecord g : data.getGlucoseRecords()) {
            items.add(new ActivityItem(
                    g.getDateMillis(),
                    "Blood Glucose: " + g.getFormattedValueWithUnit(),
                    "Glucose Tracking • " + g.getReadingContext().getDisplayName(),
                    g.getSource(),
                    R.drawable.ic_metric_glucose,
                    R.color.netra_primary_teal,
                    () -> startActivity(new Intent(this, GlucoseHba1cTrackingActivity.class))
            ));
        }

        // From HbA1c Records
        for (Hba1cRecord a : data.getHba1cRecords()) {
            items.add(new ActivityItem(
                    a.getTestDateMillis(),
                    "HbA1c Lab Test: " + a.getFormattedValueWithUnit(),
                    "Glucose Tracking • " + a.getSource(),
                    "Laboratory",
                    R.drawable.ic_metric_hba1c,
                    R.color.netra_orange,
                    () -> startActivity(new Intent(this, GlucoseHba1cTrackingActivity.class))
            ));
        }

        // Sort reverse chronological
        Collections.sort(items, (a, b) -> Long.compare(b.timestampMillis, a.timestampMillis));

        tvTimelineTotal.setText(items.size() + (items.size() == 1 ? " event" : " events"));

        if (items.isEmpty()) {
            tvEmptyActivityTimeline.setVisibility(View.VISIBLE);
            layoutActivityContainer.addView(tvEmptyActivityTimeline);
            return;
        }

        tvEmptyActivityTimeline.setVisibility(View.GONE);

        // Limit to 10 most recent for performance
        int count = Math.min(items.size(), 10);
        for (int i = 0; i < count; i++) {
            ActivityItem item = items.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutActivityContainer, false);

            ImageView iv = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvT = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvD = row.findViewById(R.id.tvActivityRowTime);
            TextView tvS = row.findViewById(R.id.tvActivityRowStatus);

            iv.setImageResource(item.iconRes);
            iv.setColorFilter(ContextCompat.getColor(this, item.iconTintRes));

            tvT.setText(item.title);
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            tvD.setText(sdf.format(new Date(item.timestampMillis)) + " • " + item.source);

            tvS.setText(item.status);
            tvS.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

            row.setOnClickListener(v -> {
                if (item.onClick != null) item.onClick.run();
            });

            layoutActivityContainer.addView(row);
        }
    }

    private void populateReminders(List<HealthReminder> reminders) {
        layoutRemindersContainer.removeAllViews();
        List<HealthReminder> active = new ArrayList<>();
        for (HealthReminder r : reminders) {
            if (!r.isCompleted()) {
                active.add(r);
            }
        }

        if (active.isEmpty()) {
            tvEmptyRemindersNotice.setVisibility(View.VISIBLE);
            layoutRemindersContainer.addView(tvEmptyRemindersNotice);
            return;
        }

        tvEmptyRemindersNotice.setVisibility(View.GONE);

        Collections.sort(active, (a, b) -> Long.compare(a.getTargetDateMillis(), b.getTargetDateMillis()));

        for (HealthReminder rem : active) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutRemindersContainer, false);
            ImageView iv = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvT = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvD = row.findViewById(R.id.tvActivityRowTime);
            TextView tvS = row.findViewById(R.id.tvActivityRowStatus);

            iv.setImageResource(R.drawable.ic_calendar_check);
            iv.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));

            tvT.setText(rem.getTitle());
            tvD.setText("Due: " + rem.getFormattedDueDate() + " • " + rem.getCategory().getDisplayName());
            tvS.setText("Pending");
            tvS.setTextColor(ContextCompat.getColor(this, R.color.netra_warning));

            row.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(rem.getTitle())
                        .setMessage("Category: " + rem.getCategory().getDisplayName() + "\nDue: " + rem.getFormattedDueDate() + "\nFrequency: " + rem.getFrequency())
                        .setPositiveButton("Mark Completed", (d, w) -> {
                            Diabetes360Repository.getInstance().toggleReminderCompletion(this, rem.getId());
                            loadDashboard();
                        })
                        .setNegativeButton("Delete Reminder", (d, w) -> {
                            Diabetes360Repository.getInstance().deleteReminder(this, rem.getId());
                            loadDashboard();
                        })
                        .setNeutralButton("Close", null)
                        .show();
            });

            layoutRemindersContainer.addView(row);
        }
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    private void showEditContextDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_edit_diabetes_context, null);
        Spinner spType = v.findViewById(R.id.spinnerDiabetesType);
        EditText etDur = v.findViewById(R.id.etDurationYears);
        TextView tvDate = v.findViewById(R.id.tvContextCheckupDate);
        EditText etNotes = v.findViewById(R.id.etContextNotes);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Type 2 Diabetes", "Type 1 Diabetes", "Prediabetes", "Gestational Diabetes", "Not specified"}
        );
        spType.setAdapter(typeAdapter);

        Diabetes360Data data = Diabetes360Repository.getInstance().getData(this, patientUuid);
        DiabetesContext ctx = data.getDiabetesContext();

        if (ctx != null) {
            for (int i = 0; i < typeAdapter.getCount(); i++) {
                if (typeAdapter.getItem(i).equalsIgnoreCase(ctx.getDiabetesType())) {
                    spType.setSelection(i);
                    break;
                }
            }
            if (ctx.getDurationYears() > 0) {
                etDur.setText(String.valueOf(ctx.getDurationYears()));
            }
            if (ctx.getLastCheckupMillis() > 0) {
                tvDate.setText(ctx.getFormattedLastCheckup());
            } else {
                tvDate.setText("Select date if available");
            }
            etNotes.setText(ctx.getRelevantHistoryNotes());
        }

        final Calendar cal = Calendar.getInstance();
        final long[] selectedMillis = {ctx != null ? ctx.getLastCheckupMillis() : 0};

        tvDate.setOnClickListener(view -> {
            new DatePickerDialog(this, (p, y, m, d) -> {
                cal.set(y, m, d);
                selectedMillis[0] = cal.getTimeInMillis();
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                tvDate.setText(sdf.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        new AlertDialog.Builder(this)
                .setTitle("Diabetes Health Context")
                .setView(v)
                .setPositiveButton("Save Context", (dialog, which) -> {
                    String selectedType = (String) spType.getSelectedItem();
                    int dur = 0;
                    try {
                        dur = Integer.parseInt(etDur.getText().toString().trim());
                    } catch (Exception ignored) {}

                    String notes = etNotes.getText().toString().trim();

                    DiabetesContext newCtx = new DiabetesContext(patientUuid, selectedType, dur, selectedMillis[0], notes);
                    Diabetes360Repository.getInstance().updateDiabetesContext(this, newCtx);
                    Toast.makeText(this, "Health context saved.", Toast.LENGTH_SHORT).show();
                    loadDashboard();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLogGlucoseDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Log Blood Glucose");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 12);

        final RadioGroup rgType = new RadioGroup(this);
        RadioButton rbFasting = new RadioButton(this);
        rbFasting.setText("Fasting (Before Breakfast)");
        rbFasting.setChecked(true);
        RadioButton rbPost = new RadioButton(this);
        rbPost.setText("Postprandial (2h After Meal)");
        rgType.addView(rbFasting);
        rgType.addView(rbPost);
        layout.addView(rgType);

        final EditText etReading = new EditText(this);
        etReading.setHint("e.g. 110 mg/dL");
        etReading.setInputType(InputType.TYPE_CLASS_NUMBER);
        etReading.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(etReading);

        builder.setView(layout);

        builder.setPositiveButton("Save Reading", (dialog, which) -> {
            String val = etReading.getText().toString().trim();
            if (val.isEmpty()) {
                Toast.makeText(this, "Please enter a valid glucose number", Toast.LENGTH_SHORT).show();
                return;
            }

            String type = rbFasting.isChecked() ? "Fasting" : "Postprandial";
            String timestamp = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(new Date());

            getSharedPreferences("netra_health_metrics", Context.MODE_PRIVATE)
                    .edit()
                    .putString("latest_glucose_value", val)
                    .putString("latest_glucose_type", type)
                    .putString("latest_glucose_timestamp", timestamp + " (" + type + ")")
                    .apply();

            Toast.makeText(this, "Glucose logged: " + val + " mg/dL (" + type + ")", Toast.LENGTH_SHORT).show();
            loadDashboard();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showAddReminderDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_health_reminder, null);
        EditText etTitle = v.findViewById(R.id.etReminderTitle);
        TextView tvDate = v.findViewById(R.id.tvReminderDate);
        Spinner spinnerFreq = v.findViewById(R.id.spinnerFrequency);
        EditText etNotes = v.findViewById(R.id.etReminderNotes);

        etTitle.setHint("e.g. Annual Retinal Exam, HbA1c Lab Test");

        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"One-Time", "Weekly", "Monthly", "Every 3 Months", "Every 6 Months", "Annual"}
        );
        spinnerFreq.setAdapter(freqAdapter);

        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, 1);
        final long[] selectedMillis = {cal.getTimeInMillis()};
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(cal.getTime()));

        tvDate.setOnClickListener(view -> {
            new DatePickerDialog(this, (picker, year, month, day) -> {
                cal.set(year, month, day);
                selectedMillis[0] = cal.getTimeInMillis();
                tvDate.setText(sdf.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        new AlertDialog.Builder(this)
                .setTitle("Schedule Health Reminder")
                .setView(v)
                .setPositiveButton("Save Reminder", (d, w) -> {
                    String title = etTitle.getText().toString().trim();
                    if (title.isEmpty()) title = "Health Checkup";
                    String freq = (String) spinnerFreq.getSelectedItem();
                    String notes = etNotes.getText().toString().trim();

                    HealthReminder rem = new HealthReminder(
                            null, patientUuid, HealthReminder.Category.GLUCOSE, title, selectedMillis[0], freq, notes
                    );
                    Diabetes360Repository.getInstance().addReminder(this, rem);
                    Toast.makeText(this, "Reminder scheduled.", Toast.LENGTH_SHORT).show();
                    loadDashboard();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNotificationsDialog() {
        if (viewNotifDot != null) viewNotifDot.setVisibility(View.GONE);
        new AlertDialog.Builder(this)
                .setTitle("Diabetes360 System Notifications")
                .setMessage(
                        "• All health modules active & synchronized locally\n" +
                        "• Microvascular Screening Engine: v2.4 Ready\n" +
                        "• Kidney & Cardiovascular logs saved securely on device\n" +
                        "• Annual Eye Examination: Recommended annually for diabetic patients"
                )
                .setPositiveButton("Dismiss All", null)
                .show();
    }
}
