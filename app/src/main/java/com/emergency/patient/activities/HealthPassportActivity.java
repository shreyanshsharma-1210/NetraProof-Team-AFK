package com.emergency.patient.activities;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.emergency.patient.BuildConfig;
import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.HealthDocumentEntity;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.models.diabetes360.BloodPressureRecord;
import com.emergency.patient.models.diabetes360.Diabetes360Data;
import com.emergency.patient.models.diabetes360.Diabetes360Repository;
import com.emergency.patient.models.diabetes360.FootCheckRecord;
import com.emergency.patient.models.diabetes360.FootConcernRecord;
import com.emergency.patient.models.diabetes360.GlucoseRecord;
import com.emergency.patient.models.diabetes360.Hba1cRecord;
import com.emergency.patient.models.diabetes360.KidneyRecord;
import com.emergency.patient.models.diabetes360.LipidProfileRecord;
import com.emergency.patient.models.diabetes360.SymptomRecord;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.utils.LocalStorageManager;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HealthPassportActivity — NetraProof Central Digital Health-Record Library (Page 12).
 *
 * Consolidates:
 *  - Medical Document Library (with secure upload, rename, view, and deletion).
 *  - Retinal Screening History (linking to individual reports).
 *  - Direct access to all 5 health modules (Retina, Kidney, Heart, Foot, Glucose).
 *  - Comprehensive Unified Health Timeline aggregating dated clinical events.
 */
public class HealthPassportActivity extends AppCompatActivity {

    private ImageView btnPassportBack;
    private TextView tvPassportPatientSubtitle;
    private MaterialButton btnHeaderUploadDoc;

    // Overview Stats
    private TextView tvTotalDocumentsCount, tvTotalScreeningsCount;
    private TextView tvTotalDiabetes360RecordsCount, tvMostRecentRecordDate;

    // Category Cards
    private View cardCategoryRetina, cardCategoryKidney, cardCategoryHeart;
    private View cardCategoryFoot, cardCategoryGlucose;
    private TextView tvCategoryRetinaSubtitle, tvCategoryKidneySubtitle;
    private TextView tvCategoryHeartSubtitle, tvCategoryFootSubtitle, tvCategoryGlucoseSubtitle;

    // Document Library
    private TextView tvDocumentLibraryCount, tvEmptyDocumentsNotice;
    private LinearLayout layoutDocumentsListContainer;

    // Screenings History
    private TextView tvScreeningsHistoryCount, tvEmptyScreeningsNotice;
    private LinearLayout layoutScreeningsListContainer;

    // Unified Timeline
    private TextView tvUnifiedTimelineCount, tvEmptyUnifiedTimelineNotice;
    private LinearLayout layoutUnifiedTimelineContainer;

    private String patientUuid;
    private ActivityResultLauncher<String> documentUploadLauncher;

    private static class TimelineEvent {
        long timestampMillis;
        String title;
        String category;
        String status;
        int iconRes;
        int iconTintRes;
        Runnable onClick;

        TimelineEvent(long timestampMillis, String title, String category, String status,
                      int iconRes, int iconTintRes, Runnable onClick) {
            this.timestampMillis = timestampMillis;
            this.title = title;
            this.category = category;
            this.status = status;
            this.iconRes = iconRes;
            this.iconTintRes = iconTintRes;
            this.onClick = onClick;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_passport);

        patientUuid = TokenManager.getUUID(this);
        if (patientUuid == null || patientUuid.trim().isEmpty()) {
            patientUuid = "default_patient";
        }

        initViews();
        setupUploadLauncher();
        setupClickListeners();
        loadPatientProfileHeader();
        loadAllPassportData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllPassportData();
    }

