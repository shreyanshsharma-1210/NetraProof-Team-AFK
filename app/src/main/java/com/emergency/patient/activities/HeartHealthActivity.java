package com.emergency.patient.activities;

import android.app.DatePickerDialog;
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.emergency.patient.R;
import com.emergency.patient.models.diabetes360.BloodPressureRecord;
import com.emergency.patient.models.diabetes360.Diabetes360Data;
import com.emergency.patient.models.diabetes360.Diabetes360Repository;
import com.emergency.patient.models.diabetes360.DiabetesContext;
import com.emergency.patient.models.diabetes360.HealthReminder;
import com.emergency.patient.models.diabetes360.LipidProfileRecord;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.views.HealthTrendChartView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HeartHealthActivity — NetraProof Diabetes360 Heart & Vascular Health (Page 09)
 *
 * Organizes user-provided cardiovascular measurements, blood pressure logs, and lipid panels.
 * Features:
 *  - Systolic & Diastolic BP recording & dual-series visualization
 *  - Lipid Profile tracking (Total Cholesterol, LDL, HDL, Triglycerides)
 *  - Cardiovascular history context
 *  - Trend charts (HealthTrendChartView)
 *  - Reminders & educational guidance (zero fake event predictions)
 */
public class HeartHealthActivity extends AppCompatActivity {

    private ImageView btnBack, btnHelp;
    private View btnAddRecordHeader, btnAddLipidReport, btnEditCardioHistory, btnAddHeartReminder;
    private TextView tvLatestRecordDate, tvHeartHistoryCount, tvEmptyHeartHistory, tvEmptyHeartReminders;
    private TextView tvCardioHistoryText;

    // BP Card
    private TextView tvBpValue, tvBpUnit, tvBpDateAndPrev, badgeBpSource;

    // Lipid Card
    private TextView tvLipidSummary, tvLipidDateAndLab, badgeLipidSource;

    // Trend Chart & Tabs
    private HealthTrendChartView chartHeartTrends;
    private TextView btnTabBp, btnTabLipids;
    private View layoutBpLegend;
    private boolean isBpTrendActive = true;

    // Containers
    private LinearLayout layoutHeartHistoryContainer;
    private LinearLayout layoutHeartRemindersContainer;

