package com.emergency.patient.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
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
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.MedicationEntity;
import com.emergency.patient.models.diabetes360.Diabetes360Data;
import com.emergency.patient.models.diabetes360.Diabetes360Repository;
import com.emergency.patient.models.diabetes360.GlucoseRecord;
import com.emergency.patient.models.diabetes360.Hba1cRecord;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.utils.LocalStorageManager;
import com.emergency.patient.views.HealthTrendChartView;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * GlucoseHba1cTrackingActivity — NetraProof Dedicated Glucose & HbA1c Tracking Module (Page 11).
 *
 * Implements strict clinical data tracking:
 *  - High-precision numerical logs for Glucose (with context) and HbA1c.
 *  - Canvas-based trend visualization without synthetic or interpolated data points.
 *  - Active medication schedule review loaded from local Room DB.
 *  - Chronological history with complete edit and delete capabilities.
 */
public class GlucoseHba1cTrackingActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextView tvSubtitleLastUpdated;
    private MaterialButton btnHeaderAddReading;

    // Glucose Section
    private View cardGlucoseOverview;
    private TextView badgeGlucoseContext, tvLatestGlucoseValue, tvLatestGlucoseUnit;
    private TextView tvLatestGlucoseTimestamp, tvLatestGlucoseNotes;
    private LinearLayout layoutGlucoseActiveContent, layoutGlucoseEmptyNotice;
    private MaterialButton btnAddFirstGlucose;

    // HbA1c Section
    private View cardHba1cOverview;
    private TextView btnQuickAddHba1c, tvLatestHba1cValue, tvLatestHba1cUnit;
    private TextView tvLatestHba1cDateAndSource, tvPreviousHba1cComparison;
    private LinearLayout layoutHba1cActiveContent, layoutHba1cEmptyNotice;
    private MaterialButton btnAddFirstHba1c;

    // Trends Section
    private TextView btnTabGlucoseTrend, btnTabHba1cTrend;
    private HealthTrendChartView chartGlucoseTrends;
    private boolean isGlucoseTrendSelected = true;

    // Medications Section
    private LinearLayout layoutMedicationsContainer;
    private TextView tvEmptyMedicationsNotice, btnOpenMedicationReminders;

    // History Section
    private TextView tvHistoryCountBadge, tvEmptyHistoryNotice;
    private LinearLayout layoutGlucoseHistoryList;

    private String patientUuid;
    private ActivityResultLauncher<String> hba1cReportPickerLauncher;
    private String pendingAttachmentPath = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_glucose_hba1c_tracking);

        patientUuid = TokenManager.getUUID(this);
        if (patientUuid == null || patientUuid.trim().isEmpty()) {
            patientUuid = "default_patient";
        }

        initViews();
        setupAttachmentPicker();
        setupClickListeners();
        loadAllData();
        com.emergency.patient.utils.NetraProfileHeaderHelper.setup(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllData();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvSubtitleLastUpdated = findViewById(R.id.tvSubtitleLastUpdated);
        btnHeaderAddReading = findViewById(R.id.btnHeaderAddReading);

        // Glucose
        cardGlucoseOverview = findViewById(R.id.cardGlucoseOverview);
        badgeGlucoseContext = findViewById(R.id.badgeGlucoseContext);
        tvLatestGlucoseValue = findViewById(R.id.tvLatestGlucoseValue);
        tvLatestGlucoseUnit = findViewById(R.id.tvLatestGlucoseUnit);
        tvLatestGlucoseTimestamp = findViewById(R.id.tvLatestGlucoseTimestamp);
        tvLatestGlucoseNotes = findViewById(R.id.tvLatestGlucoseNotes);
        layoutGlucoseActiveContent = findViewById(R.id.layoutGlucoseActiveContent);
        layoutGlucoseEmptyNotice = findViewById(R.id.layoutGlucoseEmptyNotice);
        btnAddFirstGlucose = findViewById(R.id.btnAddFirstGlucose);

        // HbA1c
        cardHba1cOverview = findViewById(R.id.cardHba1cOverview);
        btnQuickAddHba1c = findViewById(R.id.btnQuickAddHba1c);
        tvLatestHba1cValue = findViewById(R.id.tvLatestHba1cValue);
        tvLatestHba1cUnit = findViewById(R.id.tvLatestHba1cUnit);
        tvLatestHba1cDateAndSource = findViewById(R.id.tvLatestHba1cDateAndSource);
        tvPreviousHba1cComparison = findViewById(R.id.tvPreviousHba1cComparison);
        layoutHba1cActiveContent = findViewById(R.id.layoutHba1cActiveContent);
        layoutHba1cEmptyNotice = findViewById(R.id.layoutHba1cEmptyNotice);
        btnAddFirstHba1c = findViewById(R.id.btnAddFirstHba1c);

        // Trends
        btnTabGlucoseTrend = findViewById(R.id.btnTabGlucoseTrend);
        btnTabHba1cTrend = findViewById(R.id.btnTabHba1cTrend);
        chartGlucoseTrends = findViewById(R.id.chartGlucoseTrends);

        // Medications
        layoutMedicationsContainer = findViewById(R.id.layoutMedicationsContainer);
        tvEmptyMedicationsNotice = findViewById(R.id.tvEmptyMedicationsNotice);
        btnOpenMedicationReminders = findViewById(R.id.btnOpenMedicationReminders);

        // History
        tvHistoryCountBadge = findViewById(R.id.tvHistoryCountBadge);
        tvEmptyHistoryNotice = findViewById(R.id.tvEmptyHistoryNotice);
        layoutGlucoseHistoryList = findViewById(R.id.layoutGlucoseHistoryList);
    }

    private void setupAttachmentPicker() {
        hba1cReportPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        String saved = LocalStorageManager.saveFileToInternalStorage(this, uri, "hba1c_report.pdf");
                        if (saved != null) {
                            pendingAttachmentPath = saved;
                            Toast.makeText(this, "Lab report attached successfully.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Could not attach file.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnHeaderAddReading.setOnClickListener(v -> showAddOptionDialog());
        btnAddFirstGlucose.setOnClickListener(v -> showAddGlucoseDialog(null));
        btnAddFirstHba1c.setOnClickListener(v -> showAddHba1cDialog(null));
        btnQuickAddHba1c.setOnClickListener(v -> showAddHba1cDialog(null));

        btnTabGlucoseTrend.setOnClickListener(v -> {
            if (!isGlucoseTrendSelected) {
                isGlucoseTrendSelected = true;
                updateTrendTabSelection();
            }
        });

        btnTabHba1cTrend.setOnClickListener(v -> {
            if (isGlucoseTrendSelected) {
                isGlucoseTrendSelected = false;
                updateTrendTabSelection();
            }
        });

        btnOpenMedicationReminders.setOnClickListener(v -> {
            // Open Page 14 (Notifications & Reminders)
            try {
                Intent intent = new Intent(this, Class.forName("com.emergency.patient.activities.NotificationsRemindersActivity"));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Opening reminders...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddOptionDialog() {
        String[] options = {"Log Blood Glucose Reading", "Log HbA1c Lab Result"};
        new AlertDialog.Builder(this)
                .setTitle("Select Log Type")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showAddGlucoseDialog(null);
                    else showAddHba1cDialog(null);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateTrendTabSelection() {
        if (isGlucoseTrendSelected) {
            btnTabGlucoseTrend.setBackgroundResource(R.drawable.bg_mode_pill_selected);
            btnTabGlucoseTrend.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            btnTabHba1cTrend.setBackgroundResource(R.drawable.bg_mode_pill_unselected);
            btnTabHba1cTrend.setTextColor(ContextCompat.getColor(this, R.color.netra_text_secondary));
        } else {
            btnTabGlucoseTrend.setBackgroundResource(R.drawable.bg_mode_pill_unselected);
            btnTabGlucoseTrend.setTextColor(ContextCompat.getColor(this, R.color.netra_text_secondary));
            btnTabHba1cTrend.setBackgroundResource(R.drawable.bg_mode_pill_selected);
            btnTabHba1cTrend.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        }
        renderTrendsChart();
    }

    private void loadAllData() {
        Diabetes360Data data = Diabetes360Repository.getInstance().getData(this, patientUuid);

        // 1. Populate Glucose Overview
        List<GlucoseRecord> glucoseList = data.getGlucoseRecords();
        if (!glucoseList.isEmpty()) {
            GlucoseRecord latestGlu = glucoseList.get(0);
            layoutGlucoseActiveContent.setVisibility(View.VISIBLE);
            layoutGlucoseEmptyNotice.setVisibility(View.GONE);

            tvLatestGlucoseValue.setText(latestGlu.getFormattedValue());
            tvLatestGlucoseUnit.setText(latestGlu.getUnit());
            tvLatestGlucoseTimestamp.setText("Measured " + latestGlu.getFormattedTimestamp() + " (" + latestGlu.getSource() + ")");
            badgeGlucoseContext.setText(latestGlu.getReadingContext().getDisplayName());

            if (latestGlu.getNotes() != null && !latestGlu.getNotes().trim().isEmpty()) {
                tvLatestGlucoseNotes.setVisibility(View.VISIBLE);
                tvLatestGlucoseNotes.setText("Note: " + latestGlu.getNotes().trim());
            } else {
                tvLatestGlucoseNotes.setVisibility(View.GONE);
            }
            tvSubtitleLastUpdated.setText("Latest record: " + latestGlu.getFormattedDate());
        } else {
            layoutGlucoseActiveContent.setVisibility(View.GONE);
            layoutGlucoseEmptyNotice.setVisibility(View.VISIBLE);
            tvSubtitleLastUpdated.setText("No recorded glucose history yet");
        }

        // 2. Populate HbA1c Overview
        List<Hba1cRecord> hba1cList = data.getHba1cRecords();
        if (!hba1cList.isEmpty()) {
            Hba1cRecord latestA1c = hba1cList.get(0);
            layoutHba1cActiveContent.setVisibility(View.VISIBLE);
            layoutHba1cEmptyNotice.setVisibility(View.GONE);

            tvLatestHba1cValue.setText(latestA1c.getFormattedValue());
            tvLatestHba1cUnit.setText(latestA1c.getUnit());
            String lab = (latestA1c.getLabName() != null && !latestA1c.getLabName().isEmpty()) ? " • " + latestA1c.getLabName() : "";
            tvLatestHba1cDateAndSource.setText("Test Date: " + latestA1c.getFormattedDate() + " (" + latestA1c.getSource() + lab + ")");

            if (hba1cList.size() > 1) {
                Hba1cRecord prevA1c = hba1cList.get(1);
                double diff = latestA1c.getValue() - prevA1c.getValue();
                String diffStr = (diff >= 0 ? "+" : "") + String.format(Locale.getDefault(), "%.1f", diff);
                tvPreviousHba1cComparison.setVisibility(View.VISIBLE);
                tvPreviousHba1cComparison.setText("Previous: " + prevA1c.getFormattedValueWithUnit() + " on " + prevA1c.getFormattedDate() + " (" + diffStr + ")");
            } else {
                tvPreviousHba1cComparison.setVisibility(View.GONE);
            }
        } else {
            layoutHba1cActiveContent.setVisibility(View.GONE);
            layoutHba1cEmptyNotice.setVisibility(View.VISIBLE);
        }

        // 3. Render Trends Chart
        renderTrendsChart();

        // 4. Render History List
        renderHistoryList(glucoseList, hba1cList);

        // 5. Load Medications
        loadMedicationsAsync();
    }

    private void renderTrendsChart() {
        Diabetes360Data data = Diabetes360Repository.getInstance().getData(this, patientUuid);
        List<HealthTrendChartView.DataPoint> points = new ArrayList<>();

        if (isGlucoseTrendSelected) {
            List<GlucoseRecord> list = new ArrayList<>(data.getGlucoseRecords());
            // Sort chronological (oldest to newest) for line chart
            list.sort((a, b) -> Long.compare(a.getDateMillis(), b.getDateMillis()));
            String unit = "mg/dL";
            for (GlucoseRecord g : list) {
                points.add(new HealthTrendChartView.DataPoint(
                        g.getDateMillis(),
                        g.getValue(),
                        null,
                        g.getFormattedDate()
                ));
                unit = g.getUnit();
            }
            chartGlucoseTrends.setData(points, unit, false, "Blood Glucose", "");
        } else {
            List<Hba1cRecord> list = new ArrayList<>(data.getHba1cRecords());
            list.sort((a, b) -> Long.compare(a.getTestDateMillis(), b.getTestDateMillis()));
            String unit = "%";
            for (Hba1cRecord a : list) {
                points.add(new HealthTrendChartView.DataPoint(
                        a.getTestDateMillis(),
                        a.getValue(),
                        null,
                        a.getFormattedDate()
                ));
                unit = a.getUnit();
            }
            chartGlucoseTrends.setData(points, unit, false, "HbA1c", "");
        }
    }

    private void renderHistoryList(List<GlucoseRecord> glucoseList, List<Hba1cRecord> hba1cList) {
        layoutGlucoseHistoryList.removeAllViews();
        int total = glucoseList.size() + hba1cList.size();
        tvHistoryCountBadge.setText(total + (total == 1 ? " entry" : " entries"));

        if (total == 0) {
            tvEmptyHistoryNotice.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyHistoryNotice.setVisibility(View.GONE);

        // Render Glucose entries
        for (GlucoseRecord g : glucoseList) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutGlucoseHistoryList, false);
            ImageView iv = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvTime = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);

            iv.setImageResource(R.drawable.ic_metric_glucose);
            iv.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));

            tvTitle.setText(g.getFormattedValueWithUnit() + " — " + g.getReadingContext().getDisplayName());
            tvTime.setText(g.getFormattedTimestamp() + " • " + g.getSource());
            tvStatus.setText("Details");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

            row.setOnClickListener(v -> showGlucoseDetailDialog(g));
            layoutGlucoseHistoryList.addView(row);
        }

        // Render HbA1c entries
        for (Hba1cRecord a : hba1cList) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutGlucoseHistoryList, false);
            ImageView iv = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvTime = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);

            iv.setImageResource(R.drawable.ic_metric_hba1c);
            iv.setColorFilter(ContextCompat.getColor(this, ContextCompat.getColor(this, R.color.netra_orange)));

            String lab = (a.getLabName() != null && !a.getLabName().isEmpty()) ? " (" + a.getLabName() + ")" : "";
            tvTitle.setText("HbA1c: " + a.getFormattedValueWithUnit() + lab);
            tvTime.setText(a.getFormattedDate() + " • " + a.getSource());
            tvStatus.setText(a.getAttachmentPath() != null ? "📎 Report" : "Details");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

            row.setOnClickListener(v -> showHba1cDetailDialog(a));
            layoutGlucoseHistoryList.addView(row);
        }
    }

    private void loadMedicationsAsync() {
        layoutMedicationsContainer.removeAllViews();
        new Thread(() -> {
            List<MedicationEntity> meds = null;
            try {
                meds = AppDatabaseProvider.getInstance(this).medicationDao().getMedicationsForPatient(patientUuid);
            } catch (Exception ignored) {}

            final List<MedicationEntity> finalMeds = meds;
            runOnUiThread(() -> {
                if (finalMeds == null || finalMeds.isEmpty()) {
                    tvEmptyMedicationsNotice.setVisibility(View.VISIBLE);
                } else {
                    tvEmptyMedicationsNotice.setVisibility(View.GONE);
                    for (MedicationEntity m : finalMeds) {
                        View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutMedicationsContainer, false);
                        ImageView iv = row.findViewById(R.id.ivActivityRowIcon);
                        TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
                        TextView tvTime = row.findViewById(R.id.tvActivityRowTime);
                        TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);

                        iv.setImageResource(R.drawable.ic_shield_check_mint);
                        iv.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));

                        String medTitle = (m.brandName != null && !m.brandName.isEmpty()) ? m.brandName : m.name;
                        String dosage = m.dosage != null ? " (" + m.dosage + ")" : "";
                        tvTitle.setText(medTitle + dosage);

                        String expiryStr = m.expiry != null ? "Exp: " + m.expiry : "Prescription active";
                        tvTime.setText(expiryStr + " • Source: " + (m.ocrSource != null ? m.ocrSource : "Manual"));

                        tvStatus.setText("Prescribed");
                        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_success));

                        row.setOnClickListener(v -> {
                            new AlertDialog.Builder(this)
                                    .setTitle(medTitle + dosage)
                                    .setMessage("Details:\n• Drug Name: " + m.name +
                                            (m.dosage != null ? "\n• Dosage: " + m.dosage : "") +
                                            (m.expiry != null ? "\n• Expiry: " + m.expiry : "") +
                                            (m.rawOcrText != null ? "\n• Label Text: " + m.rawOcrText : ""))
                                    .setPositiveButton("Close", null)
                                    .show();
                        });

                        layoutMedicationsContainer.addView(row);
                    }
                }
            });
        }).start();
    }

    // ─── Dialogs: Add / Edit / Detail ──────────────────────────────────────────

    private void showAddGlucoseDialog(@Nullable GlucoseRecord toEdit) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_glucose_record, null);
        EditText etVal = v.findViewById(R.id.etGlucoseValue);
        Spinner spUnit = v.findViewById(R.id.spinnerGlucoseUnit);
        Spinner spContext = v.findViewById(R.id.spinnerGlucoseContext);
        TextView tvDate = v.findViewById(R.id.tvGlucoseDatePicked);
        TextView tvTime = v.findViewById(R.id.tvGlucoseTimePicked);
        Spinner spSource = v.findViewById(R.id.spinnerGlucoseSource);
        EditText etNotes = v.findViewById(R.id.etGlucoseNotes);

        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"mg/dL", "mmol/L"});
        spUnit.setAdapter(unitAdapter);

        ArrayAdapter<String> contextAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Fasting (Before Breakfast)", "Before Meal", "After Meal (Postprandial)", "Random", "Bedtime", "Other / Not Specified"});
        spContext.setAdapter(contextAdapter);

        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Patient Log", "Glucometer Sync", "Laboratory Report", "Doctor Consultation"});
        spSource.setAdapter(sourceAdapter);

        final Calendar cal = Calendar.getInstance();
        final long[] selectedDateMillis = {System.currentTimeMillis()};
        final String[] selectedTimeStr = {new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.getTime())};

        SimpleDateFormat sdfDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        tvDate.setText(sdfDate.format(cal.getTime()));
        tvTime.setText(selectedTimeStr[0]);

        if (toEdit != null) {
            etVal.setText(String.valueOf(toEdit.getValue()));
            if ("mmol/L".equalsIgnoreCase(toEdit.getUnit())) spUnit.setSelection(1);
            else spUnit.setSelection(0);

            for (int i = 0; i < contextAdapter.getCount(); i++) {
                if (contextAdapter.getItem(i).equalsIgnoreCase(toEdit.getReadingContext().getDisplayName())) {
                    spContext.setSelection(i);
                    break;
                }
            }

            cal.setTimeInMillis(toEdit.getDateMillis());
            selectedDateMillis[0] = toEdit.getDateMillis();
            tvDate.setText(sdfDate.format(cal.getTime()));

            if (toEdit.getMeasurementTime() != null) {
                selectedTimeStr[0] = toEdit.getMeasurementTime();
                tvTime.setText(selectedTimeStr[0]);
            }
            if (toEdit.getNotes() != null) etNotes.setText(toEdit.getNotes());
        }

        tvDate.setOnClickListener(view -> {
            new DatePickerDialog(this, (p, y, m, d) -> {
                cal.set(y, m, d);
                selectedDateMillis[0] = cal.getTimeInMillis();
                tvDate.setText(sdfDate.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        tvTime.setOnClickListener(view -> {
            new TimePickerDialog(this, (p, h, m) -> {
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, m);
                SimpleDateFormat tf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                selectedTimeStr[0] = tf.format(cal.getTime());
                tvTime.setText(selectedTimeStr[0]);
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
        });

        new AlertDialog.Builder(this)
                .setTitle(toEdit != null ? "Edit Blood Glucose Reading" : "Log Blood Glucose Reading")
                .setView(v)
                .setPositiveButton("Save Reading", (dialog, which) -> {
                    String valStr = etVal.getText().toString().trim();
                    if (valStr.isEmpty()) {
                        Toast.makeText(this, "Please enter a glucose number.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double val;
                    try {
                        val = Double.parseDouble(valStr);
                    } catch (Exception e) {
                        Toast.makeText(this, "Invalid glucose number.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String unit = (String) spUnit.getSelectedItem();
                    GlucoseRecord.ReadingContext ctx = GlucoseRecord.ReadingContext.fromString((String) spContext.getSelectedItem());
                    String source = (String) spSource.getSelectedItem();
                    String notes = etNotes.getText().toString().trim();

                    if (toEdit != null) {
                        toEdit.setValue(val);
                        toEdit.setUnit(unit);
                        toEdit.setReadingContext(ctx);
                        toEdit.setDateMillis(selectedDateMillis[0]);
                        toEdit.setMeasurementTime(selectedTimeStr[0]);
                        toEdit.setSource(source);
                        toEdit.setNotes(notes);
                        Diabetes360Repository.getInstance().updateGlucoseRecord(this, toEdit);
                        Toast.makeText(this, "Glucose reading updated.", Toast.LENGTH_SHORT).show();
                    } else {
                        GlucoseRecord fresh = new GlucoseRecord(patientUuid, val, unit, selectedDateMillis[0], selectedTimeStr[0], ctx, source, notes);
                        Diabetes360Repository.getInstance().addGlucoseRecord(this, fresh);
                        Toast.makeText(this, "Glucose reading logged: " + fresh.getFormattedValueWithUnit(), Toast.LENGTH_SHORT).show();
                    }
                    loadAllData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showGlucoseDetailDialog(GlucoseRecord record) {
        new AlertDialog.Builder(this)
                .setTitle(record.getFormattedValueWithUnit())
                .setMessage("Reading Details:\n" +
                        "• Context: " + record.getReadingContext().getDisplayName() + "\n" +
                        "• Timestamp: " + record.getFormattedTimestamp() + "\n" +
                        "• Source: " + record.getSource() +
                        (record.getNotes() != null && !record.getNotes().isEmpty() ? "\n• Notes: " + record.getNotes() : ""))
                .setPositiveButton("Edit", (d, w) -> showAddGlucoseDialog(record))
                .setNegativeButton("Delete", (d, w) -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Delete Glucose Record")
                            .setMessage("Are you sure you want to permanently delete this reading (" + record.getFormattedValueWithUnit() + ")?")
                            .setPositiveButton("Delete", (dd, ww) -> {
                                Diabetes360Repository.getInstance().deleteGlucoseRecord(this, record.getId());
                                Toast.makeText(this, "Record deleted.", Toast.LENGTH_SHORT).show();
                                loadAllData();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNeutralButton("Close", null)
                .show();
    }

    private void showAddHba1cDialog(@Nullable Hba1cRecord toEdit) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_hba1c_record, null);
        EditText etVal = v.findViewById(R.id.etHba1cValue);
        Spinner spUnit = v.findViewById(R.id.spinnerHba1cUnit);
        TextView tvDate = v.findViewById(R.id.tvHba1cDatePicked);
        EditText etLab = v.findViewById(R.id.etHba1cLabName);
        Spinner spSource = v.findViewById(R.id.spinnerHba1cSource);
        LinearLayout btnPickAttachment = v.findViewById(R.id.btnPickHba1cAttachment);
        TextView tvAttachmentStatus = v.findViewById(R.id.tvHba1cAttachmentStatus);
        EditText etNotes = v.findViewById(R.id.etHba1cNotes);

        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"%", "mmol/mol"});
        spUnit.setAdapter(unitAdapter);

        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Laboratory Report", "Self-Reported", "Doctor Consultation"});
        spSource.setAdapter(sourceAdapter);

        final Calendar cal = Calendar.getInstance();
        final long[] selectedDateMillis = {System.currentTimeMillis()};
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(cal.getTime()));

        pendingAttachmentPath = null;
        if (toEdit != null) {
            etVal.setText(String.valueOf(toEdit.getValue()));
            if ("mmol/mol".equalsIgnoreCase(toEdit.getUnit())) spUnit.setSelection(1);
            else spUnit.setSelection(0);

            cal.setTimeInMillis(toEdit.getTestDateMillis());
            selectedDateMillis[0] = toEdit.getTestDateMillis();
            tvDate.setText(sdf.format(cal.getTime()));

            if (toEdit.getLabName() != null) etLab.setText(toEdit.getLabName());
            if (toEdit.getNotes() != null) etNotes.setText(toEdit.getNotes());
            if (toEdit.getAttachmentPath() != null) {
                pendingAttachmentPath = toEdit.getAttachmentPath();
                tvAttachmentStatus.setText("Attached: " + toEdit.getAttachmentPath());
            }
        }

        tvDate.setOnClickListener(view -> {
            new DatePickerDialog(this, (p, y, m, d) -> {
                cal.set(y, m, d);
                selectedDateMillis[0] = cal.getTimeInMillis();
                tvDate.setText(sdf.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnPickAttachment.setOnClickListener(view -> {
            hba1cReportPickerLauncher.launch("application/pdf,image/*");
        });

        new AlertDialog.Builder(this)
                .setTitle(toEdit != null ? "Edit HbA1c Record" : "Log HbA1c Lab Result")
                .setView(v)
                .setPositiveButton("Save Record", (dialog, which) -> {
                    String valStr = etVal.getText().toString().trim();
                    if (valStr.isEmpty()) {
                        Toast.makeText(this, "Please enter an HbA1c value.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double val;
                    try {
                        val = Double.parseDouble(valStr);
                    } catch (Exception e) {
                        Toast.makeText(this, "Invalid HbA1c number.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String unit = (String) spUnit.getSelectedItem();
                    String lab = etLab.getText().toString().trim();
                    String source = (String) spSource.getSelectedItem();
                    String notes = etNotes.getText().toString().trim();

                    if (toEdit != null) {
                        toEdit.setValue(val);
                        toEdit.setUnit(unit);
                        toEdit.setTestDateMillis(selectedDateMillis[0]);
                        toEdit.setLabName(lab);
                        toEdit.setSource(source);
                        toEdit.setNotes(notes);
                        if (pendingAttachmentPath != null) toEdit.setAttachmentPath(pendingAttachmentPath);
                        Diabetes360Repository.getInstance().updateHba1cRecord(this, toEdit);
                        Toast.makeText(this, "HbA1c record updated.", Toast.LENGTH_SHORT).show();
                    } else {
                        Hba1cRecord fresh = new Hba1cRecord(patientUuid, val, unit, selectedDateMillis[0], source, lab, notes, pendingAttachmentPath);
                        Diabetes360Repository.getInstance().addHba1cRecord(this, fresh);
                        Toast.makeText(this, "HbA1c record logged: " + fresh.getFormattedValueWithUnit(), Toast.LENGTH_SHORT).show();
                    }
                    loadAllData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHba1cDetailDialog(Hba1cRecord record) {
        new AlertDialog.Builder(this)
                .setTitle("HbA1c: " + record.getFormattedValueWithUnit())
                .setMessage("Laboratory Record:\n" +
                        "• Test Date: " + record.getFormattedDate() + "\n" +
                        "• Source: " + record.getSource() +
                        (record.getLabName() != null && !record.getLabName().isEmpty() ? "\n• Laboratory: " + record.getLabName() : "") +
                        (record.getNotes() != null && !record.getNotes().isEmpty() ? "\n• Notes: " + record.getNotes() : "") +
                        (record.getAttachmentPath() != null ? "\n• Attached Document: Yes" : ""))
                .setPositiveButton("Edit", (d, w) -> showAddHba1cDialog(record))
                .setNegativeButton("Delete", (d, w) -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Delete HbA1c Record")
                            .setMessage("Are you sure you want to permanently delete this HbA1c record?")
                            .setPositiveButton("Delete", (dd, ww) -> {
                                Diabetes360Repository.getInstance().deleteHba1cRecord(this, record.getId());
                                Toast.makeText(this, "Record deleted.", Toast.LENGTH_SHORT).show();
                                loadAllData();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNeutralButton("Close", null)
                .show();
    }
}