    private void initViews() {
        btnPassportBack = findViewById(R.id.btnPassportBack);
        tvPassportPatientSubtitle = findViewById(R.id.tvPassportPatientSubtitle);
        btnHeaderUploadDoc = findViewById(R.id.btnHeaderUploadDoc);

        // Stats
        tvTotalDocumentsCount = findViewById(R.id.tvTotalDocumentsCount);
        tvTotalScreeningsCount = findViewById(R.id.tvTotalScreeningsCount);
        tvTotalDiabetes360RecordsCount = findViewById(R.id.tvTotalDiabetes360RecordsCount);
        tvMostRecentRecordDate = findViewById(R.id.tvMostRecentRecordDate);

        // Categories
        cardCategoryRetina = findViewById(R.id.cardCategoryRetina);
        cardCategoryKidney = findViewById(R.id.cardCategoryKidney);
        cardCategoryHeart = findViewById(R.id.cardCategoryHeart);
        cardCategoryFoot = findViewById(R.id.cardCategoryFoot);
        cardCategoryGlucose = findViewById(R.id.cardCategoryGlucose);

        tvCategoryRetinaSubtitle = findViewById(R.id.tvCategoryRetinaSubtitle);
        tvCategoryKidneySubtitle = findViewById(R.id.tvCategoryKidneySubtitle);
        tvCategoryHeartSubtitle = findViewById(R.id.tvCategoryHeartSubtitle);
        tvCategoryFootSubtitle = findViewById(R.id.tvCategoryFootSubtitle);
        tvCategoryGlucoseSubtitle = findViewById(R.id.tvCategoryGlucoseSubtitle);

        // Library
        tvDocumentLibraryCount = findViewById(R.id.tvDocumentLibraryCount);
        tvEmptyDocumentsNotice = findViewById(R.id.tvEmptyDocumentsNotice);
        layoutDocumentsListContainer = findViewById(R.id.layoutDocumentsListContainer);

        // Screenings
        tvScreeningsHistoryCount = findViewById(R.id.tvScreeningsHistoryCount);
        tvEmptyScreeningsNotice = findViewById(R.id.tvEmptyScreeningsNotice);
        layoutScreeningsListContainer = findViewById(R.id.layoutScreeningsListContainer);

        // Timeline
        tvUnifiedTimelineCount = findViewById(R.id.tvUnifiedTimelineCount);
        tvEmptyUnifiedTimelineNotice = findViewById(R.id.tvEmptyUnifiedTimelineNotice);
        layoutUnifiedTimelineContainer = findViewById(R.id.layoutUnifiedTimelineContainer);
    }