    private String patientUuid;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_health);

        patientUuid = TokenManager.getUUID(this);

        initViews();
        setupClickListeners();
        loadHeartData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHeartData();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHelp = findViewById(R.id.btnHelp);
        btnAddRecordHeader = findViewById(R.id.btnAddRecordHeader);
        btnAddLipidReport = findViewById(R.id.btnAddLipidReport);
        btnEditCardioHistory = findViewById(R.id.btnEditCardioHistory);
        btnAddHeartReminder = findViewById(R.id.btnAddHeartReminder);

        tvLatestRecordDate = findViewById(R.id.tvLatestRecordDate);
        tvHeartHistoryCount = findViewById(R.id.tvHeartHistoryCount);
        tvEmptyHeartHistory = findViewById(R.id.tvEmptyHeartHistory);
        tvEmptyHeartReminders = findViewById(R.id.tvEmptyHeartReminders);
        tvCardioHistoryText = findViewById(R.id.tvCardioHistoryText);

        // BP Card
        tvBpValue = findViewById(R.id.tvBpValue);
        tvBpUnit = findViewById(R.id.tvBpUnit);
        tvBpDateAndPrev = findViewById(R.id.tvBpDateAndPrev);
        badgeBpSource = findViewById(R.id.badgeBpSource);

        // Lipid Card
        tvLipidSummary = findViewById(R.id.tvLipidSummary);
        tvLipidDateAndLab = findViewById(R.id.tvLipidDateAndLab);
        badgeLipidSource = findViewById(R.id.badgeLipidSource);

        // Chart & Tabs
        chartHeartTrends = findViewById(R.id.chartHeartTrends);
        btnTabBp = findViewById(R.id.btnTabBp);
        btnTabLipids = findViewById(R.id.btnTabLipids);
        layoutBpLegend = findViewById(R.id.layoutBpLegend);

        layoutHeartHistoryContainer = findViewById(R.id.layoutHeartHistoryContainer);
        layoutHeartRemindersContainer = findViewById(R.id.layoutHeartRemindersContainer);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnHelp.setOnClickListener(v -> showEducationalDialog());

        btnAddRecordHeader.setOnClickListener(v -> showAddChoiceDialog());
        btnAddLipidReport.setOnClickListener(v -> showAddLipidDialog());

        btnEditCardioHistory.setOnClickListener(v -> showEditHistoryDialog());
        btnAddHeartReminder.setOnClickListener(v -> showAddReminderDialog());

        btnTabBp.setOnClickListener(v -> selectTrendTab(true));
        btnTabLipids.setOnClickListener(v -> selectTrendTab(false));

        findViewById(R.id.cardBp).setOnClickListener(v -> showAddBpDialog());
        findViewById(R.id.cardLipids).setOnClickListener(v -> showAddLipidDialog());
    }

    private void showAddChoiceDialog() {
        String[] options = {"Log Blood Pressure Reading", "Add Lipid Profile Lab Test"};
        new AlertDialog.Builder(this)
                .setTitle("Add Cardiovascular Record")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showAddBpDialog();
                    else showAddLipidDialog();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void selectTrendTab(boolean showBp) {
        isBpTrendActive = showBp;

        btnTabBp.setBackgroundResource(showBp ? R.drawable.bg_mode_pill_selected : R.drawable.bg_mode_pill_unselected);
        btnTabBp.setTextColor(ContextCompat.getColor(this, showBp ? android.R.color.white : R.color.netra_text_secondary));

        btnTabLipids.setBackgroundResource(!showBp ? R.drawable.bg_mode_pill_selected : R.drawable.bg_mode_pill_unselected);
        btnTabLipids.setTextColor(ContextCompat.getColor(this, !showBp ? android.R.color.white : R.color.netra_text_secondary));

        layoutBpLegend.setVisibility(showBp ? View.VISIBLE : View.GONE);
        updateTrendChart();
    }

    private void loadHeartData() {
        Diabetes360Data data = Diabetes360Repository.getInstance().getData(this, patientUuid);

        // 1. Latest Activity Date
        long latestMillis = 0;
        String latestDesc = "";
        if (!data.getBloodPressureRecords().isEmpty()) {
            latestMillis = data.getBloodPressureRecords().get(0).getMeasurementDateMillis();
            latestDesc = "BP: " + data.getBloodPressureRecords().get(0).getFormattedReading();
        }
        if (!data.getLipidProfileRecords().isEmpty() && data.getLipidProfileRecords().get(0).getTestDateMillis() > latestMillis) {
            latestMillis = data.getLipidProfileRecords().get(0).getTestDateMillis();
            latestDesc = "Lipid panel";
        }
        if (latestMillis > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            tvLatestRecordDate.setText("Latest record: " + sdf.format(new Date(latestMillis)) + " (" + latestDesc + ")");
        } else {
            tvLatestRecordDate.setText("No cardiovascular records found");
        }

        // 2. BP Card
        if (!data.getBloodPressureRecords().isEmpty()) {
            BloodPressureRecord bp = data.getBloodPressureRecords().get(0);
            tvBpValue.setText(bp.getSystolic() + " / " + bp.getDiastolic());
            tvBpUnit.setVisibility(View.VISIBLE);

            String prevStr = data.getBloodPressureRecords().size() > 1 ?
                    " • Prev: " + data.getBloodPressureRecords().get(1).getFormattedReading() : "";
            tvBpDateAndPrev.setText("Recorded on " + bp.getFormattedDate() + " (" + bp.getSource() + ")" + prevStr);

            badgeBpSource.setText(bp.getSource());
            badgeBpSource.setVisibility(View.VISIBLE);
        } else {
            tvBpValue.setText("No blood pressure reading");
            tvBpUnit.setVisibility(View.GONE);
            tvBpDateAndPrev.setText("Tap to log your latest systolic and diastolic readings");
            badgeBpSource.setVisibility(View.GONE);
        }

        // 3. Lipid Card
        if (!data.getLipidProfileRecords().isEmpty()) {
            LipidProfileRecord lip = data.getLipidProfileRecords().get(0);
            tvLipidSummary.setText(lip.getSummaryText());
            String labStr = (lip.getLabName() != null && !lip.getLabName().isEmpty()) ? " • " + lip.getLabName() : "";
            tvLipidDateAndLab.setText("Tested on " + lip.getFormattedDate() + " (" + lip.getSource() + ")" + labStr);

            badgeLipidSource.setText(lip.getSource());
            badgeLipidSource.setVisibility(View.VISIBLE);
        } else {
            tvLipidSummary.setText("No lipid test record available");
            tvLipidDateAndLab.setText("Tap to log Total Cholesterol, LDL, HDL, and Triglycerides");
            badgeLipidSource.setVisibility(View.GONE);
        }

        // 4. Cardio History Context
        DiabetesContext ctx = data.getDiabetesContext();
        if (ctx != null && ctx.getRelevantHistoryNotes() != null && !ctx.getRelevantHistoryNotes().trim().isEmpty()) {
            tvCardioHistoryText.setText(ctx.getRelevantHistoryNotes());
        } else {
            tvCardioHistoryText.setText("No user-provided cardiovascular history noted. Tap 'Edit' to record self-reported conditions or clinician history.");
        }

        // 5. Update Trends
        updateTrendChart();

        // 6. Populate Combined Chronological History
        populateHistoryList(data.getBloodPressureRecords(), data.getLipidProfileRecords());

        // 7. Reminders
        populateReminders(data.getReminders());
    }

    private void updateTrendChart() {
        Diabetes360Data data = Diabetes360Repository.getInstance().getData(this, patientUuid);
        List<HealthTrendChartView.DataPoint> points = new ArrayList<>();

        if (isBpTrendActive) {
            for (BloodPressureRecord bp : data.getBloodPressureRecords()) {
                points.add(new HealthTrendChartView.DataPoint(
                        bp.getMeasurementDateMillis(),
                        bp.getSystolic(),
                        (double) bp.getDiastolic(),
                        bp.getFormattedDate()
                ));
            }
            chartHeartTrends.setData(points, "mmHg", true, "Systolic", "Diastolic");
        } else {
            String unit = "mg/dL";
            for (LipidProfileRecord lip : data.getLipidProfileRecords()) {
                if (lip.getTotalCholesterol() != null) {
                    points.add(new HealthTrendChartView.DataPoint(
                            lip.getTestDateMillis(),
                            lip.getTotalCholesterol(),
                            null,
                            lip.getFormattedDate()
                    ));
                    unit = lip.getUnit();
                }
            }
            chartHeartTrends.setData(points, unit, false, "Total Cholesterol", "");
        }
    }

    private void populateHistoryList(List<BloodPressureRecord> bpList, List<LipidProfileRecord> lipList) {
        layoutHeartHistoryContainer.removeAllViews();

        int totalCount = bpList.size() + lipList.size();
        if (totalCount == 0) {
            tvEmptyHeartHistory.setVisibility(View.VISIBLE);
            tvHeartHistoryCount.setText("0 records");
            return;
        }

        tvEmptyHeartHistory.setVisibility(View.GONE);
        tvHeartHistoryCount.setText(totalCount + (totalCount == 1 ? " record" : " records"));

        // Render BP records
        for (BloodPressureRecord bp : bpList) {
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutHeartHistoryContainer, false);
            ImageView ivIcon = itemView.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = itemView.findViewById(R.id.tvActivityRowTitle);
            TextView tvDate = itemView.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = itemView.findViewById(R.id.tvActivityRowStatus);

            ivIcon.setImageResource(R.drawable.ic_blood_pressure);
            ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));

            tvTitle.setText("Blood Pressure: " + bp.getFormattedReading());
            tvDate.setText(bp.getFormattedDate() + " • " + bp.getSource());
            tvStatus.setText("Details");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

            itemView.setOnClickListener(v -> showBpDetailDialog(bp));
            layoutHeartHistoryContainer.addView(itemView);
        }

        // Render Lipid records
        for (LipidProfileRecord lip : lipList) {
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutHeartHistoryContainer, false);
            ImageView ivIcon = itemView.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = itemView.findViewById(R.id.tvActivityRowTitle);
            TextView tvDate = itemView.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = itemView.findViewById(R.id.tvActivityRowStatus);

            ivIcon.setImageResource(R.drawable.ic_lipid);
            ivIcon.setColorFilter(ContextCompat.getColor(this, ContextCompat.getColor(this, R.color.netra_orange)));

            tvTitle.setText("Lipid Panel: " + lip.getSummaryText());
            String lab = (lip.getLabName() != null && !lip.getLabName().isEmpty()) ? " • " + lip.getLabName() : "";
            tvDate.setText(lip.getFormattedDate() + " • " + lip.getSource() + lab);
            tvStatus.setText("Details");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

            itemView.setOnClickListener(v -> showLipidDetailDialog(lip));
            layoutHeartHistoryContainer.addView(itemView);
        }
    }

    private void populateReminders(List<HealthReminder> allReminders) {
        layoutHeartRemindersContainer.removeAllViews();
        List<HealthReminder> heartReminders = new ArrayList<>();
        for (HealthReminder r : allReminders) {
            if (r.getCategory() == HealthReminder.Category.HEART) {
                heartReminders.add(r);
            }
        }

        if (heartReminders.isEmpty()) {
            tvEmptyHeartReminders.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyHeartReminders.setVisibility(View.GONE);

        for (HealthReminder rem : heartReminders) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutHeartRemindersContainer, false);
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
                        .setMessage("Due: " + rem.getFormattedDueDate() + "\nFrequency: " + rem.getFrequency())
                        .setPositiveButton(rem.isCompleted() ? "Mark Pending" : "Mark Done", (d, w) -> {
                            Diabetes360Repository.getInstance().toggleReminderCompletion(this, rem.getId());
                            loadHeartData();
                        })
                        .setNegativeButton("Delete", (d, w) -> {
                            Diabetes360Repository.getInstance().deleteReminder(this, rem.getId());
                            loadHeartData();
                        })
                        .setNeutralButton("Close", null)
                        .show();
            });

            layoutHeartRemindersContainer.addView(row);
        }
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    private void showAddBpDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_blood_pressure, null);
        EditText etSys = dialogView.findViewById(R.id.etSystolic);
        EditText etDia = dialogView.findViewById(R.id.etDiastolic);
        TextView tvDate = dialogView.findViewById(R.id.tvBpDatePicked);
        Spinner spinnerSource = dialogView.findViewById(R.id.spinnerBpSource);
        EditText etNotes = dialogView.findViewById(R.id.etBpNotes);

        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Manual Entry", "Home Digital Monitor", "Clinician Measured"}
        );
        spinnerSource.setAdapter(sourceAdapter);

        final Calendar cal = Calendar.getInstance();
        final long[] selectedMillis = {cal.getTimeInMillis()};
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        tvDate.setText(sdf.format(cal.getTime()));

        tvDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                cal.set(year, month, dayOfMonth);
                selectedMillis[0] = cal.getTimeInMillis();
                tvDate.setText(sdf.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        new AlertDialog.Builder(this)
                .setTitle("Log Blood Pressure")
                .setView(dialogView)
                .setPositiveButton("Save Reading", (dialog, which) -> {
                    String sysStr = etSys.getText().toString().trim();
                    String diaStr = etDia.getText().toString().trim();

                    if (sysStr.isEmpty() || diaStr.isEmpty()) {
                        Toast.makeText(this, "Please enter both systolic and diastolic numbers.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int sys, dia;
                    try {
                        sys = Integer.parseInt(sysStr);
                        dia = Integer.parseInt(diaStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid number format.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (sys < 50 || sys > 300 || dia < 30 || dia > 200) {
                        Toast.makeText(this, "Please verify readings (physiologically typical range: 50-300 / 30-200).", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String src = (String) spinnerSource.getSelectedItem();
                    String notes = etNotes.getText().toString().trim();

                    BloodPressureRecord record = new BloodPressureRecord(
                            null, patientUuid, sys, dia, selectedMillis[0], src, notes
                    );

                    Diabetes360Repository.getInstance().addBloodPressureRecord(this, record);
                    Toast.makeText(this, "Blood pressure reading saved.", Toast.LENGTH_SHORT).show();
                    loadHeartData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddLipidDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_lipid_profile, null);
        EditText etTotal = dialogView.findViewById(R.id.etTotalCholesterol);
        EditText etLdl = dialogView.findViewById(R.id.etLdl);
        EditText etHdl = dialogView.findViewById(R.id.etHdl);
        EditText etTg = dialogView.findViewById(R.id.etTriglycerides);
        EditText etUnit = dialogView.findViewById(R.id.etLipidUnit);
        TextView tvDate = dialogView.findViewById(R.id.tvLipidDatePicked);
        EditText etLab = dialogView.findViewById(R.id.etLipidLabName);
        EditText etNotes = dialogView.findViewById(R.id.etLipidNotes);

        final Calendar cal = Calendar.getInstance();
        final long[] selectedMillis = {cal.getTimeInMillis()};
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(cal.getTime()));

        tvDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                cal.set(year, month, dayOfMonth);
                selectedMillis[0] = cal.getTimeInMillis();
                tvDate.setText(sdf.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        new AlertDialog.Builder(this)
                .setTitle("Add Lipid Profile")
                .setView(dialogView)
                .setPositiveButton("Save Panel", (dialog, which) -> {
                    Double total = parseNullableDouble(etTotal.getText().toString().trim());
                    Double ldl = parseNullableDouble(etLdl.getText().toString().trim());
                    Double hdl = parseNullableDouble(etHdl.getText().toString().trim());
                    Double tg = parseNullableDouble(etTg.getText().toString().trim());

                    if (total == null && ldl == null && hdl == null && tg == null) {
                        Toast.makeText(this, "Please enter at least one lipid measurement.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String unit = etUnit.getText().toString().trim();
                    if (unit.isEmpty()) unit = "mg/dL";

                    String lab = etLab.getText().toString().trim();
                    String notes = etNotes.getText().toString().trim();

                    LipidProfileRecord record = new LipidProfileRecord(
                            null, patientUuid, total, ldl, hdl, tg, unit, selectedMillis[0], "Manual Entry", lab, notes, null
                    );

                    Diabetes360Repository.getInstance().addLipidProfileRecord(this, record);
                    Toast.makeText(this, "Lipid panel saved.", Toast.LENGTH_SHORT).show();
                    loadHeartData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Double parseNullableDouble(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            return Double.parseDouble(str);
        } catch (Exception e) {
            return null;
        }
    }

    private void showBpDetailDialog(BloodPressureRecord bp) {
        new AlertDialog.Builder(this)
                .setTitle("Blood Pressure Reading")
                .setMessage(
                        "Reading: " + bp.getFormattedReading() + "\n" +
                        "Date: " + bp.getFormattedDate() + "\n" +
                        "Source: " + bp.getSource() + "\n" +
                        (bp.getNotes() != null && !bp.getNotes().isEmpty() ? "Notes: " + bp.getNotes() + "\n" : "") +
                        "\nRecord ID: " + bp.getId()
                )
                .setPositiveButton("Close", null)
                .setNegativeButton("Delete Record", (d, w) -> {
                    Diabetes360Repository.getInstance().deleteBloodPressureRecord(this, bp.getId());
                    Toast.makeText(this, "Reading deleted.", Toast.LENGTH_SHORT).show();
                    loadHeartData();
                })
                .show();
    }

    private void showLipidDetailDialog(LipidProfileRecord lip) {
        new AlertDialog.Builder(this)
                .setTitle("Lipid Profile Details")
                .setMessage(
                        "Summary: " + lip.getSummaryText() + "\n" +
                        "Test Date: " + lip.getFormattedDate() + "\n" +
                        "Source: " + lip.getSource() + "\n" +
                        (lip.getLabName() != null && !lip.getLabName().isEmpty() ? "Laboratory: " + lip.getLabName() + "\n" : "") +
                        (lip.getNotes() != null && !lip.getNotes().isEmpty() ? "Notes: " + lip.getNotes() + "\n" : "") +
                        "\nRecord ID: " + lip.getId()
                )
                .setPositiveButton("Close", null)
                .setNegativeButton("Delete Record", (d, w) -> {
                    Diabetes360Repository.getInstance().deleteLipidProfileRecord(this, lip.getId());
                    Toast.makeText(this, "Record deleted.", Toast.LENGTH_SHORT).show();
                    loadHeartData();
                })
                .show();
    }

    private void showEditHistoryDialog() {
        final EditText etInput = new EditText(this);
        etInput.setHint("e.g. Diagnosed with mild hypertension in 2022; no history of coronary artery disease.");
        Diabetes360Data data = Diabetes360Repository.getInstance().getData(this, patientUuid);
        if (data.getDiabetesContext() != null && data.getDiabetesContext().getRelevantHistoryNotes() != null) {
            etInput.setText(data.getDiabetesContext().getRelevantHistoryNotes());
        }

        new AlertDialog.Builder(this)
                .setTitle("Cardiovascular History Notes")
                .setMessage("Record self-reported history or clinician-documented cardiovascular information:")
                .setView(etInput)
                .setPositiveButton("Save Notes", (d, w) -> {
                    DiabetesContext ctx = data.getDiabetesContext();
                    ctx.setRelevantHistoryNotes(etInput.getText().toString().trim());
                    Diabetes360Repository.getInstance().updateDiabetesContext(this, ctx);
                    Toast.makeText(this, "History notes updated.", Toast.LENGTH_SHORT).show();
                    loadHeartData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddReminderDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_health_reminder, null);
        EditText etTitle = v.findViewById(R.id.etReminderTitle);
        TextView tvDate = v.findViewById(R.id.tvReminderDate);
        Spinner spinnerFreq = v.findViewById(R.id.spinnerFrequency);
        EditText etNotes = v.findViewById(R.id.etReminderNotes);

        etTitle.setText("Blood Pressure Check / Lipid Follow-up");

        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"One-Time", "Weekly", "Monthly", "Every 3 Months"}
        );
        spinnerFreq.setAdapter(freqAdapter);

        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.WEEK_OF_YEAR, 2);
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
                .setTitle("Schedule Heart Health Reminder")
                .setView(v)
                .setPositiveButton("Save Reminder", (d, w) -> {
                    String title = etTitle.getText().toString().trim();
                    if (title.isEmpty()) title = "Blood Pressure Check";
                    String freq = (String) spinnerFreq.getSelectedItem();
                    String notes = etNotes.getText().toString().trim();

                    HealthReminder rem = new HealthReminder(
                            null, patientUuid, HealthReminder.Category.HEART, title, selectedMillis[0], freq, notes
                    );
                    Diabetes360Repository.getInstance().addReminder(this, rem);
                    Toast.makeText(this, "Reminder scheduled.", Toast.LENGTH_SHORT).show();
                    loadHeartData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEducationalDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Understanding Heart Health Measurements")
                .setMessage(
                        "Cardiovascular health in diabetes monitoring focuses on vascular pressure and lipid metabolism:\n\n" +
                        "1. Blood Pressure:\n" +
                        "   • Systolic (Upper): Pressure exerted against artery walls when the heart beats.\n" +
                        "   • Diastolic (Lower): Pressure within arteries between heartbeats.\n" +
                        "   • Regular logging helps physicians evaluate whether blood pressure targets are maintained.\n\n" +
                        "2. Lipid Profile:\n" +
                        "   • Total Cholesterol, LDL ('low-density'), HDL ('high-density'), and Triglycerides.\n" +
                        "   • Evaluated by clinicians to monitor arterial and heart vascular health.\n\n" +
                        "Clinical Limitations:\n" +
                        "• This app does not calculate automated heart-attack or stroke risk scores.\n" +
                        "• Never change medications without direct consultation with your cardiologist or doctor."
                )
                .setPositiveButton("Close", null)
                .show();
    }
}
