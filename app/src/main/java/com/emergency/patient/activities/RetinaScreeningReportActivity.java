package com.emergency.patient.activities;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.emergency.patient.R;
import com.emergency.patient.models.DrSeverityGrade;
import com.emergency.patient.models.LesionCategory;
import com.emergency.patient.models.LesionMaskData;
import com.emergency.patient.models.RetinaReportData;
import com.emergency.patient.network.RetinaReportRepository;
import com.emergency.patient.utils.RetinaReportPdfGenerator;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RetinaScreeningReportActivity — NetraProof AI Retinal Screening Report (Page 06)
 *
 * Consolidates the complete multi-stage retinal screening workflow into an official,
 * publication-grade medical screening report:
 *  - Stage 01: Authenticated Patient Profile & Demographics.
 *  - Stage 02: Retinal Fundus Photograph & Optical Metadata.
 *  - Stage 03: Optical Quality Assessment (Multi-parameter analysis).
 *  - Stage 04: DR Severity Grading (Five-fold EfficientNet-B0 Ensemble with Ordinal Regression).
 *  - Stage 05: Retinal Lesion Spatial Segmentation (U-Net + SE-ResNeXt50).
 *  - Native PDF Report Generation & Secure Android System Sharing via FileProvider.
 *  - Traceability, Model Independence, and Mandatory Clinical Disclaimers.
 */
public class RetinaScreeningReportActivity extends AppCompatActivity {

    // ─── Intent Extra Keys ───────────────────────────────────────────────────
    public static final String EXTRA_DOCUMENT_ID         = "extra_document_id";
    public static final String EXTRA_IMAGE_PATH          = "extra_image_path";
    public static final String EXTRA_FILE_NAME           = "extra_file_name";
    public static final String EXTRA_IMAGE_WIDTH         = "extra_image_width";
    public static final String EXTRA_IMAGE_HEIGHT        = "extra_image_height";
    public static final String EXTRA_IMAGE_SIZE          = "extra_image_size";
    public static final String EXTRA_SESSION_ID          = "extra_session_id";
    public static final String EXTRA_PREDICTED_GRADE     = "extra_predicted_grade";
    public static final String EXTRA_SEGMENTATION_STATE  = "extra_segmentation_state";

    // ─── View References ──────────────────────────────────────────────────────
    private View btnBack, btnDownloadPdf, btnShareReport;
    private TextView tvSessionIdBadge;

    // Report Banner
    private LinearLayout cardReportStatusBanner;
    private ImageView ivReportStatusIcon;
    private TextView tvReportStatusTitle, tvReportStatusSubtitle, badgeReportStatus;

    // Patient & Screening Information
    private TextView tvPatientName, tvPatientDisplayId, tvPatientDemographics, tvReportIdDetail;
    private TextView tvScreeningDateTime, tvSessionIdDetail;

    // Executive Summary
    private TextView badgeQualityStatus, tvQualityDetail;
    private ImageView ivDrStageIcon, ivLesionStageIcon;
    private TextView badgeDrStatus, tvDrDetail, badgeLesionStatus, tvLesionDetail;

    // Image & Optical Quality
    private ImageView ivRetinaThumbnail;
    private TextView tvImageCaption;

    // DR Severity Assessment
    private TextView tvPredictedTier, badgePredictedGrade, tvDrExplanation;
    private TextView scaleTier0, scaleTier1, scaleTier2, scaleTier3, scaleTier4;

    // Lesion Segmentation
    private TextView tvCwsStatus, tvHexStatus, tvHemStatus, tvMaStatus;
    private MaterialButton btnOpenLesionWorkstation;

    // Interpretation & Next Steps
    private TextView tvOverallInterpretation;
    private LinearLayout layoutNextStepsContainer;

    // Action Footers
    private MaterialButton btnDownloadPdfFooter, btnShareReportFooter, btnReturnToDashboard;