    private void setupUploadLauncher() {
        documentUploadLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        handleUploadedDocument(uri);
                    }
                }
        );
    }

    private void setupClickListeners() {
        btnPassportBack.setOnClickListener(v -> finish());
        btnHeaderUploadDoc.setOnClickListener(v -> documentUploadLauncher.launch("*/*"));

        // Module routing
        cardCategoryRetina.setOnClickListener(v -> startActivity(new Intent(this, RetinaScreeningActivity.class)));
        cardCategoryKidney.setOnClickListener(v -> startActivity(new Intent(this, KidneyHealthActivity.class)));
        cardCategoryHeart.setOnClickListener(v -> startActivity(new Intent(this, HeartHealthActivity.class)));
        cardCategoryFoot.setOnClickListener(v -> startActivity(new Intent(this, FootNerveCareActivity.class)));
        cardCategoryGlucose.setOnClickListener(v -> startActivity(new Intent(this, GlucoseHba1cTrackingActivity.class)));
    }

    private void loadPatientProfileHeader() {
        new Thread(() -> {
            PatientEntity pat = null;
            try {
                pat = AppDatabaseProvider.getInstance(this).patientDao().getPatient(patientUuid);
            } catch (Exception ignored) {}

            final PatientEntity finalPat = pat;
            runOnUiThread(() -> {
                if (finalPat != null && finalPat.fullName != null && !finalPat.fullName.trim().isEmpty()) {
                    tvPassportPatientSubtitle.setText("Patient: " + finalPat.fullName.trim() + " • NetraID: " + patientUuid.substring(0, Math.min(8, patientUuid.length())));
                } else {
                    tvPassportPatientSubtitle.setText("Your health records, securely organized.");
                }
            });
        }).start();
    }

    private void handleUploadedDocument(Uri uri) {
        String originalName = queryFileName(uri);
        if (originalName == null || originalName.trim().isEmpty()) {
            originalName = "medical_doc_" + System.currentTimeMillis();
        }

        String savedPath = LocalStorageManager.saveFileToInternalStorage(this, uri, originalName);
        if (savedPath != null) {
            final String finalName = originalName;
            new Thread(() -> {
                HealthDocumentEntity doc = new HealthDocumentEntity(patientUuid, finalName, savedPath);
                doc.extractionStatus = "uploaded";
                AppDatabaseProvider.getInstance(this).healthDocumentDao().insertDocument(doc);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Document saved to Health Passport.", Toast.LENGTH_SHORT).show();
                    loadAllPassportData();
                });
            }).start();
        } else {
            Toast.makeText(this, "Failed to import document.", Toast.LENGTH_SHORT).show();
        }
    }

    private String queryFileName(Uri uri) {
        String result = null;
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                result = cursor.getString(0);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void loadAllPassportData() {
        new Thread(() -> {
            // Load Documents from Room DB
            List<HealthDocumentEntity> allDocs = new ArrayList<>();
            try {
                allDocs = AppDatabaseProvider.getInstance(this).healthDocumentDao().getDocumentsForPatient(patientUuid);
            } catch (Exception ignored) {}

            // Load Diabetes360 Data
            Diabetes360Data d360 = Diabetes360Repository.getInstance().getData(this, patientUuid);

            final List<HealthDocumentEntity> docs = allDocs;
            runOnUiThread(() -> {
                populatePassportContent(docs, d360);
            });
        }).start();
    }

    private void populatePassportContent(List<HealthDocumentEntity> docs, Diabetes360Data d360) {
        List<HealthDocumentEntity> screeningDocs = new ArrayList<>();
        List<HealthDocumentEntity> generalDocs = new ArrayList<>();

        for (HealthDocumentEntity doc : docs) {
            String name = (doc.displayName != null ? doc.displayName : "").toLowerCase(Locale.ROOT);
            String status = (doc.extractionStatus != null ? doc.extractionStatus : "").toLowerCase(Locale.ROOT);
            if (name.contains("retina") || status.contains("quality") || status.contains("dr_")) {
                screeningDocs.add(doc);
            } else {
                generalDocs.add(doc);
            }
        }

        // 1. Overview Counts
        int d360Count = d360.getKidneyRecords().size() + d360.getBloodPressureRecords().size() +
                d360.getLipidProfileRecords().size() + d360.getFootCheckRecords().size() +
                d360.getSymptomRecords().size() + d360.getFootConcernRecords().size() +
                d360.getGlucoseRecords().size() + d360.getHba1cRecords().size();

        tvTotalDocumentsCount.setText(String.valueOf(generalDocs.size()));
        tvTotalScreeningsCount.setText(String.valueOf(screeningDocs.size()));
        tvTotalDiabetes360RecordsCount.setText(String.valueOf(d360Count));

        // Latest activity date calculation
        long latestMillis = 0;
        if (!docs.isEmpty()) latestMillis = Math.max(latestMillis, docs.get(0).uploadTimestamp);
        if (!d360.getGlucoseRecords().isEmpty()) latestMillis = Math.max(latestMillis, d360.getGlucoseRecords().get(0).getDateMillis());
        if (!d360.getKidneyRecords().isEmpty()) latestMillis = Math.max(latestMillis, d360.getKidneyRecords().get(0).getTestDateMillis());
        if (!d360.getBloodPressureRecords().isEmpty()) latestMillis = Math.max(latestMillis, d360.getBloodPressureRecords().get(0).getMeasurementDateMillis());

        if (latestMillis > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            tvMostRecentRecordDate.setText(sdf.format(new Date(latestMillis)));
        } else {
            tvMostRecentRecordDate.setText("None logged");
        }

        // 2. Category Subtitles
        tvCategoryRetinaSubtitle.setText(screeningDocs.size() + (screeningDocs.size() == 1 ? " session" : " sessions") + " on record");
        tvCategoryKidneySubtitle.setText(d360.getKidneyRecords().size() + " kidney lab tests recorded");
        tvCategoryHeartSubtitle.setText(d360.getBloodPressureRecords().size() + " BP readings • " + d360.getLipidProfileRecords().size() + " lipid profiles");
        tvCategoryFootSubtitle.setText(d360.getFootCheckRecords().size() + " checks • " + d360.getSymptomRecords().size() + " symptoms");
        tvCategoryGlucoseSubtitle.setText(d360.getGlucoseRecords().size() + " glucose logs • " + d360.getHba1cRecords().size() + " HbA1c tests");

        // 3. Document Library Section
        renderDocumentLibrary(generalDocs);

        // 4. Retinal Screening History Section
        renderScreeningHistory(screeningDocs);

        // 5. Unified Timeline Section
        renderUnifiedTimeline(docs, d360);
    }

    private void renderDocumentLibrary(List<HealthDocumentEntity> generalDocs) {
        layoutDocumentsListContainer.removeAllViews();
        tvDocumentLibraryCount.setText(generalDocs.size() + (generalDocs.size() == 1 ? " file" : " files"));

        if (generalDocs.isEmpty()) {
            tvEmptyDocumentsNotice.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyDocumentsNotice.setVisibility(View.GONE);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

        for (HealthDocumentEntity doc : generalDocs) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutDocumentsListContainer, false);
            ImageView iv = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvTime = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);

            iv.setImageResource(R.drawable.ic_action_upload);
            iv.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));

            tvTitle.setText(doc.displayName != null ? doc.displayName : "Health Document");
            tvTime.setText(sdf.format(new Date(doc.uploadTimestamp)));
            tvStatus.setText("Actions");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

            row.setOnClickListener(v -> showDocumentOptionsDialog(doc));
            layoutDocumentsListContainer.addView(row);
        }
    }

    private void showDocumentOptionsDialog(HealthDocumentEntity doc) {
        String[] options = {"Open / View Document", "Rename Document", "Delete Document"};
        new AlertDialog.Builder(this)
                .setTitle(doc.displayName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openDocumentFile(doc);
                    } else if (which == 1) {
                        showRenameDialog(doc);
                    } else if (which == 2) {
                        confirmDeleteDocument(doc);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openDocumentFile(HealthDocumentEntity doc) {
        if (doc.internalFilePath == null) {
            Toast.makeText(this, "File reference not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(doc.internalFilePath);
        if (!file.exists()) {
            Toast.makeText(this, "Document file no longer exists on device storage.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri contentUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open Document"));
        } catch (Exception e) {
            Toast.makeText(this, "No compatible application found to open this document: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showRenameDialog(HealthDocumentEntity doc) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_rename_document, null);
        EditText etName = v.findViewById(R.id.etDocumentNewName);
        etName.setText(doc.displayName);

        new AlertDialog.Builder(this)
                .setView(v)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = etName.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        doc.displayName = newName;
                        new Thread(() -> {
                            AppDatabaseProvider.getInstance(this).healthDocumentDao().updateDisplayName(doc.id, newName);
                            runOnUiThread(this::loadAllPassportData);
                        }).start();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteDocument(HealthDocumentEntity doc) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Document")
                .setMessage("Are you sure you want to permanently delete \"" + doc.displayName + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        if (doc.internalFilePath != null) {
                            File f = new File(doc.internalFilePath);
                            if (f.exists()) f.delete();
                        }
                        AppDatabaseProvider.getInstance(this).healthDocumentDao().deleteDocument(doc.id);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Document deleted.", Toast.LENGTH_SHORT).show();
                            loadAllPassportData();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void renderScreeningHistory(List<HealthDocumentEntity> screeningDocs) {
        layoutScreeningsListContainer.removeAllViews();
        tvScreeningsHistoryCount.setText(screeningDocs.size() + (screeningDocs.size() == 1 ? " session" : " sessions"));

        if (screeningDocs.isEmpty()) {
            tvEmptyScreeningsNotice.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyScreeningsNotice.setVisibility(View.GONE);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

        for (HealthDocumentEntity doc : screeningDocs) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutScreeningsListContainer, false);
            ImageView iv = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvTime = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);

            iv.setImageResource(R.drawable.ic_action_retina);
            iv.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));

            String title = doc.displayName != null ? doc.displayName : "Retina Screening Session";
            tvTitle.setText(title);
            tvTime.setText(sdf.format(new Date(doc.uploadTimestamp)));
            tvStatus.setText("View Report");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, RetinaScreeningReportActivity.class);
                intent.putExtra("document_id", doc.id);
                intent.putExtra("image_path", doc.internalFilePath);
                startActivity(intent);
            });

            layoutScreeningsListContainer.addView(row);
        }
    }

    private void renderUnifiedTimeline(List<HealthDocumentEntity> docs, Diabetes360Data d360) {
        layoutUnifiedTimelineContainer.removeAllViews();
        List<TimelineEvent> events = new ArrayList<>();

        // From Documents
        for (HealthDocumentEntity doc : docs) {
            events.add(new TimelineEvent(
                    doc.uploadTimestamp,
                    "Document: " + (doc.displayName != null ? doc.displayName : "Health File"),
                    "Medical Records",
                    "Uploaded",
                    R.drawable.ic_action_upload,
                    R.color.netra_primary_teal,
                    () -> showDocumentOptionsDialog(doc)
            ));
        }

        // From Kidney Records
        for (KidneyRecord k : d360.getKidneyRecords()) {
            events.add(new TimelineEvent(
                    k.getTestDateMillis(),
                    k.getTestType().getDisplayName() + ": " + k.getFormattedValueWithUnit(),
                    "Kidney Health • " + k.getSource(),
                    "Laboratory",
                    R.drawable.ic_metric_kidney,
                    R.color.netra_primary_teal,
                    () -> startActivity(new Intent(this, KidneyHealthActivity.class))
            ));
        }

        // From BP Records
        for (BloodPressureRecord bp : d360.getBloodPressureRecords()) {
            events.add(new TimelineEvent(
                    bp.getMeasurementDateMillis(),
                    "Blood Pressure: " + bp.getFormattedReading(),
                    "Cardiovascular • " + bp.getSource(),
                    "Logged",
                    R.drawable.ic_blood_pressure,
                    R.color.netra_primary_teal,
                    () -> startActivity(new Intent(this, HeartHealthActivity.class))
            ));
        }

        // From Lipid Records
        for (LipidProfileRecord lip : d360.getLipidProfileRecords()) {
            events.add(new TimelineEvent(
                    lip.getTestDateMillis(),
                    "Lipid Profile: " + lip.getSummaryText(),
                    "Cardiovascular • " + lip.getSource(),
                    "Laboratory",
                    R.drawable.ic_lipid,
                    R.color.netra_orange,
                    () -> startActivity(new Intent(this, HeartHealthActivity.class))
            ));
        }

        // From Foot Checks
        for (FootCheckRecord fc : d360.getFootCheckRecords()) {
            events.add(new TimelineEvent(
                    fc.getCheckDateMillis(),
                    "Foot Check: " + fc.getSide() + " (" + fc.getSkinCondition() + ")",
                    "Foot & Nerve Care",
                    "Self-Check",
                    R.drawable.ic_metric_foot,
                    R.color.netra_primary_teal,
                    () -> startActivity(new Intent(this, FootNerveCareActivity.class))
            ));
        }

        // From Symptoms
        for (SymptomRecord sym : d360.getSymptomRecords()) {
            events.add(new TimelineEvent(
                    sym.getDateMillis(),
                    "Symptom: " + sym.getSymptomType().getDisplayName(),
                    "Foot & Nerve Care • " + sym.getBodyLocation(),
                    sym.getStatus(),
                    R.drawable.ic_nerve,
                    R.color.netra_warning,
                    () -> startActivity(new Intent(this, FootNerveCareActivity.class))
            ));
        }

        // From Foot Concerns
        for (FootConcernRecord con : d360.getFootConcernRecords()) {
            events.add(new TimelineEvent(
                    con.getDateNoticedMillis(),
                    "Concern: " + con.getConcernType().getDisplayName() + " (" + con.getSide() + ")",
                    "Foot & Nerve Care",
                    con.getStatus(),
                    R.drawable.ic_info_circle,
                    R.color.netra_warning,
                    () -> startActivity(new Intent(this, FootNerveCareActivity.class))
            ));
        }

        // From Glucose Records
        for (GlucoseRecord g : d360.getGlucoseRecords()) {
            events.add(new TimelineEvent(
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
        for (Hba1cRecord a : d360.getHba1cRecords()) {
            events.add(new TimelineEvent(
                    a.getTestDateMillis(),
                    "HbA1c Lab Test: " + a.getFormattedValueWithUnit(),
                    "Glucose Tracking • " + a.getSource(),
                    "Laboratory",
                    R.drawable.ic_metric_hba1c,
                    R.color.netra_orange,
                    () -> startActivity(new Intent(this, GlucoseHba1cTrackingActivity.class))
            ));
        }

        events.sort((a, b) -> Long.compare(b.timestampMillis, a.timestampMillis));

        tvUnifiedTimelineCount.setText(events.size() + (events.size() == 1 ? " event" : " events"));

        if (events.isEmpty()) {
            tvEmptyUnifiedTimelineNotice.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyUnifiedTimelineNotice.setVisibility(View.GONE);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

        int limit = Math.min(events.size(), 15);
        for (int i = 0; i < limit; i++) {
            TimelineEvent item = events.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_dashboard_activity_row, layoutUnifiedTimelineContainer, false);
            ImageView iv = row.findViewById(R.id.ivActivityRowIcon);
            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvTime = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);

            iv.setImageResource(item.iconRes);
            iv.setColorFilter(ContextCompat.getColor(this, item.iconTintRes));

            tvTitle.setText(item.title);
            tvTime.setText(sdf.format(new Date(item.timestampMillis)) + " • " + item.category);
            tvStatus.setText(item.status);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

            row.setOnClickListener(v -> {
                if (item.onClick != null) item.onClick.run();
            });

            layoutUnifiedTimelineContainer.addView(row);
        }
    }
}

