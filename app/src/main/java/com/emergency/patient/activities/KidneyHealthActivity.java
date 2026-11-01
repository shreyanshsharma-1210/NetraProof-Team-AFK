package com.emergency.patient.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.core.content.ContextCompat;

import com.emergency.patient.R;
import com.emergency.patient.models.diabetes360.Diabetes360Data;
import com.emergency.patient.models.diabetes360.Diabetes360Repository;
import com.emergency.patient.models.diabetes360.HealthReminder;
import com.emergency.patient.models.diabetes360.KidneyRecord;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.utils.LocalStorageManager;
import com.emergency.patient.views.HealthTrendChartView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * KidneyHealthActivity — NetraProof Diabetes360 Kidney Health (Page 08)
 *
 * Dedicated renal health record organizer for user-provided laboratory measurements and reports.
 * Supports:
 *  - Primary metrics: eGFR, Serum Creatinine, UACR (with units, dates, provenance)
 *  - Interactive historical trend chart (HealthTrendChartView)
 *  - Chronological history with view/delete actions
 *  - Lab report attachment upload
 *  - Scheduled kidney health reminders
 *  - Educational information with strict clinical disclaimers
 */
public class KidneyHealthActivity extends AppCompatActivity {

    private ImageView btnBack, btnHelp;
    private View btnAddRecordHeader, btnUploadReport, btnAddReminder;
    private TextView tvLatestRecordDate, tvHistoryCount, tvEmptyHistory, tvEmptyReminders;

    // Metric Cards
    private TextView tvEgfrValue, tvEgfrUnit, tvEgfrDateAndPrev, badgeEgfrSource;
    private TextView tvCreatinineValue, tvCreatinineUnit, tvCreatinineDateAndPrev, badgeCreatinineSource;
    private TextView tvUacrValue, tvUacrUnit, tvUacrDateAndPrev, badgeUacrSource;

    // Trend Chart & Tabs
    private HealthTrendChartView chartKidneyTrends;
    private TextView btnTabEgfr, btnTabCreatinine, btnTabUacr;
    private KidneyRecord.TestType activeTrendType = KidneyRecord.TestType.EGFR;

    // Containers
    private LinearLayout layoutKidneyHistoryContainer;
    private LinearLayout layoutKidneyRemindersContainer;