    // ─── State ────────────────────────────────────────────────────────────────
    private int documentId = -1;
    private String currentImagePath;
    private String currentFileName = "fundus_retina.jpg";
    private int currentImageWidth = 0;
    private int currentImageHeight = 0;
    private long currentFileSizeBytes = 0;
    private String currentSessionId;
    private String drGradeName;
    private String segStateName;

    private RetinaReportData currentReport;
    private File generatedPdfFile = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retina_screening_report);

        parseIntentExtras();
        initializeViews();
        setupInteractionListeners();
        loadReportData();
        com.emergency.patient.utils.NetraProfileHeaderHelper.setup(this);
        com.emergency.patient.utils.NetraBottomNavHelper.setup(this, com.emergency.patient.utils.NetraBottomNavHelper.TAB_REPORTS);
    }

    // ─── Initialization ───────────────────────────────────────────────────────

    private void parseIntentExtras() {
        if (getIntent() != null) {
            documentId = getIntent().getIntExtra(EXTRA_DOCUMENT_ID, -1);
            currentImagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
            currentFileName = getIntent().getStringExtra(EXTRA_FILE_NAME);
            currentImageWidth = getIntent().getIntExtra(EXTRA_IMAGE_WIDTH, 0);
            currentImageHeight = getIntent().getIntExtra(EXTRA_IMAGE_HEIGHT, 0);
            currentFileSizeBytes = getIntent().getLongExtra(EXTRA_IMAGE_SIZE, 0);
            currentSessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
            drGradeName = getIntent().getStringExtra(EXTRA_PREDICTED_GRADE);
            segStateName = getIntent().getStringExtra(EXTRA_SEGMENTATION_STATE);
        }

        if (currentSessionId == null || currentSessionId.trim().isEmpty()) {
            currentSessionId = "NP-SCR-" + (documentId > 0 ? String.format(Locale.getDefault(), "%05d", documentId) : System.currentTimeMillis() % 100000);
        }
    }

    private void initializeViews() {
        btnBack = findViewById(R.id.btnBack);
        btnDownloadPdf = findViewById(R.id.btnDownloadPdf);
        btnShareReport = findViewById(R.id.btnShareReport);
        tvSessionIdBadge = findViewById(R.id.tvSessionIdBadge);
        tvSessionIdBadge.setText("SESSION: " + currentSessionId);

        // Banner
        cardReportStatusBanner = findViewById(R.id.cardReportStatusBanner);
        ivReportStatusIcon = findViewById(R.id.ivReportStatusIcon);
        tvReportStatusTitle = findViewById(R.id.tvReportStatusTitle);
        tvReportStatusSubtitle = findViewById(R.id.tvReportStatusSubtitle);
        badgeReportStatus = findViewById(R.id.badgeReportStatus);

        // Patient Info
        tvPatientName = findViewById(R.id.tvPatientName);
        tvPatientDisplayId = findViewById(R.id.tvPatientDisplayId);
        tvPatientDemographics = findViewById(R.id.tvPatientDemographics);
        tvReportIdDetail = findViewById(R.id.tvReportIdDetail);
        tvScreeningDateTime = findViewById(R.id.tvScreeningDateTime);
        tvSessionIdDetail = findViewById(R.id.tvSessionIdDetail);

        // Summary
        badgeQualityStatus = findViewById(R.id.badgeQualityStatus);
        tvQualityDetail = findViewById(R.id.tvQualityDetail);
        ivDrStageIcon = findViewById(R.id.ivDrStageIcon);
        badgeDrStatus = findViewById(R.id.badgeDrStatus);
        tvDrDetail = findViewById(R.id.tvDrDetail);
        ivLesionStageIcon = findViewById(R.id.ivLesionStageIcon);
        badgeLesionStatus = findViewById(R.id.badgeLesionStatus);
        tvLesionDetail = findViewById(R.id.tvLesionDetail);

        // Image / Quality
        ivRetinaThumbnail = findViewById(R.id.ivRetinaThumbnail);
        tvImageCaption = findViewById(R.id.tvImageCaption);

        // DR Severity
        tvPredictedTier = findViewById(R.id.tvPredictedTier);
        badgePredictedGrade = findViewById(R.id.badgePredictedGrade);
        tvDrExplanation = findViewById(R.id.tvDrExplanation);
        scaleTier0 = findViewById(R.id.scaleTier0);
        scaleTier1 = findViewById(R.id.scaleTier1);
        scaleTier2 = findViewById(R.id.scaleTier2);
        scaleTier3 = findViewById(R.id.scaleTier3);
        scaleTier4 = findViewById(R.id.scaleTier4);

        // Lesions
        tvCwsStatus = findViewById(R.id.tvCwsStatus);
        tvHexStatus = findViewById(R.id.tvHexStatus);
        tvHemStatus = findViewById(R.id.tvHemStatus);
        tvMaStatus = findViewById(R.id.tvMaStatus);
        btnOpenLesionWorkstation = findViewById(R.id.btnOpenLesionWorkstation);

        // Interpretation & Steps
        tvOverallInterpretation = findViewById(R.id.tvOverallInterpretation);
        layoutNextStepsContainer = findViewById(R.id.layoutNextStepsContainer);

        // Footers
        btnDownloadPdfFooter = findViewById(R.id.btnDownloadPdfFooter);
        btnShareReportFooter = findViewById(R.id.btnShareReportFooter);
        btnReturnToDashboard = findViewById(R.id.btnReturnToDashboard);
    }

    private void setupInteractionListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnReturnToDashboard.setOnClickListener(v -> finish());

        btnDownloadPdf.setOnClickListener(v -> exportPdfReport(false));
        btnDownloadPdfFooter.setOnClickListener(v -> exportPdfReport(false));

        btnShareReport.setOnClickListener(v -> exportPdfReport(true));
        btnShareReportFooter.setOnClickListener(v -> exportPdfReport(true));

        btnOpenLesionWorkstation.setOnClickListener(v -> openLesionSegmentationWorkstation());
    }

    // ─── Data Loading ─────────────────────────────────────────────────────────

    private void loadReportData() {
        RetinaReportRepository.getInstance().compileReport(
                this,
                documentId,
                currentImagePath,
                currentSessionId,
                drGradeName,
                segStateName,
                new RetinaReportRepository.RetinaReportCallback() {
                    @Override
                    public void onProgress(String statusMessage) {
                        tvReportStatusSubtitle.setText(statusMessage);
                    }

                    @Override
                    public void onReportReady(RetinaReportData report) {
                        currentReport = report;
                        populateReportViews(report);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(RetinaScreeningReportActivity.this, "Error compiling report: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void populateReportViews(RetinaReportData report) {
        // Patient & Session Info
        tvPatientName.setText(report.getPatientFullName());
        tvPatientDisplayId.setText(report.getPatientDisplayId());
        tvPatientDemographics.setText(report.getPatientAge() + " • " + report.getPatientGender());
        tvReportIdDetail.setText(report.getReportId());
        tvSessionIdDetail.setText(report.getSessionId());

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm z", Locale.getDefault());
        tvScreeningDateTime.setText(sdf.format(new Date(report.getScreeningTimestamp())));

        // Banner
        if (report.isComplete()) {
            cardReportStatusBanner.setBackgroundResource(R.drawable.bg_quality_card_passed);
            ivReportStatusIcon.setImageResource(R.drawable.ic_check_circle);
            ivReportStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_success));
            tvReportStatusTitle.setText("Complete Screening Report");
            tvReportStatusSubtitle.setText("All screening stages evaluated successfully.");
            badgeReportStatus.setText("Complete");
            badgeReportStatus.setBackgroundResource(R.drawable.bg_pill_status_good);
            badgeReportStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_success));
        } else {
            cardReportStatusBanner.setBackgroundResource(R.drawable.bg_quality_card_pending);
            ivReportStatusIcon.setImageResource(R.drawable.ic_document_report);
            ivReportStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));
            tvReportStatusTitle.setText("Partial Screening Report");
            tvReportStatusSubtitle.setText("Optical quality verified. AI classification pending server deployment.");
            badgeReportStatus.setText("Partial");
            badgeReportStatus.setBackgroundResource(R.drawable.bg_pill_status_not_assessed);
            badgeReportStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_deep_navy));
        }

        // Summary Stage Badges
        badgeQualityStatus.setText("Passed");
        badgeQualityStatus.setBackgroundResource(R.drawable.bg_pill_status_good);
        badgeQualityStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_success));

        DrSeverityGrade grade = report.getDrSeverityGrade();
        if (grade != null) {
            ivDrStageIcon.setImageResource(R.drawable.ic_stage_completed);
            badgeDrStatus.setText(grade.getShortCategoryName());
            badgeDrStatus.setBackgroundResource(grade.getBadgeBgResId());
            badgeDrStatus.setTextColor(ContextCompat.getColor(this, grade.getColorResId()));
            tvDrDetail.setText("Predicted: " + grade.getFullCategoryName());

            tvPredictedTier.setText(grade.getFullCategoryName());
            badgePredictedGrade.setText(grade.getGradeBadgeText());
            badgePredictedGrade.setBackgroundResource(grade.getBadgeBgResId());
            badgePredictedGrade.setTextColor(ContextCompat.getColor(this, grade.getColorResId()));
            tvDrExplanation.setText(grade.getClinicalInterpretation());

            highlightSeverityScale(grade);
        } else {
            ivDrStageIcon.setImageResource(R.drawable.ic_stage_in_progress);
            badgeDrStatus.setText("Service Pending");
            badgeDrStatus.setBackgroundResource(R.drawable.bg_pill_status_not_assessed);
            badgeDrStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_deep_navy));
            tvDrDetail.setText("Five-fold EfficientNet-B0 ensemble ordinal regression");

            tvPredictedTier.setText("Prediction Pending Deployment");
            badgePredictedGrade.setText("Service Pending");
            badgePredictedGrade.setBackgroundResource(R.drawable.bg_pill_status_not_assessed);
            badgePredictedGrade.setTextColor(ContextCompat.getColor(this, R.color.netra_deep_navy));
            tvDrExplanation.setText(report.getDrExplanation());

            resetSeverityScale();
        }

        // Lesions
        boolean segDone = report.getSegmentationState().equalsIgnoreCase("Completed");
        badgeLesionStatus.setText(segDone ? "4 Classes" : "Service Pending");
        badgeLesionStatus.setBackgroundResource(segDone ? R.drawable.bg_pill_status_good : R.drawable.bg_pill_status_not_assessed);
        badgeLesionStatus.setTextColor(ContextCompat.getColor(this, segDone ? R.color.netra_success : R.color.netra_deep_navy));

        tvCwsStatus.setText(segDone ? "Evaluated" : "Service Pending");
        tvHexStatus.setText(segDone ? "Evaluated" : "Service Pending");
        tvHemStatus.setText(segDone ? "Evaluated" : "Service Pending");
        tvMaStatus.setText(segDone ? "Evaluated" : "Service Pending");

        // Image & Thumbnail
        tvImageCaption.setText("Image: " + report.getFileName() + " • " + report.getFormattedDimensions() + " • " + report.getFormattedFileSize() + " • Aspect Ratio Preserved");
        loadThumbnailBitmap(report.getImagePath());

        // Interpretation
        tvOverallInterpretation.setText(report.getScreeningInterpretation());

        // Recommended Next Steps List
        populateNextSteps(report.getRecommendedNextSteps());
    }

    private void highlightSeverityScale(DrSeverityGrade grade) {
        resetSeverityScale();
        TextView targetTier = null;
        switch (grade) {
            case GRADE_0: targetTier = scaleTier0; break;
            case GRADE_1: targetTier = scaleTier1; break;
            case GRADE_2: targetTier = scaleTier2; break;
            case GRADE_3: targetTier = scaleTier3; break;
            case GRADE_4: targetTier = scaleTier4; break;
        }
        if (targetTier != null) {
            targetTier.setBackgroundResource(R.drawable.bg_mode_pill_selected);
            targetTier.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        }
    }

    private void resetSeverityScale() {
        TextView[] tiers = {scaleTier0, scaleTier1, scaleTier2, scaleTier3, scaleTier4};
        for (TextView t : tiers) {
            t.setBackgroundResource(R.drawable.bg_mode_pill_unselected);
            t.setTextColor(ContextCompat.getColor(this, R.color.netra_text_primary));
        }
    }

    private void loadThumbnailBitmap(String path) {
        if (path == null || !(new File(path).exists())) return;

        new Thread(() -> {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, opts);
                int sample = 1;
                while ((opts.outWidth / sample) > 512 || (opts.outHeight / sample) > 512) {
                    sample *= 2;
                }
                opts.inSampleSize = sample;
                opts.inJustDecodeBounds = false;
                Bitmap bmp = BitmapFactory.decodeFile(path, opts);
                mainHandler.post(() -> {
                    if (bmp != null) {
                        ivRetinaThumbnail.setImageBitmap(bmp);
                    }
                });
            } catch (Throwable ignored) {}
        }).start();
    }

    private void populateNextSteps(List<String> nextSteps) {
        layoutNextStepsContainer.removeAllViews();
        if (nextSteps == null || nextSteps.isEmpty()) return;

        for (String step : nextSteps) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 4, 0, 8);

            TextView bullet = new TextView(this);
            bullet.setText("•");
            bullet.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));
            bullet.setTextSize(14);
            bullet.setPadding(0, 0, 8, 0);

            TextView tvStep = new TextView(this);
            tvStep.setText(step);
            tvStep.setTextColor(ContextCompat.getColor(this, R.color.netra_text_primary));
            tvStep.setTextSize(12);
            tvStep.setLineSpacing(2, 1.15f);

            row.addView(bullet);
            row.addView(tvStep);
            layoutNextStepsContainer.addView(row);
        }
    }

    // ─── Actions & Export ─────────────────────────────────────────────────────

    private void openLesionSegmentationWorkstation() {
        Intent intent = new Intent(this, RetinaLesionSegmentationActivity.class);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_DOCUMENT_ID, documentId);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_IMAGE_PATH, currentImagePath);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_FILE_NAME, currentFileName);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_IMAGE_WIDTH, currentImageWidth);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_IMAGE_HEIGHT, currentImageHeight);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_IMAGE_SIZE, currentFileSizeBytes);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_SESSION_ID, currentSessionId);
        if (drGradeName != null) {
            intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_PREDICTED_GRADE, drGradeName);
        }
        startActivity(intent);
    }

    private void exportPdfReport(boolean autoShare) {
        if (currentReport == null) {
            Toast.makeText(this, "Screening report data not yet ready.", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Generating NetraProof Screening Report PDF...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                File pdf = RetinaReportPdfGenerator.generatePdf(this, currentReport);
                generatedPdfFile = pdf;

                mainHandler.post(() -> {
                    progressDialog.dismiss();
                    if (autoShare) {
                        sharePdfFile(pdf);
                    } else {
                        showPdfReadyDialog(pdf);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to generate PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showPdfReadyDialog(File pdf) {
        new AlertDialog.Builder(this)
                .setTitle("Screening Report PDF Generated")
                .setMessage("Your official NetraProof retinal screening report has been compiled:\n\n" + pdf.getName() + "\n\nSaved to secure document storage.")
                .setPositiveButton("View PDF", (d, w) -> viewPdfFile(pdf))
                .setNeutralButton("Share PDF", (d, w) -> sharePdfFile(pdf))
                .setNegativeButton("Done", null)
                .show();
    }

    private void viewPdfFile(File pdf) {
        try {
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", pdf);
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(contentUri, "application/pdf");
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(viewIntent, "Open Screening Report"));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open PDF viewer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sharePdfFile(File pdf) {
        try {
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", pdf);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "NetraProof Retinal Screening Report - " + currentSessionId);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Please find attached the official NetraProof AI Retinal Screening Report for session: " + currentSessionId);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share Screening Report with Physician"));
        } catch (Exception e) {
            Toast.makeText(this, "Could not share PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

