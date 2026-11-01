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
import com.emergency.patient.models.diabetes360.Diabetes360Data;
import com.emergency.patient.models.diabetes360.Diabetes360Repository;
import com.emergency.patient.models.diabetes360.FootCheckRecord;
import com.emergency.patient.models.diabetes360.FootConcernRecord;
import com.emergency.patient.models.diabetes360.HealthReminder;
import com.emergency.patient.models.diabetes360.SymptomRecord;
import com.emergency.patient.security.TokenManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * FootNerveCareActivity — NetraProof Diabetes360 Foot & Nerve Care (Page 10)
 *
 * Dedicated module for tracking routine foot self-inspections, user-reported neurological
 * symptoms (numbness, tingling, pain, burning), and skin/wound concerns.
 *
 * Adheres strictly to healthcare software limitations:
 *  - Does not diagnose diabetic peripheral neuropathy.
 *  - Prominently highlights red-flag warning signs requiring urgent clinician assessment.
 */
public class FootNerveCareActivity extends AppCompatActivity {

    private ImageView btnBack, btnHelp;
    private View btnAddCheckHeader, btnTrackSymptomQuick, btnLogNewSymptom, btnRecordConcern, btnAddFootReminder;
    private TextView tvLatestCheckDate, tvActiveSymptomsCount, tvFootHistoryCount;

    // Recent Check Card
    private TextView tvRecentCheckSummary, tvRecentCheckDetails, badgeCheckStatus;
    private View layoutPhotoAttachedIndicator;

    // Containers
    private LinearLayout layoutSymptomsListContainer;
    private TextView tvEmptySymptoms;

    private LinearLayout layoutFootConcernsContainer;
    private TextView tvEmptyConcerns;

    private LinearLayout layoutFootRemindersContainer;
    private TextView tvEmptyFootReminders;

    private LinearLayout layoutFootHistoryTimeline;
    private TextView tvEmptyFootTimeline;