    private String patientUuid;
    private ActivityResultLauncher<String> reportPickerLauncher;
    private String pendingAttachmentPath = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kidney_health);

        patientUuid = TokenManager.getUUID(this);

        initViews();
        setupClickListeners();
        setupAttachmentPicker();
        loadKidneyData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadKidneyData();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHelp = findViewById(R.id.btnHelp);
        btnAddRecordHeader = findViewById(R.id.btnAddRecordHeader);
        btnUploadReport = findViewById(R.id.btnUploadReport);
        btnAddReminder = findViewById(R.id.btnAddReminder);

        tvLatestRecordDate = findViewById(R.id.tvLatestRecordDate);
        tvHistoryCount = findViewById(R.id.tvHistoryCount);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);
        tvEmptyReminders = findViewById(R.id.tvEmptyReminders);

        // Cards
        tvEgfrValue = findViewById(R.id.tvEgfrValue);
        tvEgfrUnit = findViewById(R.id.tvEgfrUnit);
        tvEgfrDateAndPrev = findViewById(R.id.tvEgfrDateAndPrev);
        badgeEgfrSource = findViewById(R.id.badgeEgfrSource);

        tvCreatinineValue = findViewById(R.id.tvCreatinineValue);
        tvCreatinineUnit = findViewById(R.id.tvCreatinineUnit);
        tvCreatinineDateAndPrev = findViewById(R.id.tvCreatinineDateAndPrev);
        badgeCreatinineSource = findViewById(R.id.badgeCreatinineSource);

        tvUacrValue = findViewById(R.id.tvUacrValue);
        tvUacrUnit = findViewById(R.id.tvUacrUnit);
        tvUacrDateAndPrev = findViewById(R.id.tvUacrDateAndPrev);
        badgeUacrSource = findViewById(R.id.badgeUacrSource);

        // Chart & Tabs
        chartKidneyTrends = findViewById(R.id.chartKidneyTrends);
        btnTabEgfr = findViewById(R.id.btnTabEgfr);
        btnTabCreatinine = findViewById(R.id.btnTabCreatinine);
        btnTabUacr = findViewById(R.id.btnTabUacr);

        layoutKidneyHistoryContainer = findViewById(R.id.layoutKidneyHistoryContainer);
        layoutKidneyRemindersContainer = findViewById(R.id.layoutKidneyRemindersContainer);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnHelp.setOnClickListener(v -> showEducationalDialog());
        btnAddRecordHeader.setOnClickListener(v -> showAddRecordDialog(null));
        btnUploadReport.setOnClickListener(v -> {
            if (reportPickerLauncher != null) {
                reportPickerLauncher.launch("*/*");
            }
        });
        btnAddReminder.setOnClickListener(v -> showAddReminderDialog());

        // Trend tab selection
        btnTabEgfr.setOnClickListener(v -> selectTrendTab(KidneyRecord.TestType.EGFR));
        btnTabCreatinine.setOnClickListener(v -> selectTrendTab(KidneyRecord.TestType.SERUM_CREATININE));
        btnTabUacr.setOnClickListener(v -> selectTrendTab(KidneyRecord.TestType.UACR));

        findViewById(R.id.cardEgfr).setOnClickListener(v -> showAddRecordDialog(KidneyRecord.TestType.EGFR));
        findViewById(R.id.cardCreatinine).setOnClickListener(v -> showAddRecordDialog(KidneyRecord.TestType.SERUM_CREATININE));
        findViewById(R.id.cardUacr).setOnClickListener(v -> showAddRecordDialog(KidneyRecord.TestType.UACR));
    }

    private void setupAttachmentPicker() {
        reportPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        handleSelectedDocument(uri);
                    }
                }
        );
    }

    private void handleSelectedDocument(Uri uri) {
        String savedPath = LocalStorageManager.saveFileToInternalStorage(this, uri, "kidney_report.pdf");
        if (savedPath != null) {
            pendingAttachmentPath = savedPath;
            Toast.makeText(this, "Kidney report attached. Enter lab values to save.", Toast.LENGTH_SHORT).show();
            showAddRecordDialog(null);
        } else {
            Toast.makeText(this, "Unable to import document. Please retry.", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectTrendTab(KidneyRecord.TestType type) {
        activeTrendType = type;

        btnTabEgfr.setBackgroundResource(type == KidneyRecord.TestType.EGFR ? R.drawable.bg_mode_pill_selected : R.drawable.bg_mode_pill_unselected);
        btnTabEgfr.setTextColor(ContextCompat.getColor(this, type == KidneyRecord.TestType.EGFR ? android.R.color.white : R.color.netra_text_secondary));

        btnTabCreatinine.setBackgroundResource(type == KidneyRecord.TestType.SERUM_CREATININE ? R.drawable.bg_mode_pill_selected : R.drawable.bg_mode_pill_unselected);
        btnTabCreatinine.setTextColor(ContextCompat.getColor(this, type == KidneyRecord.TestType.SERUM_CREATININE ? android.R.color.white : R.color.netra_text_secondary));

        btnTabUacr.setBackgroundResource(type == KidneyRecord.TestType.UACR ? R.drawable.bg_mode_pill_selected : R.drawable.bg_mode_pill_unselected);
        btnTabUacr.setTextColor(ContextCompat.getColor(this, type == KidneyRecord.TestType.UACR ? android.R.color.white : R.color.netra_text_secondary));

        updateTrendChart();
    }

    private void loadKidneyData() {
        Diabetes360Data data = Diabetes360Repository.getInstance().getData(this, patientUuid);
        List<KidneyRecord> records = data.getKidneyRecords();

        // 1. Update Latest Header
        if (!records.isEmpty()) {
            tvLatestRecordDate.setText("Latest test: " + records.get(0).getFormattedDate() + " (" + records.get(0).getSource() + ")");
        } else {
            tvLatestRecordDate.setText("No kidney test records found");
        }

        // 2. Populate eGFR Card
        populateMetricCard(
                records, KidneyRecord.TestType.EGFR,
                tvEgfrValue, tvEgfrUnit, tvEgfrDateAndPrev, badgeEgfrSource,
                "mL/min/1.73 m²"
        );

        // 3. Populate Serum Creatinine Card
        populateMetricCard(
                records, KidneyRecord.TestType.SERUM_CREATININE,
                tvCreatinineValue, tvCreatinineUnit, tvCreatinineDateAndPrev, badgeCreatinineSource,
                "mg/dL"
        );

        // 4. Populate UACR Card
        populateMetricCard(
                records, KidneyRecord.TestType.UACR,
                tvUacrValue, tvUacrUnit, tvUacrDateAndPrev, badgeUacrSource,
                "mg/g"
        );

        // 5. Populate Trends Chart
        updateTrendChart();

        // 6. Populate History List
        populateHistoryList(records);

        // 7. Populate Reminders
        populateReminders(data.getReminders());
    }

    private void populateMetricCard(List<KidneyRecord> records, KidneyRecord.TestType type,
                                    TextView tvVal, TextView tvUnit, TextView tvDate, TextView badgeSource,
                                    String defaultUnit) {
        KidneyRecord latest = null;
        KidneyRecord previous = null;

        for (KidneyRecord r : records) {
            if (r.getTestType() == type) {
                if (latest == null) {
                    latest = r;
                } else if (previous == null) {
                    previous = r;
                    break;
                }
            }
        }

        if (latest != null) {
            String valStr = (latest.getValue() == Math.floor(latest.getValue())) ?
                    String.format(Locale.getDefault(), "%.0f", latest.getValue()) :
                    String.format(Locale.getDefault(), "%.2f", latest.getValue());

            tvVal.setText(valStr);
            tvUnit.setText(" " + (latest.getUnit() != null && !latest.getUnit().isEmpty() ? latest.getUnit() : defaultUnit));
            tvUnit.setVisibility(View.VISIBLE);

            String prevStr = (previous != null) ? " • Prev: " + previous.getFormattedValueWithUnit() : "";
            tvDate.setText("Recorded on " + latest.getFormattedDate() + " (" + latest.getSource() + ")" + prevStr);

            badgeSource.setText(latest.getSource());
            badgeSource.setVisibility(View.VISIBLE);
        } else {
            tvVal.setText("No test record available");
            tvUnit.setVisibility(View.GONE);
            tvDate.setText("Tap to add your " + type.getDisplayName() + " measurement");
            badgeSource.setVisibility(View.GONE);
        }
    }

    private void updateTrendChart() {
        Diabetes360Data data = Diabetes360Repository.getInstance().getData(this, patientUuid);
        List<HealthTrendChartView.DataPoint> points = new ArrayList<>();
        String unit = activeTrendType.getDefaultUnit();

        for (KidneyRecord r : data.getKidneyRecords()) {
            if (r.getTestType() == activeTrendType) {
                points.add(new HealthTrendChartView.DataPoint(
                        r.getTestDateMillis(),
                        r.getValue(),
                        null,
                        r.getFormattedDate()
                ));
                if (r.getUnit() != null && !r.getUnit().isEmpty()) {
                    unit = r.getUnit();
                }
            }
        }

        chartKidneyTrends.setData(points, unit, false, activeTrendType.getDisplayName(), "");
    }

    private void populateHistoryList(List<KidneyRecord> records) {
        layoutKidneyHistoryContainer.removeAllViews();

        if (records.isEmpty()) {
            tvEmptyHistory.setVisibility(View.VISIBLE);
            tvHistoryCount.setText("0 records");
            return;
        }

        tvEmptyHistory.setVisibility(View.GONE);
        tvHistoryCount.setText(records.size() + (records.size() == 1 ? " record" : " records"));

        for (KidneyRecord record : records) {
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutKidneyHistoryContainer, false);

            ImageView ivIcon = itemView.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = itemView.findViewById(R.id.tvActivityRowTitle);
            TextView tvDate = itemView.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = itemView.findViewById(R.id.tvActivityRowStatus);

            ivIcon.setImageResource(R.drawable.ic_metric_kidney);
            ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));

            tvTitle.setText(record.getTestType().getDisplayName() + ": " + record.getFormattedValueWithUnit());
            String labStr = (record.getLabName() != null && !record.getLabName().isEmpty()) ? " • " + record.getLabName() : "";
            tvDate.setText(record.getFormattedDate() + " • " + record.getSource() + labStr);

            tvStatus.setText(record.getAttachmentPath() != null ? "📎 Report" : "Details");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

            itemView.setOnClickListener(v -> showRecordDetailDialog(record));

            layoutKidneyHistoryContainer.addView(itemView);
        }
    }

    private void populateReminders(List<HealthReminder> allReminders) {
        layoutKidneyRemindersContainer.removeAllViews();
        List<HealthReminder> kidneyReminders = new ArrayList<>();
        for (HealthReminder r : allReminders) {
            if (r.getCategory() == HealthReminder.Category.KIDNEY) {
                kidneyReminders.add(r);
            }
        }

        if (kidneyReminders.isEmpty()) {
            tvEmptyReminders.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyReminders.setVisibility(View.GONE);

        for (HealthReminder rem : kidneyReminders) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutKidneyRemindersContainer, false);

            ImageView ivIcon = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvDate = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);

            ivIcon.setImageResource(R.drawable.ic_calendar_check);
            ivIcon.setColorFilter(ContextCompat.getColor(this, rem.isCompleted() ? R.color.netra_success : R.color.netra_warning));

            tvTitle.setText(rem.getTitle());
            tvDate.setText("Due: " + rem.getFormattedDueDate() + " (" + rem.getFrequency() + ")");
            tvStatus.setText(rem.isCompleted() ? "Completed" : "Active");
            tvStatus.setTextColor(ContextCompat.getColor(this, rem.isCompleted() ? R.color.netra_success : R.color.netra_warning));

            row.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(rem.getTitle())
                        .setMessage("Scheduled Due Date: " + rem.getFormattedDueDate() + "\nFrequency: " + rem.getFrequency() + (rem.getNotes() != null ? "\nNotes: " + rem.getNotes() : ""))
                        .setPositiveButton(rem.isCompleted() ? "Mark Pending" : "Mark Done", (d, w) -> {
                            Diabetes360Repository.getInstance().toggleReminderCompletion(this, rem.getId());
                            loadKidneyData();
                        })
                        .setNegativeButton("Delete", (d, w) -> {
                            Diabetes360Repository.getInstance().deleteReminder(this, rem.getId());
                            loadKidneyData();
                        })
                        .setNeutralButton("Close", null)
                        .show();
            });

            layoutKidneyRemindersContainer.addView(row);
        }
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    private void showAddRecordDialog(@Nullable KidneyRecord.TestType preselectedType) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_kidney_record, null);

        Spinner spinnerType = dialogView.findViewById(R.id.spinnerTestType);
        EditText etValue = dialogView.findViewById(R.id.etMeasurementValue);
        EditText etUnit = dialogView.findViewById(R.id.etMeasurementUnit);
        TextView tvDatePicked = dialogView.findViewById(R.id.tvTestDatePicked);
        Spinner spinnerSource = dialogView.findViewById(R.id.spinnerSource);
        EditText etLabName = dialogView.findViewById(R.id.etLabName);
        EditText etNotes = dialogView.findViewById(R.id.etNotes);
        TextView tvAttachmentStatus = dialogView.findViewById(R.id.tvAttachmentStatus);

        // Populate Types
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"eGFR (mL/min/1.73 m²)", "Serum Creatinine (mg/dL)", "UACR (mg/g)", "Other Kidney Test"}
        );
        spinnerType.setAdapter(typeAdapter);

        if (preselectedType != null) {
            switch (preselectedType) {
                case EGFR: spinnerType.setSelection(0); etUnit.setText("mL/min/1.73 m²"); break;
                case SERUM_CREATININE: spinnerType.setSelection(1); etUnit.setText("mg/dL"); break;
                case UACR: spinnerType.setSelection(2); etUnit.setText("mg/g"); break;
                case OTHER: spinnerType.setSelection(3); break;
            }
        }

        // Sources
        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Manual Entry", "Uploaded Lab Report", "Connected Clinical Lab"}
        );
        spinnerSource.setAdapter(sourceAdapter);

        // Date Picker setup
        final Calendar cal = Calendar.getInstance();
        final long[] selectedMillis = {cal.getTimeInMillis()};
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        tvDatePicked.setText(sdf.format(cal.getTime()));

        tvDatePicked.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                cal.set(year, month, dayOfMonth);
                selectedMillis[0] = cal.getTimeInMillis();
                tvDatePicked.setText(sdf.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        if (pendingAttachmentPath != null) {
            tvAttachmentStatus.setText("Attached: " + pendingAttachmentPath.substring(pendingAttachmentPath.lastIndexOf('/') + 1));
            tvAttachmentStatus.setVisibility(View.VISIBLE);
            spinnerSource.setSelection(1); // Uploaded Report
        }

        new AlertDialog.Builder(this)
                .setTitle("Add Kidney Test Record")
                .setView(dialogView)
                .setPositiveButton("Save Record", (dialog, which) -> {
                    String valStr = etValue.getText().toString().trim();
                    if (valStr.isEmpty()) {
                        Toast.makeText(this, "Please enter a valid numeric measurement.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double val;
                    try {
                        val = Double.parseDouble(valStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid number format.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    KidneyRecord.TestType type;
                    int sel = spinnerType.getSelectedItemPosition();
                    if (sel == 0) type = KidneyRecord.TestType.EGFR;
                    else if (sel == 1) type = KidneyRecord.TestType.SERUM_CREATININE;
                    else if (sel == 2) type = KidneyRecord.TestType.UACR;
                    else type = KidneyRecord.TestType.OTHER;

                    String unit = etUnit.getText().toString().trim();
                    if (unit.isEmpty()) unit = type.getDefaultUnit();

                    String src = (String) spinnerSource.getSelectedItem();
                    String lab = etLabName.getText().toString().trim();
                    String notes = etNotes.getText().toString().trim();

                    KidneyRecord newRecord = new KidneyRecord(
                            null, patientUuid, type, val, unit, selectedMillis[0], src, lab, notes, pendingAttachmentPath
                    );

                    Diabetes360Repository.getInstance().addKidneyRecord(this, newRecord);
                    pendingAttachmentPath = null;
                    Toast.makeText(this, "Kidney record saved successfully.", Toast.LENGTH_SHORT).show();
                    loadKidneyData();
                })
                .setNegativeButton("Cancel", (d, w) -> pendingAttachmentPath = null)
                .show();
    }

    private void showRecordDetailDialog(KidneyRecord record) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(record.getTestType().getDisplayName());

        StringBuilder sb = new StringBuilder();
        sb.append("Measurement: ").append(record.getFormattedValueWithUnit()).append("\n");
        sb.append("Test Date: ").append(record.getFormattedDate()).append("\n");
        sb.append("Source: ").append(record.getSource()).append("\n");
        if (record.getLabName() != null && !record.getLabName().isEmpty()) {
            sb.append("Laboratory: ").append(record.getLabName()).append("\n");
        }
        if (record.getNotes() != null && !record.getNotes().isEmpty()) {
            sb.append("Notes: ").append(record.getNotes()).append("\n");
        }
        if (record.getAttachmentPath() != null) {
            sb.append("Attached Report: Available on device\n");
        }
        sb.append("\nRecord ID: ").append(record.getId());

        builder.setMessage(sb.toString());
        builder.setPositiveButton("Close", null);
        builder.setNegativeButton("Delete Record", (dialog, which) -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Record?")
                    .setMessage("Are you sure you want to delete this " + record.getTestType().getDisplayName() + " measurement?")
                    .setPositiveButton("Delete", (d, w) -> {
                        Diabetes360Repository.getInstance().deleteKidneyRecord(this, record.getId());
                        Toast.makeText(this, "Record deleted.", Toast.LENGTH_SHORT).show();
                        loadKidneyData();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        builder.show();
    }

    private void showAddReminderDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_health_reminder, null);
        EditText etTitle = v.findViewById(R.id.etReminderTitle);
        TextView tvDate = v.findViewById(R.id.tvReminderDate);
        Spinner spinnerFreq = v.findViewById(R.id.spinnerFrequency);
        EditText etNotes = v.findViewById(R.id.etReminderNotes);

        etTitle.setText("Kidney Function Blood Test (eGFR / Creatinine)");

        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"One-Time", "Every 3 Months", "Every 6 Months", "Annual"}
        );
        spinnerFreq.setAdapter(freqAdapter);

        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, 3); // Default 3 months out
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
                .setTitle("Schedule Kidney Health Reminder")
                .setView(v)
                .setPositiveButton("Save Reminder", (d, w) -> {
                    String title = etTitle.getText().toString().trim();
                    if (title.isEmpty()) title = "Kidney Health Check";
                    String freq = (String) spinnerFreq.getSelectedItem();
                    String notes = etNotes.getText().toString().trim();

                    HealthReminder rem = new HealthReminder(
                            null, patientUuid, HealthReminder.Category.KIDNEY, title, selectedMillis[0], freq, notes
                    );
                    Diabetes360Repository.getInstance().addReminder(this, rem);
                    Toast.makeText(this, "Reminder scheduled.", Toast.LENGTH_SHORT).show();
                    loadKidneyData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEducationalDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Understanding Kidney Tests")
                .setMessage(
                        "Diabetic kidney screening relies on complementary biochemical markers:\n\n" +
                        "1. eGFR (Estimated Glomerular Filtration Rate):\n" +
                        "   Derived from blood creatinine, age, and sex. A filtration rate over 90 mL/min/1.73 m² represents normal filtration capacity in healthy adults.\n\n" +
                        "2. Serum Creatinine:\n" +
                        "   Metabolic byproduct cleared by the kidneys. Elevated blood levels reflect decreased filtration.\n\n" +
                        "3. UACR (Urine Albumin-to-Creatinine Ratio):\n" +
                        "   Identifies early microalbumin leakage through glomeruli before blood creatinine rises.\n\n" +
                        "Clinical Limitations:\n" +
                        "• This app acts as a patient organizer and does not diagnose chronic kidney disease (CKD).\n" +
                        "• Always consult your nephrologist or treating physician for clinical advice."
                )
                .setPositiveButton("Close", null)
                .show();
    }
}
