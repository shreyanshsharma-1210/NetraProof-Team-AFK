package com.emergency.patient.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.emergency.patient.R;
import com.emergency.patient.models.DrSeverityGrade;
import com.emergency.patient.models.LesionCategory;
import com.emergency.patient.models.LesionMaskData;
import com.emergency.patient.models.RetinaSegmentationResult;
import com.emergency.patient.models.RetinaSegmentationResult.SegmentationState;
import com.emergency.patient.network.RetinaSegmentationRepository;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.views.RetinaOverlayView;
import com.emergency.patient.views.RetinaOverlayView.DisplayMode;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RetinaLesionSegmentationActivity — NetraProof Retinal Lesion Segmentation & Visualization (Page 05)
 *
 * Implements Stage 04 of the NetraProof screening pipeline:
 *  - Visualizes retinal lesions across 4 categories (Cotton Wool Spots, Hard Exudates, Hemorrhages, Microaneurysms)
 *    segmented by the U-Net + SE-ResNeXt50 architecture.
 *  - Interactive multi-layer blending canvas (RetinaOverlayView) with real-time opacity slider (10% to 100%).
 *  - Three visualization modes: Standard Overlay, Side-by-Side Split, and Before/After comparison.
 *  - Four independent layer switches with instant layer compositing, Show All, and Hide All controls.
 *  - Enforces clinical model independence: lesion segmentation operates as an independent stage and
 *    does not validate or alter the Stage 03 DR severity grade.
 *  - Downstream bridge to Stage 05 (AI Screening Report).
 */
public class RetinaLesionSegmentationActivity extends AppCompatActivity {

    // ─── Intent Extra Keys ───────────────────────────────────────────────────
    public static final String EXTRA_DOCUMENT_ID    = "extra_document_id";
    public static final String EXTRA_IMAGE_PATH     = "extra_image_path";
    public static final String EXTRA_FILE_NAME      = "extra_file_name";
    public static final String EXTRA_IMAGE_WIDTH    = "extra_image_width";
    public static final String EXTRA_IMAGE_HEIGHT   = "extra_image_height";
    public static final String EXTRA_IMAGE_SIZE     = "extra_image_size";
    public static final String EXTRA_SESSION_ID     = "extra_session_id";
    public static final String EXTRA_PREDICTED_GRADE = "extra_predicted_grade";

    // ─── View References ──────────────────────────────────────────────────────
    private View btnBack, btnHelp;
    private TextView tvSessionIdBadge;

    // Stepper
    private ImageView ivStage4Icon, ivStage5Icon;
    private TextView tvStage4Status, tvStage5Status, tvStage3Status, tvPipelineStageIndicator;

    // Mode Selector Pills
    private TextView btnModeOverlay, btnModeSideBySide, btnModeBeforeAfter;

    // Retina Canvas & Controls
    private RetinaOverlayView retinaOverlayView;
    private View btnZoomIn, btnZoomOut, btnResetZoom, btnFullscreen;
    private LinearLayout layoutBeforeAfterBar;
    private TextView btnToggleBefore, btnToggleAfter;
    private TextView tvImageDimensions, tvImageResolutionTag;

    // Opacity Controls
    private SeekBar sbOpacity;
    private TextView tvOpacityValue, btnResetOpacity;

    // Layer Controls
    private TextView btnShowAllLayers, btnHideAllLayers;
    private View layerCottonWool, layerHardExudates, layerHemorrhages, layerMicroaneurysms;

    // Summary Card
    private TextView badgeSummaryStatus, tvSummaryModelName, tvSummaryModelVersion, tvSummaryTimestamp;

    // CTA
    private MaterialButton btnGenerateReport;
    private MaterialButton btnQuickGenerateReport;

    // Stepper Interactive Rows
    private View rowStage1, rowStage2, rowStage3, rowStage4, rowStage5;

    // ─── State ────────────────────────────────────────────────────────────────
    private int documentId = -1;
    private String currentImagePath;
    private String currentFileName = "fundus_retina.jpg";
    private int currentImageWidth = 0;
    private int currentImageHeight = 0;
    private long currentFileSizeBytes = 0;
    private String currentSessionId;
    private String patientUuid;
    private DrSeverityGrade drSeverityGrade;