    private String patientUuid;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_foot_nerve_care);

        patientUuid = TokenManager.getUUID(this);

        initViews();
        setupClickListeners();
        loadFootData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFootData();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHelp = findViewById(R.id.btnHelp);
        btnAddCheckHeader = findViewById(R.id.btnAddCheckHeader);
        btnTrackSymptomQuick = findViewById(R.id.btnTrackSymptomQuick);
        btnLogNewSymptom = findViewById(R.id.btnLogNewSymptom);
        btnRecordConcern = findViewById(R.id.btnRecordConcern);
        btnAddFootReminder = findViewById(R.id.btnAddFootReminder);

        tvLatestCheckDate = findViewById(R.id.tvLatestCheckDate);
        tvActiveSymptomsCount = findViewById(R.id.tvActiveSymptomsCount);
        tvFootHistoryCount = findViewById(R.id.tvFootHistoryCount);

        // Check Card
        tvRecentCheckSummary = findViewById(R.id.tvRecentCheckSummary);
        tvRecentCheckDetails = findViewById(R.id.tvRecentCheckDetails);
        badgeCheckStatus = findViewById(R.id.badgeCheckStatus);
        layoutPhotoAttachedIndicator = findViewById(R.id.layoutPhotoAttachedIndicator);

        // Containers
        layoutSymptomsListContainer = findViewById(R.id.layoutSymptomsListContainer);
        tvEmptySymptoms = findViewById(R.id.tvEmptySymptoms);

        layoutFootConcernsContainer = findViewById(R.id.layoutFootConcernsContainer);
        tvEmptyConcerns = findViewById(R.id.tvEmptyConcerns);

        layoutFootRemindersContainer = findViewById(R.id.layoutFootRemindersContainer);
        tvEmptyFootReminders = findViewById(R.id.tvEmptyFootReminders);

        layoutFootHistoryTimeline = findViewById(R.id.layoutFootHistoryTimeline);
        tvEmptyFootTimeline = findViewById(R.id.tvEmptyFootTimeline);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnHelp.setOnClickListener(v -> showEducationalDialog());

        btnAddCheckHeader.setOnClickListener(v -> showAddCheckDialog());
        btnTrackSymptomQuick.setOnClickListener(v -> showAddSymptomDialog());
        btnLogNewSymptom.setOnClickListener(v -> showAddSymptomDialog());
        btnRecordConcern.setOnClickListener(v -> showAddConcernDialog());
        btnAddFootReminder.setOnClickListener(v -> showAddReminderDialog());

        findViewById(R.id.cardRecentCheck).setOnClickListener(v -> showAddCheckDialog());
    }

    private void loadFootData() {
        Diabetes360Data data = Diabetes360Repository.getInstance().getData(this, patientUuid);

        // 1. Populate Recent Foot Check
        if (!data.getFootCheckRecords().isEmpty()) {
            FootCheckRecord check = data.getFootCheckRecords().get(0);
            tvRecentCheckSummary.setText(check.getSide() + " Inspection: " + check.getSkinCondition());
            String notes = (check.getNotes() != null && !check.getNotes().isEmpty()) ? " • " + check.getNotes() : "";
            tvRecentCheckDetails.setText("Recorded on " + check.getFormattedDate() + " • Concern: " + check.getWoundOrConcern() + notes);

            badgeCheckStatus.setText(check.getSource());
            badgeCheckStatus.setVisibility(View.VISIBLE);

            if (check.getPhotoPath() != null) {
                layoutPhotoAttachedIndicator.setVisibility(View.VISIBLE);
            } else {
                layoutPhotoAttachedIndicator.setVisibility(View.GONE);
            }

            tvLatestCheckDate.setText("Latest check: " + check.getFormattedDate());
        } else {
            tvRecentCheckSummary.setText("No foot-check record available");
            tvRecentCheckDetails.setText("Tap '+ Log Check' to record observations for left, right, or both feet.");
            badgeCheckStatus.setVisibility(View.GONE);
            layoutPhotoAttachedIndicator.setVisibility(View.GONE);
            tvLatestCheckDate.setText("No foot inspection records found");
        }

        // 2. Populate Symptoms
        populateSymptoms(data.getSymptomRecords());

        // 3. Populate Foot Concerns
        populateConcerns(data.getFootConcernRecords());

        // 4. Populate Reminders
        populateReminders(data.getReminders());

        // 5. Populate Timeline
        populateTimeline(data.getFootCheckRecords(), data.getSymptomRecords(), data.getFootConcernRecords());
    }

    private void populateSymptoms(List<SymptomRecord> symptoms) {
        layoutSymptomsListContainer.removeAllViews();

        int activeCount = 0;
        for (SymptomRecord s : symptoms) {
            if ("Active".equalsIgnoreCase(s.getStatus()) || "Improving".equalsIgnoreCase(s.getStatus())) {
                activeCount++;
            }
        }
        tvActiveSymptomsCount.setText(activeCount + " active");

        if (symptoms.isEmpty()) {
            tvEmptySymptoms.setVisibility(View.VISIBLE);
            layoutSymptomsListContainer.addView(tvEmptySymptoms);
            return;
        }

        tvEmptySymptoms.setVisibility(View.GONE);

        for (SymptomRecord s : symptoms) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutSymptomsListContainer, false);
            ImageView ivIcon = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvDate = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);

            ivIcon.setImageResource(R.drawable.ic_nerve);
            ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));

            tvTitle.setText(s.getSymptomType().getDisplayName() + " (" + s.getSide() + ")");
            String desc = (s.getUserDescription() != null && !s.getUserDescription().isEmpty()) ? " • " + s.getUserDescription() : "";
            tvDate.setText(s.getFormattedDate() + " • Area: " + s.getBodyLocation() + desc);

            tvStatus.setText(s.getStatus());
            tvStatus.setTextColor(ContextCompat.getColor(this, "Active".equalsIgnoreCase(s.getStatus()) ? R.color.netra_warning : R.color.netra_success));

            row.setOnClickListener(v -> showSymptomDetailDialog(s));
            layoutSymptomsListContainer.addView(row);
        }
    }

    private void populateConcerns(List<FootConcernRecord> concerns) {
        layoutFootConcernsContainer.removeAllViews();

        if (concerns.isEmpty()) {
            tvEmptyConcerns.setVisibility(View.VISIBLE);
            layoutFootConcernsContainer.addView(tvEmptyConcerns);
            return;
        }

        tvEmptyConcerns.setVisibility(View.GONE);

        for (FootConcernRecord c : concerns) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutFootConcernsContainer, false);
            ImageView ivIcon = card.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = card.findViewById(R.id.tvActivityRowTitle);
            TextView tvDate = card.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = card.findViewById(R.id.tvActivityRowStatus);

            ivIcon.setImageResource(R.drawable.ic_metric_foot);
            ivIcon.setColorFilter(ContextCompat.getColor(this, ContextCompat.getColor(this, R.color.netra_warning)));

            tvTitle.setText(c.getConcernType().getDisplayName() + " (" + c.getSide() + ")");
            tvDate.setText("Noticed: " + c.getFormattedDate() + " • " + c.getDescription());

            tvStatus.setText(c.getStatus());
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

            card.setOnClickListener(v -> showConcernDetailDialog(c));
            layoutFootConcernsContainer.addView(card);
        }
    }

    private void populateReminders(List<HealthReminder> allReminders) {
        layoutFootRemindersContainer.removeAllViews();
        List<HealthReminder> footReminders = new ArrayList<>();
        for (HealthReminder r : allReminders) {
            if (r.getCategory() == HealthReminder.Category.FOOT) {
                footReminders.add(r);
            }
        }

        if (footReminders.isEmpty()) {
            tvEmptyFootReminders.setVisibility(View.VISIBLE);
            layoutFootRemindersContainer.addView(tvEmptyFootReminders);
            return;
        }

        tvEmptyFootReminders.setVisibility(View.GONE);

        for (HealthReminder rem : footReminders) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutFootRemindersContainer, false);
            ImageView ivIcon = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvDate = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);

            ivIcon.setImageResource(R.drawable.ic_calendar_check);
            ivIcon.setColorFilter(ContextCompat.getColor(this, rem.isCompleted() ? R.color.netra_success : R.color.netra_warning));

            tvTitle.setText(rem.getTitle());
            tvDate.setText("Due: " + rem.getFormattedDueDate() + " (" + rem.getFrequency() + ")");
            tvStatus.setText(rem.isCompleted() ? "Done" : "Pending");

            row.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(rem.getTitle())
                        .setMessage("Due: " + rem.getFormattedDueDate() + "\nFrequency: " + rem.getFrequency())
                        .setPositiveButton(rem.isCompleted() ? "Mark Pending" : "Mark Done", (d, w) -> {
                            Diabetes360Repository.getInstance().toggleReminderCompletion(this, rem.getId());
                            loadFootData();
                        })
                        .setNegativeButton("Delete", (d, w) -> {
                            Diabetes360Repository.getInstance().deleteReminder(this, rem.getId());
                            loadFootData();
                        })
                        .setNeutralButton("Close", null)
                        .show();
            });

            layoutFootRemindersContainer.addView(row);
        }
    }

    private void populateTimeline(List<FootCheckRecord> checks, List<SymptomRecord> symptoms, List<FootConcernRecord> concerns) {
        layoutFootHistoryTimeline.removeAllViews();

        int totalCount = checks.size() + symptoms.size() + concerns.size();
        tvFootHistoryCount.setText(totalCount + (totalCount == 1 ? " record" : " records"));

        if (totalCount == 0) {
            tvEmptyFootTimeline.setVisibility(View.VISIBLE);
            layoutFootHistoryTimeline.addView(tvEmptyFootTimeline);
            return;
        }

        tvEmptyFootTimeline.setVisibility(View.GONE);

        // Render Checks
        for (FootCheckRecord chk : checks) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutFootHistoryTimeline, false);
            ImageView ivIcon = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvDate = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);

            ivIcon.setImageResource(R.drawable.ic_metric_foot);
            ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));

            tvTitle.setText("Inspection: " + chk.getSide() + " (" + chk.getSkinCondition() + ")");
            tvDate.setText(chk.getFormattedDate() + " • Concern: " + chk.getWoundOrConcern());
            tvStatus.setText("Details");

            row.setOnClickListener(v -> showCheckDetailDialog(chk));
            layoutFootHistoryTimeline.addView(row);
        }

        // Render Concerns
        for (FootConcernRecord con : concerns) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutFootHistoryTimeline, false);
            ImageView ivIcon = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvDate = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);

            ivIcon.setImageResource(R.drawable.ic_info_circle);
            ivIcon.setColorFilter(ContextCompat.getColor(this, ContextCompat.getColor(this, R.color.netra_warning)));

            tvTitle.setText("Concern: " + con.getConcernType().getDisplayName());
            tvDate.setText(con.getFormattedDate() + " • " + con.getSide());
            tvStatus.setText(con.getStatus());

            row.setOnClickListener(v -> showConcernDetailDialog(con));
            layoutFootHistoryTimeline.addView(row);
        }
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    private void showAddCheckDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_foot_check, null);
        TextView tvDate = v.findViewById(R.id.tvCheckDatePicked);
        Spinner spSide = v.findViewById(R.id.spinnerFootSide);
        Spinner spSkin = v.findViewById(R.id.spinnerSkinCondition);
        Spinner spWound = v.findViewById(R.id.spinnerWoundObservation);
        EditText etNotes = v.findViewById(R.id.etCheckNotes);

        ArrayAdapter<String> sideAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Both Feet", "Left Foot Only", "Right Foot Only"}
        );
        spSide.setAdapter(sideAdapter);

        ArrayAdapter<String> skinAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Normal / Intact", "Dry / Flaky Skin", "Callus / Thickened Skin", "Cold / Pale Skin"}
        );
        spSkin.setAdapter(skinAdapter);

        ArrayAdapter<String> woundAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"None Observed", "Blister", "Cut / Skin Break", "Redness / Swelling", "Other Observation"}
        );
        spWound.setAdapter(woundAdapter);

        final Calendar cal = Calendar.getInstance();
        final long[] selectedMillis = {cal.getTimeInMillis()};
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(cal.getTime()));

        tvDate.setOnClickListener(view -> {
            new DatePickerDialog(this, (p, y, m, d) -> {
                cal.set(y, m, d);
                selectedMillis[0] = cal.getTimeInMillis();
                tvDate.setText(sdf.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        new AlertDialog.Builder(this)
                .setTitle("Record Foot Self-Inspection")
                .setView(v)
                .setPositiveButton("Save Record", (dialog, which) -> {
                    String side = (String) spSide.getSelectedItem();
                    String skin = (String) spSkin.getSelectedItem();
                    String wound = (String) spWound.getSelectedItem();
                    String notes = etNotes.getText().toString().trim();

                    FootCheckRecord record = new FootCheckRecord(
                            null, patientUuid, selectedMillis[0], side, skin, wound, "", notes, null, "Self-Check"
                    );
                    Diabetes360Repository.getInstance().addFootCheckRecord(this, record);
                    Toast.makeText(this, "Foot inspection recorded.", Toast.LENGTH_SHORT).show();
                    loadFootData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddSymptomDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_symptom, null);
        Spinner spType = v.findViewById(R.id.spinnerSymptomType);
        EditText etLoc = v.findViewById(R.id.etSymptomLocation);
        Spinner spSide = v.findViewById(R.id.spinnerSymptomSide);
        TextView tvDate = v.findViewById(R.id.tvSymptomDatePicked);
        Spinner spStatus = v.findViewById(R.id.spinnerSymptomStatus);
        EditText etDesc = v.findViewById(R.id.etSymptomDescription);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Numbness / Loss of Sensation", "Tingling / Pins & Needles", "Burning Sensation", "Foot / Nerve Pain", "Persistent Cold Sensation", "Other Symptom"}
        );
        spType.setAdapter(typeAdapter);

        ArrayAdapter<String> sideAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Bilateral (Both Feet)", "Left Foot Only", "Right Foot Only"}
        );
        spSide.setAdapter(sideAdapter);

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Active", "Improving", "Resolved"}
        );
        spStatus.setAdapter(statusAdapter);

        final Calendar cal = Calendar.getInstance();
        final long[] selectedMillis = {cal.getTimeInMillis()};
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(cal.getTime()));

        tvDate.setOnClickListener(view -> {
            new DatePickerDialog(this, (p, y, m, d) -> {
                cal.set(y, m, d);
                selectedMillis[0] = cal.getTimeInMillis();
                tvDate.setText(sdf.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        new AlertDialog.Builder(this)
                .setTitle("Track Neurological / Foot Symptom")
                .setView(v)
                .setPositiveButton("Save Symptom", (dialog, which) -> {
                    SymptomRecord.SymptomType type;
                    int sel = spType.getSelectedItemPosition();
                    if (sel == 0) type = SymptomRecord.SymptomType.NUMBNESS;
                    else if (sel == 1) type = SymptomRecord.SymptomType.TINGLING;
                    else if (sel == 2) type = SymptomRecord.SymptomType.BURNING;
                    else if (sel == 3) type = SymptomRecord.SymptomType.PAIN;
                    else if (sel == 4) type = SymptomRecord.SymptomType.COLD_FEET;
                    else type = SymptomRecord.SymptomType.OTHER;

                    String loc = etLoc.getText().toString().trim();
                    if (loc.isEmpty()) loc = "Feet";
                    String side = (String) spSide.getSelectedItem();
                    String status = (String) spStatus.getSelectedItem();
                    String desc = etDesc.getText().toString().trim();

                    SymptomRecord record = new SymptomRecord(
                            null, patientUuid, type, loc, side, selectedMillis[0], desc, status, ""
                    );
                    Diabetes360Repository.getInstance().addSymptomRecord(this, record);
                    Toast.makeText(this, "Symptom entry saved.", Toast.LENGTH_SHORT).show();
                    loadFootData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddConcernDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_foot_concern, null);
        Spinner spType = v.findViewById(R.id.spinnerConcernType);
        TextView tvDate = v.findViewById(R.id.tvConcernDatePicked);
        Spinner spSide = v.findViewById(R.id.spinnerConcernSide);
        EditText etDesc = v.findViewById(R.id.etConcernDescription);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Skin Wound / Ulcer", "Blister / Sore", "Cut / Skin Break", "Localized Swelling", "Localized Redness / Heat", "Other Concern"}
        );
        spType.setAdapter(typeAdapter);

        ArrayAdapter<String> sideAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Right Foot", "Left Foot", "Both"}
        );
        spSide.setAdapter(sideAdapter);

        final Calendar cal = Calendar.getInstance();
        final long[] selectedMillis = {cal.getTimeInMillis()};
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(cal.getTime()));

        tvDate.setOnClickListener(view -> {
            new DatePickerDialog(this, (p, y, m, d) -> {
                cal.set(y, m, d);
                selectedMillis[0] = cal.getTimeInMillis();
                tvDate.setText(sdf.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        new AlertDialog.Builder(this)
                .setTitle("Log Foot Concern or Wound")
                .setView(v)
                .setPositiveButton("Save Record", (dialog, which) -> {
                    String desc = etDesc.getText().toString().trim();
                    if (desc.isEmpty()) {
                        Toast.makeText(this, "Please provide a brief description.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FootConcernRecord.ConcernType type;
                    int sel = spType.getSelectedItemPosition();
                    if (sel == 0) type = FootConcernRecord.ConcernType.WOUND;
                    else if (sel == 1) type = FootConcernRecord.ConcernType.BLISTER;
                    else if (sel == 2) type = FootConcernRecord.ConcernType.SKIN_BREAK;
                    else if (sel == 3) type = FootConcernRecord.ConcernType.SWELLING;
                    else if (sel == 4) type = FootConcernRecord.ConcernType.REDNESS;
                    else type = FootConcernRecord.ConcernType.OTHER;

                    String side = (String) spSide.getSelectedItem();

                    FootConcernRecord record = new FootConcernRecord(
                            null, patientUuid, type, selectedMillis[0], side, desc, null, "", "Active"
                    );
                    Diabetes360Repository.getInstance().addFootConcernRecord(this, record);
                    Toast.makeText(this, "Foot concern logged.", Toast.LENGTH_SHORT).show();
                    loadFootData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCheckDetailDialog(FootCheckRecord chk) {
        new AlertDialog.Builder(this)
                .setTitle("Foot Self-Inspection Record")
                .setMessage(
                        "Date: " + chk.getFormattedDate() + "\n" +
                        "Side: " + chk.getSide() + "\n" +
                        "Skin Condition: " + chk.getSkinCondition() + "\n" +
                        "Wound / Concern: " + chk.getWoundOrConcern() + "\n" +
                        (chk.getNotes() != null && !chk.getNotes().isEmpty() ? "Notes: " + chk.getNotes() + "\n" : "") +
                        "\nRecord ID: " + chk.getId()
                )
                .setPositiveButton("Close", null)
                .show();
    }

    private void showSymptomDetailDialog(SymptomRecord s) {
        new AlertDialog.Builder(this)
                .setTitle(s.getSymptomType().getDisplayName())
                .setMessage(
                        "Location: " + s.getBodyLocation() + " (" + s.getSide() + ")\n" +
                        "Date Noticed: " + s.getFormattedDate() + "\n" +
                        "Current Status: " + s.getStatus() + "\n" +
                        (s.getUserDescription() != null && !s.getUserDescription().isEmpty() ? "Description: " + s.getUserDescription() + "\n" : "") +
                        "\nRecord ID: " + s.getId()
                )
                .setPositiveButton("Close", null)
                .setNegativeButton("Delete Entry", (d, w) -> {
                    Diabetes360Repository.getInstance().deleteSymptomRecord(this, s.getId());
                    Toast.makeText(this, "Symptom deleted.", Toast.LENGTH_SHORT).show();
                    loadFootData();
                })
                .show();
    }

    private void showConcernDetailDialog(FootConcernRecord c) {
        new AlertDialog.Builder(this)
                .setTitle(c.getConcernType().getDisplayName())
                .setMessage(
                        "Noticed: " + c.getFormattedDate() + "\n" +
                        "Side: " + c.getSide() + "\n" +
                        "Description: " + c.getDescription() + "\n" +
                        "Status: " + c.getStatus() + "\n" +
                        "\nRecord ID: " + c.getId()
                )
                .setPositiveButton("Close", null)
                .setNegativeButton("Delete Concern", (d, w) -> {
                    Diabetes360Repository.getInstance().deleteFootConcernRecord(this, c.getId());
                    Toast.makeText(this, "Concern record deleted.", Toast.LENGTH_SHORT).show();
                    loadFootData();
                })
                .show();
    }

    private void showAddReminderDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_health_reminder, null);
        EditText etTitle = v.findViewById(R.id.etReminderTitle);
        TextView tvDate = v.findViewById(R.id.tvReminderDate);
        Spinner spinnerFreq = v.findViewById(R.id.spinnerFrequency);
        EditText etNotes = v.findViewById(R.id.etReminderNotes);

        etTitle.setText("Routine Foot Inspection & Podiatry Check");

        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Weekly", "Every 2 Weeks", "Monthly", "Annual Exam"}
        );
        spinnerFreq.setAdapter(freqAdapter);

        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 7);
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
                .setTitle("Schedule Foot Care Reminder")
                .setView(v)
                .setPositiveButton("Save Reminder", (d, w) -> {
                    String title = etTitle.getText().toString().trim();
                    if (title.isEmpty()) title = "Foot Care Inspection";
                    String freq = (String) spinnerFreq.getSelectedItem();
                    String notes = etNotes.getText().toString().trim();

                    HealthReminder rem = new HealthReminder(
                            null, patientUuid, HealthReminder.Category.FOOT, title, selectedMillis[0], freq, notes
                    );
                    Diabetes360Repository.getInstance().addReminder(this, rem);
                    Toast.makeText(this, "Reminder scheduled.", Toast.LENGTH_SHORT).show();
                    loadFootData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEducationalDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Foot & Nerve Care in Diabetes")
                .setMessage(
                        "Diabetes can affect microvascular blood supply and peripheral nerves in the lower extremities:\n\n" +
                        "1. Daily Self-Checks:\n" +
                        "   Check your feet daily for cuts, blisters, redness, swelling, or nail issues using good lighting and a mirror.\n\n" +
                        "2. Sensory Symptoms:\n" +
                        "   Numbness or tingling may reduce awareness of minor injuries. Tracking onset and location helps your clinician assess peripheral sensation.\n\n" +
                        "3. Urgent Attention:\n" +
                        "   Never attempt to treat deep wounds, corns, or ingrown toenails with sharp tools at home. Seek medical advice for open breaks in the skin."
                )
                .setPositiveButton("Understood", null)
                .show();
    }
}