    private Bitmap baseRetinaBitmap = null;
    private RetinaSegmentationResult currentResult = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retina_lesion_segmentation);

        parseIntentExtras();
        initializeViews();
        loadPatientContext();
        setupInteractionListeners();
        loadRetinalImage();
        executeSegmentationPipeline();
        com.emergency.patient.utils.NetraProfileHeaderHelper.setup(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (baseRetinaBitmap != null && !baseRetinaBitmap.isRecycled()) {
            baseRetinaBitmap.recycle();
            baseRetinaBitmap = null;
        }
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

            String gradeStr = getIntent().getStringExtra(EXTRA_PREDICTED_GRADE);
            if (gradeStr != null) {
                try {
                    drSeverityGrade = DrSeverityGrade.valueOf(gradeStr);
                } catch (Exception ignored) {
                    drSeverityGrade = null;
                }
            }
        }

        if (currentSessionId == null || currentSessionId.trim().isEmpty()) {
            currentSessionId = "NP-SCR-" + (documentId > 0 ? String.format(Locale.getDefault(), "%05d", documentId) : System.currentTimeMillis() % 100000);
        }
        if (currentFileName == null || currentFileName.trim().isEmpty()) {
            currentFileName = "retinal_photograph.jpg";
        }
    }

    private void initializeViews() {
        btnBack = findViewById(R.id.btnBack);
        btnHelp = findViewById(R.id.btnHelp);
        tvSessionIdBadge = findViewById(R.id.tvSessionIdBadge);
        tvSessionIdBadge.setText("SESSION: " + currentSessionId);

        // Stepper
        ivStage4Icon = findViewById(R.id.ivStage4Icon);
        tvStage4Status = findViewById(R.id.tvStage4Status);
        ivStage5Icon = findViewById(R.id.ivStage5Icon);
        tvStage5Status = findViewById(R.id.tvStage5Status);
        tvStage3Status = findViewById(R.id.tvStage3Status);
        tvPipelineStageIndicator = findViewById(R.id.tvPipelineStageIndicator);

        if (drSeverityGrade != null) {
            tvStage3Status.setText(drSeverityGrade.getShortCategoryName());
        }

        // Mode Pills
        btnModeOverlay = findViewById(R.id.btnModeOverlay);
        btnModeSideBySide = findViewById(R.id.btnModeSideBySide);
        btnModeBeforeAfter = findViewById(R.id.btnModeBeforeAfter);

        // Canvas & Floating Controls
        retinaOverlayView = findViewById(R.id.retinaOverlayView);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);
        btnResetZoom = findViewById(R.id.btnResetZoom);
        btnFullscreen = findViewById(R.id.btnFullscreen);

        layoutBeforeAfterBar = findViewById(R.id.layoutBeforeAfterBar);
        btnToggleBefore = findViewById(R.id.btnToggleBefore);
        btnToggleAfter = findViewById(R.id.btnToggleAfter);

        tvImageDimensions = findViewById(R.id.tvImageDimensions);
        tvImageResolutionTag = findViewById(R.id.tvImageResolutionTag);

        // Opacity
        sbOpacity = findViewById(R.id.sbOpacity);
        tvOpacityValue = findViewById(R.id.tvOpacityValue);
        btnResetOpacity = findViewById(R.id.btnResetOpacity);

        // Layer Quick Actions
        btnShowAllLayers = findViewById(R.id.btnShowAllLayers);
        btnHideAllLayers = findViewById(R.id.btnHideAllLayers);

        // 4 Layers
        layerCottonWool = findViewById(R.id.layerCottonWool);
        layerHardExudates = findViewById(R.id.layerHardExudates);
        layerHemorrhages = findViewById(R.id.layerHemorrhages);
        layerMicroaneurysms = findViewById(R.id.layerMicroaneurysms);

        // Summary Card
        badgeSummaryStatus = findViewById(R.id.badgeSummaryStatus);
        tvSummaryModelName = findViewById(R.id.tvSummaryModelName);
        tvSummaryModelVersion = findViewById(R.id.tvSummaryModelVersion);
        tvSummaryTimestamp = findViewById(R.id.tvSummaryTimestamp);

        // CTA
        btnGenerateReport = findViewById(R.id.btnGenerateReport);
        btnQuickGenerateReport = findViewById(R.id.btnQuickGenerateReport);

        // Stepper Interactive Rows
        rowStage1 = findViewById(R.id.rowStage1);
        rowStage2 = findViewById(R.id.rowStage2);
        rowStage3 = findViewById(R.id.rowStage3);
        rowStage4 = findViewById(R.id.rowStage4);
        rowStage5 = findViewById(R.id.rowStage5);

        // Initial setup for the 4 layer cards
        bindLayerCard(layerCottonWool, LesionCategory.COTTON_WOOL_SPOTS);
        bindLayerCard(layerHardExudates, LesionCategory.HARD_EXUDATES);
        bindLayerCard(layerHemorrhages, LesionCategory.HEMORRHAGES);
        bindLayerCard(layerMicroaneurysms, LesionCategory.MICROANEURYSMS);
    }

    private void loadPatientContext() {
        patientUuid = TokenManager.getUUID(this);
    }

    // ─── Interaction Listeners ────────────────────────────────────────────────

    private void setupInteractionListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnHelp.setOnClickListener(v -> showSegmentationHelpDialog());

        // Mode Switching
        btnModeOverlay.setOnClickListener(v -> selectDisplayMode(DisplayMode.OVERLAY));
        btnModeSideBySide.setOnClickListener(v -> selectDisplayMode(DisplayMode.SIDE_BY_SIDE));
        btnModeBeforeAfter.setOnClickListener(v -> selectDisplayMode(DisplayMode.BEFORE_AFTER));

        // Floating Zoom Controls
        btnZoomIn.setOnClickListener(v -> retinaOverlayView.zoomIn());
        btnZoomOut.setOnClickListener(v -> retinaOverlayView.zoomOut());
        btnResetZoom.setOnClickListener(v -> retinaOverlayView.resetZoom());
        btnFullscreen.setOnClickListener(v -> showFullscreenImageDialog());

        // Before/After Sub-Toggles
        btnToggleBefore.setOnClickListener(v -> {
            retinaOverlayView.setBeforeAfterShowOverlay(false);
            btnToggleBefore.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));
            btnToggleAfter.setTextColor(ContextCompat.getColor(this, R.color.netra_text_secondary));
        });
        btnToggleAfter.setOnClickListener(v -> {
            retinaOverlayView.setBeforeAfterShowOverlay(true);
            btnToggleAfter.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));
            btnToggleBefore.setTextColor(ContextCompat.getColor(this, R.color.netra_text_secondary));
        });

        // Opacity Slider
        sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int clamped = Math.max(10, Math.min(100, progress));
                tvOpacityValue.setText(clamped + "%");
                retinaOverlayView.setOverlayOpacity(clamped / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnResetOpacity.setOnClickListener(v -> {
            sbOpacity.setProgress(65);
            tvOpacityValue.setText("65%");
            retinaOverlayView.setOverlayOpacity(0.65f);
        });

        // Layer Quick Actions
        btnShowAllLayers.setOnClickListener(v -> toggleAllLayers(true));
        btnHideAllLayers.setOnClickListener(v -> toggleAllLayers(false));

        // Downstream CTA
        btnGenerateReport.setOnClickListener(v -> onGenerateReportPressed());
        if (btnQuickGenerateReport != null) {
            btnQuickGenerateReport.setOnClickListener(v -> onGenerateReportPressed());
        }

        // Stepper Navigation
        if (rowStage1 != null) {
            rowStage1.setOnClickListener(v -> finish());
        }
        if (rowStage2 != null) {
            rowStage2.setOnClickListener(v -> finish());
        }
        if (rowStage3 != null) {
            rowStage3.setOnClickListener(v -> finish());
        }
        if (rowStage4 != null) {
            rowStage4.setOnClickListener(v -> Toast.makeText(this, "You are currently on Stage 04 (Retinal Lesion Segmentation)", Toast.LENGTH_SHORT).show());
        }
        if (rowStage5 != null) {
            rowStage5.setOnClickListener(v -> onGenerateReportPressed());
        }
    }

    private void selectDisplayMode(DisplayMode mode) {
        retinaOverlayView.setDisplayMode(mode);

        btnModeOverlay.setBackgroundResource(mode == DisplayMode.OVERLAY ? R.drawable.bg_mode_pill_selected : R.drawable.bg_mode_pill_unselected);
        btnModeOverlay.setTextColor(ContextCompat.getColor(this, mode == DisplayMode.OVERLAY ? android.R.color.white : R.color.netra_text_primary));

        btnModeSideBySide.setBackgroundResource(mode == DisplayMode.SIDE_BY_SIDE ? R.drawable.bg_mode_pill_selected : R.drawable.bg_mode_pill_unselected);
        btnModeSideBySide.setTextColor(ContextCompat.getColor(this, mode == DisplayMode.SIDE_BY_SIDE ? android.R.color.white : R.color.netra_text_primary));

        btnModeBeforeAfter.setBackgroundResource(mode == DisplayMode.BEFORE_AFTER ? R.drawable.bg_mode_pill_selected : R.drawable.bg_mode_pill_unselected);
        btnModeBeforeAfter.setTextColor(ContextCompat.getColor(this, mode == DisplayMode.BEFORE_AFTER ? android.R.color.white : R.color.netra_text_primary));

        layoutBeforeAfterBar.setVisibility(mode == DisplayMode.BEFORE_AFTER ? View.VISIBLE : View.GONE);
    }

    // ─── Layer Card Binding ───────────────────────────────────────────────────

    private void bindLayerCard(View cardView, LesionCategory category) {
        View viewCategoryDot = cardView.findViewById(R.id.viewCategoryDot);
        TextView tvCategoryTitle = cardView.findViewById(R.id.tvCategoryTitle);
        TextView badgeCategoryCode = cardView.findViewById(R.id.badgeCategoryCode);
        TextView tvCategoryStatus = cardView.findViewById(R.id.tvCategoryStatus);
        TextView tvCategoryDescription = cardView.findViewById(R.id.tvCategoryDescription);
        TextView tvLayerMetrics = cardView.findViewById(R.id.tvLayerMetrics);
        TextView btnInspectLayer = cardView.findViewById(R.id.btnInspectLayer);
        SwitchMaterial switchLayerVisibility = cardView.findViewById(R.id.switchLayerVisibility);

        viewCategoryDot.setBackgroundResource(category.getIndicatorDrawableResId());
        tvCategoryTitle.setText(category.getDisplayName());
        badgeCategoryCode.setText(category.getShortCode());
        badgeCategoryCode.setTextColor(ContextCompat.getColor(this, category.getColorResId()));
        badgeCategoryCode.setBackgroundResource(R.drawable.bg_pill_status_teal);

        tvCategoryDescription.setText(category.getClinicalDescription());
        tvCategoryStatus.setText("Status: Pending Analysis");
        tvLayerMetrics.setText("Coverage: Not available");

        switchLayerVisibility.setOnCheckedChangeListener((btn, isChecked) -> {
            if (currentResult != null) {
                LesionMaskData mask = currentResult.getMask(category);
                if (mask != null) {
                    mask.setVisible(isChecked);
                    retinaOverlayView.invalidate();
                }
            }
        });

        btnInspectLayer.setOnClickListener(v -> showLayerInspectionDialog(category));
    }

    private void toggleAllLayers(boolean visible) {
        setSwitchChecked(layerCottonWool, visible);
        setSwitchChecked(layerHardExudates, visible);
        setSwitchChecked(layerHemorrhages, visible);
        setSwitchChecked(layerMicroaneurysms, visible);

        if (currentResult != null) {
            for (LesionMaskData mask : currentResult.getAllMasks()) {
                mask.setVisible(visible);
            }
            retinaOverlayView.invalidate();
        }
    }

    private void setSwitchChecked(View cardView, boolean checked) {
        SwitchMaterial sw = cardView.findViewById(R.id.switchLayerVisibility);
        if (sw != null) {
            sw.setChecked(checked);
        }
    }

    // ─── Image Loading ────────────────────────────────────────────────────────

    private void loadRetinalImage() {
        if (currentImagePath == null || !(new File(currentImagePath).exists())) {
            tvImageDimensions.setText("No fundus image file accessible.");
            tvImageResolutionTag.setText("File Missing");
            return;
        }

        new Thread(() -> {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(currentImagePath, opts);

                if (currentImageWidth <= 0) currentImageWidth = opts.outWidth;
                if (currentImageHeight <= 0) currentImageHeight = opts.outHeight;

                // Decode with sensible sample size to avoid OOM while preserving quality
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, 2048, 2048);
                Bitmap decoded = BitmapFactory.decodeFile(currentImagePath, opts);

                mainHandler.post(() -> {
                    if (decoded != null) {
                        baseRetinaBitmap = decoded;
                        retinaOverlayView.setBaseBitmap(decoded);

                        tvImageDimensions.setText(String.format(Locale.getDefault(), "Dimensions: %d × %d px • %s",
                                currentImageWidth, currentImageHeight, formatFileSize(currentFileSizeBytes)));
                        tvImageResolutionTag.setText("Aspect Ratio Preserved • fitCenter");
                    } else {
                        tvImageDimensions.setText("Failed to decode retinal fundus photograph.");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> tvImageDimensions.setText("Image load error: " + e.getMessage()));
            }
        }).start();
    }

    private int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    // ─── Model Execution ──────────────────────────────────────────────────────

    private void executeSegmentationPipeline() {
        RetinaSegmentationRepository.getInstance().segmentLesions(
                this,
                documentId,
                currentImagePath,
                patientUuid,
                drSeverityGrade,
                new RetinaSegmentationRepository.RetinaSegmentationCallback() {
                    @Override
                    public void onProgress(String statusMessage) {
                        badgeSummaryStatus.setText("Analyzing...");
                    }

                    @Override
                    public void onResult(RetinaSegmentationResult result) {
                        currentResult = result;
                        applySegmentationResult(result);
                    }

                    @Override
                    public void onError(String errorMessage, Throwable error) {
                        badgeSummaryStatus.setText("Failed");
                        badgeSummaryStatus.setBackgroundResource(R.drawable.bg_pill_status_poor);
                        badgeSummaryStatus.setTextColor(ContextCompat.getColor(RetinaLesionSegmentationActivity.this, R.color.netra_critical));
                    }
                }
        );
    }

    private void applySegmentationResult(@NonNull RetinaSegmentationResult result) {
        tvSummaryModelName.setText(result.getModelName());
        tvSummaryModelVersion.setText(result.getModelVersion());
        tvSummaryTimestamp.setText(new SimpleDateFormat("MMM dd, yyyy • HH:mm z", Locale.getDefault()).format(new Date(result.getInferenceTimestamp())));

        SegmentationState state = result.getSegmentationState();

        if (state == SegmentationState.SERVICE_UNAVAILABLE) {
            badgeSummaryStatus.setText("Service Pending");
            badgeSummaryStatus.setBackgroundResource(R.drawable.bg_pill_status_not_assessed);
            badgeSummaryStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_deep_navy));

            ivStage4Icon.setImageResource(R.drawable.ic_stage_in_progress);
            tvStage4Status.setText("Pending Gate");

            updateLayerCardStatus(layerCottonWool, "Status: Service Pending Deployment", "Coverage: Not available");
            updateLayerCardStatus(layerHardExudates, "Status: Service Pending Deployment", "Coverage: Not available");
            updateLayerCardStatus(layerHemorrhages, "Status: Service Pending Deployment", "Coverage: Not available");
            updateLayerCardStatus(layerMicroaneurysms, "Status: Service Pending Deployment", "Coverage: Not available");

        } else if (state == SegmentationState.SUCCESS) {
            badgeSummaryStatus.setText("Completed");
            badgeSummaryStatus.setBackgroundResource(R.drawable.bg_pill_status_good);
            badgeSummaryStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_success));

            ivStage4Icon.setImageResource(R.drawable.ic_stage_completed);
            tvStage4Status.setText("Done");

            if (ivStage5Icon != null) {
                ivStage5Icon.setImageResource(R.drawable.ic_stage_in_progress);
                ivStage5Icon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));
            }
            if (tvStage5Status != null) {
                tvStage5Status.setText("Ready • View");
                tvStage5Status.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));
            }

            retinaOverlayView.setLesionMasks(result.getAllMasks());

            for (LesionMaskData mask : result.getAllMasks()) {
                View card = getCardForCategory(mask.getCategory());
                if (card != null) {
                    updateLayerCardStatus(card, "Status: " + mask.getStatusDescription(), "Coverage: " + mask.getFormattedPixelArea());
                }
            }
        } else if (state == SegmentationState.FAILED) {
            badgeSummaryStatus.setText("Failed");
            badgeSummaryStatus.setBackgroundResource(R.drawable.bg_pill_status_poor);
            badgeSummaryStatus.setTextColor(ContextCompat.getColor(this, R.color.netra_critical));

            ivStage4Icon.setImageResource(R.drawable.ic_stage_failed);
            tvStage4Status.setText("Failed");
        }
    }

    private View getCardForCategory(LesionCategory cat) {
        switch (cat) {
            case COTTON_WOOL_SPOTS: return layerCottonWool;
            case HARD_EXUDATES: return layerHardExudates;
            case HEMORRHAGES: return layerHemorrhages;
            case MICROANEURYSMS: return layerMicroaneurysms;
            default: return null;
        }
    }

    private void updateLayerCardStatus(View cardView, String status, String metrics) {
        TextView tvStatus = cardView.findViewById(R.id.tvCategoryStatus);
        TextView tvMetrics = cardView.findViewById(R.id.tvLayerMetrics);
        if (tvStatus != null) tvStatus.setText(status);
        if (tvMetrics != null) tvMetrics.setText(metrics);
    }

    // ─── Dialogs & Downstream ─────────────────────────────────────────────────

    private void showSegmentationHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Lesion Segmentation Architecture")
                .setMessage(
                        "NetraProof utilizes a U-Net + SE-ResNeXt50 convolutional architecture for retinal lesion spatial segmentation:\n\n" +
                        "• Supported Morphological Targets:\n" +
                        "  - Cotton Wool Spots (#2979FF Blue)\n" +
                        "  - Hard Exudates (#FBC02D Yellow)\n" +
                        "  - Hemorrhages (#E53935 Red)\n" +
                        "  - Microaneurysms (#E040FB Magenta)\n\n" +
                        "• Clinical Independence Notice:\n" +
                        "  Lesion segmentation is an independent computer vision pipeline. " +
                        "  Its visual masks do not explain or validate the DR severity grade produced by the separate EfficientNet-B0 ensemble model."
                )
                .setPositiveButton("Understood", null)
                .show();
    }

    private void showLayerInspectionDialog(LesionCategory category) {
        new AlertDialog.Builder(this)
                .setTitle(category.getDisplayName() + " (" + category.getShortCode() + ")")
                .setMessage(
                        "Clinical Definition:\n" + category.getClinicalDescription() + "\n\n" +
                        "Pathophysiology:\n" + category.getEducationalNotes() + "\n\n" +
                        "Visualization Color: " + category.getColorHex() + " (Distinction convention only, non-severity-indicative).\n\n" +
                        "Deployment Note: When the backend segmentation model is active, detailed spatial bounding regions and calibrated pixel densities appear here."
                )
                .setPositiveButton("Close", null)
                .show();
    }

    private void showFullscreenImageDialog() {
        if (currentImagePath == null || !(new File(currentImagePath).exists())) {
            Toast.makeText(this, "No image available to display.", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_retina_image_viewer, null);
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        ImageView ivFull = dialogView.findViewById(R.id.ivFullRetina);
        TextView tvTitle = dialogView.findViewById(R.id.tvViewerTitle);
        TextView tvMeta  = dialogView.findViewById(R.id.tvViewerMeta);
        View btnClose    = dialogView.findViewById(R.id.btnViewerClose);
        View btnDone     = dialogView.findViewById(R.id.btnViewerDone);

        tvTitle.setText("Retinal Lesion Visual Inspection");
        tvMeta.setText(currentImageWidth + " × " + currentImageHeight + " px • " + formatFileSize(currentFileSizeBytes));

        if (baseRetinaBitmap != null && !baseRetinaBitmap.isRecycled()) {
            ivFull.setImageBitmap(baseRetinaBitmap);
        } else {
            ivFull.setImageURI(Uri.fromFile(new File(currentImagePath)));
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnDone.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void onGenerateReportPressed() {
        if (currentResult == null || !currentResult.isReportUnlocked()) {
            Toast.makeText(this, "Screening session reference required to compile report.", Toast.LENGTH_SHORT).show();
            return;
        }

        launchReportActivity();
    }

    private void launchReportActivity() {
        Intent intent = new Intent(this, RetinaScreeningReportActivity.class);
        intent.putExtra(RetinaScreeningReportActivity.EXTRA_DOCUMENT_ID, documentId);
        intent.putExtra(RetinaScreeningReportActivity.EXTRA_IMAGE_PATH, currentImagePath);
        intent.putExtra(RetinaScreeningReportActivity.EXTRA_FILE_NAME, currentFileName);
        intent.putExtra(RetinaScreeningReportActivity.EXTRA_IMAGE_WIDTH, currentImageWidth);
        intent.putExtra(RetinaScreeningReportActivity.EXTRA_IMAGE_HEIGHT, currentImageHeight);
        intent.putExtra(RetinaScreeningReportActivity.EXTRA_IMAGE_SIZE, currentFileSizeBytes);
        intent.putExtra(RetinaScreeningReportActivity.EXTRA_SESSION_ID, currentSessionId);
        if (drSeverityGrade != null) {
            intent.putExtra(RetinaScreeningReportActivity.EXTRA_PREDICTED_GRADE, drSeverityGrade.name());
        }
        if (currentResult != null && currentResult.getSegmentationState() != null) {
            intent.putExtra(RetinaScreeningReportActivity.EXTRA_SEGMENTATION_STATE, currentResult.getSegmentationState().name());
        }
        startActivity(intent);
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "--";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}

