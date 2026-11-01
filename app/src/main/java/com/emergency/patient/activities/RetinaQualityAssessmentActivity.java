package com.emergency.patient.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.emergency.patient.BuildConfig;
import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.HealthDocumentEntity;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.models.RetinaQualityAssessmentResult;
import com.emergency.patient.models.RetinaQualityAssessmentResult.OverallState;
import com.emergency.patient.models.RetinaQualityDimension;
import com.emergency.patient.models.RetinaQualityDimension.Status;
import com.emergency.patient.network.RetinaQualityRepository;
import com.emergency.patient.security.TokenManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RetinaQualityAssessmentActivity — NetraProof Retinal Image Quality Assessment (Page 03)
 *
 * Serves as the authoritative quality-control gate in the NetraProof screening pipeline,
 * situated between retinal image intake (Page 02) and downstream AI models (DR Severity
 * Grading & Lesion Segmentation).
 *
 * Distinguishes strictly between:
 *  1. File Validation: On-device file integrity and header decoding.
 *  2. Image Quality Assessment: Optical evaluation (sharpness, illumination, artifacts).
 *  3. Clinical Suitability: Gate condition required to unlock downstream screening.
 */
public class RetinaQualityAssessmentActivity extends AppCompatActivity {

    // ─── Intent Extra Keys ───────────────────────────────────────────────────
    public static final String EXTRA_DOCUMENT_ID   = "extra_document_id";
    public static final String EXTRA_IMAGE_PATH    = "extra_image_path";
    public static final String EXTRA_FILE_NAME     = "extra_file_name";
    public static final String EXTRA_IMAGE_WIDTH   = "extra_image_width";
    public static final String EXTRA_IMAGE_HEIGHT  = "extra_image_height";
    public static final String EXTRA_IMAGE_SIZE    = "extra_image_size";
    public static final String EXTRA_SESSION_ID    = "extra_session_id";

    // ─── View References ──────────────────────────────────────────────────────

    // Header
    private View btnBack, btnQualityHelp;
    private TextView tvSessionIdBadge;

    // Stepper
    private ImageView ivStage1Icon, ivStage2Icon, ivStage3Icon, ivStage4Icon, ivStage5Icon;
    private TextView tvStage1Status, tvStage2Status, tvStage2Subtitle, tvStage3Status, tvStage4Status, tvStage5Status;
    private TextView tvPipelineGateStatus;
    private View rowStage1, rowStage2, rowStage3, rowStage4, rowStage5;
    private View btnQuickContinueToStage3;

    // Preview & Metadata
    private LinearLayout cardImagePreviewContainer, layoutEmptyImageState;
    private ImageView ivRetinaPreview;
    private TextView tvImageResolutionOverlay;
    private TextView tvFileName, tvFileSize, tvDimensionsFormat, tvIntakeTimestamp;
    private LinearLayout badgeFileValidation;
    private TextView tvFileValidationText;
    private View btnZoomImage, btnViewOriginal, btnReplaceImage, btnRemoveImage;
    private View btnReturnToUploadEmpty;

    // Overall Decision Card
    private LinearLayout cardOverallResult;
    private ImageView ivOverallStatusIcon;
    private TextView tvOverallResultTitle, tvOverallResultTimestamp, badgeOverallDecision, tvOverallResultDesc;
    private LinearLayout layoutAssessmentLoading;
    private TextView tvLoadingStatusText;
    private LinearLayout layoutDetectedIssuesList;
    private TextView tvDetectedIssuesBody;

    // Dimensions Panel & Controls
    private View btnRefreshAnalysis;
    private View itemDimSharpness, itemDimIllumination, itemDimContrast;
    private View itemDimFieldVisibility, itemDimBlur, itemDimReflections, itemDimObstruction;

    // Recovery & Retake
    private View btnRetakePhotograph, btnUploadAnother;

    // Primary Downstream CTAs
    private View btnContinueToScreeningActive, btnContinueToScreeningLocked;
    private TextView tvLockedCtaText;
    private View btnRetryAssessmentAction;

    // ─── State ────────────────────────────────────────────────────────────────
    private int documentId = -1;
    private String currentImagePath;
    private String currentFileName;
    private int currentImageWidth = 0;
    private int currentImageHeight = 0;
    private long currentFileSizeBytes = 0;
    private String currentSessionId;
    private String patientUuid;

    private boolean isAssessing = false;
    private RetinaQualityAssessmentResult currentResult;
    private File currentTempCameraFile;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── Activity Result Launchers ────────────────────────────────────────────

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchCamera();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Camera Permission Required")
                            .setMessage("NetraProof requires camera access to capture retinal fundus images through ophthalmic lens attachments.")
                            .setPositiveButton("Choose from Gallery", (d, w) -> launchGallery())
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    handleNewImageSelected(result.getData().getData());
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && currentTempCameraFile != null && currentTempCameraFile.exists()) {
                    Uri photoUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", currentTempCameraFile);
                    handleNewImageSelected(photoUri);
                }
            });

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retina_quality_assessment);

        extractIntentExtras();
        bindViews();
        setupClickListeners();
        loadPatientContext();
        initializeImageAndRunAssessment();
        com.emergency.patient.utils.NetraProfileHeaderHelper.setup(this);
    }

    // ─── Intent Extra Extraction ──────────────────────────────────────────────

    private void extractIntentExtras() {
        Intent intent = getIntent();
        if (intent != null) {
            documentId = intent.getIntExtra(EXTRA_DOCUMENT_ID, -1);
            currentImagePath = intent.getStringExtra(EXTRA_IMAGE_PATH);
            currentFileName = intent.getStringExtra(EXTRA_FILE_NAME);
            currentImageWidth = intent.getIntExtra(EXTRA_IMAGE_WIDTH, 0);
            currentImageHeight = intent.getIntExtra(EXTRA_IMAGE_HEIGHT, 0);
            currentFileSizeBytes = intent.getLongExtra(EXTRA_IMAGE_SIZE, 0);
            currentSessionId = intent.getStringExtra(EXTRA_SESSION_ID);
        }

        if (currentSessionId == null || currentSessionId.trim().isEmpty()) {
            currentSessionId = "NP-SCR-" + (documentId > 0 ? String.format(Locale.getDefault(), "%05d", documentId) : System.currentTimeMillis() % 100000);
        }
    }

    // ─── View Binding ─────────────────────────────────────────────────────────

    private void bindViews() {
        // Header
        btnBack          = findViewById(R.id.btnBack);
        btnQualityHelp   = findViewById(R.id.btnQualityHelp);
        tvSessionIdBadge = findViewById(R.id.tvSessionIdBadge);

        // Stepper
        ivStage1Icon          = findViewById(R.id.ivStage1Icon);
        tvStage1Status        = findViewById(R.id.tvStage1Status);
        ivStage2Icon          = findViewById(R.id.ivStage2Icon);
        tvStage2Status        = findViewById(R.id.tvStage2Status);
        tvStage2Subtitle      = findViewById(R.id.tvStage2Subtitle);
        ivStage3Icon          = findViewById(R.id.ivStage3Icon);
        tvStage3Status        = findViewById(R.id.tvStage3Status);
        ivStage4Icon          = findViewById(R.id.ivStage4Icon);
        tvStage4Status        = findViewById(R.id.tvStage4Status);
        ivStage5Icon          = findViewById(R.id.ivStage5Icon);
        tvStage5Status        = findViewById(R.id.tvStage5Status);
        tvPipelineGateStatus  = findViewById(R.id.tvPipelineGateStatus);
        rowStage1             = findViewById(R.id.rowStage1);
        rowStage2             = findViewById(R.id.rowStage2);
        rowStage3             = findViewById(R.id.rowStage3);
        rowStage4             = findViewById(R.id.rowStage4);
        rowStage5             = findViewById(R.id.rowStage5);
        btnQuickContinueToStage3 = findViewById(R.id.btnQuickContinueToStage3);

        // Image Preview & Metadata
        cardImagePreviewContainer = findViewById(R.id.cardImagePreviewContainer);
        layoutEmptyImageState     = findViewById(R.id.layoutEmptyImageState);
        ivRetinaPreview           = findViewById(R.id.ivRetinaPreview);
        tvImageResolutionOverlay  = findViewById(R.id.tvImageResolutionOverlay);
        tvFileName                = findViewById(R.id.tvFileName);
        tvFileSize                = findViewById(R.id.tvFileSize);
        tvDimensionsFormat        = findViewById(R.id.tvDimensionsFormat);
        tvIntakeTimestamp         = findViewById(R.id.tvIntakeTimestamp);
        badgeFileValidation       = findViewById(R.id.badgeFileValidation);
        tvFileValidationText      = findViewById(R.id.tvFileValidationText);

        btnZoomImage              = findViewById(R.id.btnZoomImage);
        btnViewOriginal           = findViewById(R.id.btnViewOriginal);
        btnReplaceImage           = findViewById(R.id.btnReplaceImage);
        btnRemoveImage            = findViewById(R.id.btnRemoveImage);
        btnReturnToUploadEmpty    = findViewById(R.id.btnReturnToUploadEmpty);

        // Overall Decision Card
        cardOverallResult         = findViewById(R.id.cardOverallResult);
        ivOverallStatusIcon       = findViewById(R.id.ivOverallStatusIcon);
        tvOverallResultTitle      = findViewById(R.id.tvOverallResultTitle);
        tvOverallResultTimestamp  = findViewById(R.id.tvOverallResultTimestamp);
        badgeOverallDecision      = findViewById(R.id.badgeOverallDecision);
        tvOverallResultDesc       = findViewById(R.id.tvOverallResultDesc);
        layoutAssessmentLoading   = findViewById(R.id.layoutAssessmentLoading);
        tvLoadingStatusText       = findViewById(R.id.tvLoadingStatusText);
        layoutDetectedIssuesList  = findViewById(R.id.layoutDetectedIssuesList);
        tvDetectedIssuesBody      = findViewById(R.id.tvDetectedIssuesBody);

        // Quality Dimensions
        btnRefreshAnalysis        = findViewById(R.id.btnRefreshAnalysis);
        itemDimSharpness          = findViewById(R.id.itemDimSharpness);
        itemDimIllumination       = findViewById(R.id.itemDimIllumination);
        itemDimContrast           = findViewById(R.id.itemDimContrast);
        itemDimFieldVisibility    = findViewById(R.id.itemDimFieldVisibility);
        itemDimBlur               = findViewById(R.id.itemDimBlur);
        itemDimReflections        = findViewById(R.id.itemDimReflections);
        itemDimObstruction        = findViewById(R.id.itemDimObstruction);

        // Recovery
        btnRetakePhotograph       = findViewById(R.id.btnRetakePhotograph);
        btnUploadAnother          = findViewById(R.id.btnUploadAnother);

        // CTAs
        btnContinueToScreeningActive = findViewById(R.id.btnContinueToScreeningActive);
        btnContinueToScreeningLocked = findViewById(R.id.btnContinueToScreeningLocked);
        tvLockedCtaText              = findViewById(R.id.tvLockedCtaText);
        btnRetryAssessmentAction     = findViewById(R.id.btnRetryAssessmentAction);

        tvSessionIdBadge.setText("SESSION: " + currentSessionId);
    }

    // ─── Setup Click Listeners ────────────────────────────────────────────────

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnQualityHelp.setOnClickListener(v -> showQualityHelpDialog());

        btnZoomImage.setOnClickListener(v -> showImageViewerDialog(true));

        btnViewOriginal.setOnClickListener(v -> showImageViewerDialog(false));

        btnReplaceImage.setOnClickListener(v -> showReplaceImageDialog());

        btnRemoveImage.setOnClickListener(v -> showRemoveImageConfirmation());

        btnReturnToUploadEmpty.setOnClickListener(v -> finish());

        btnRetakePhotograph.setOnClickListener(v -> checkCameraPermissionAndLaunch());

        btnUploadAnother.setOnClickListener(v -> launchGallery());

        btnRefreshAnalysis.setOnClickListener(v -> {
            if (isAssessing) {
                Toast.makeText(this, "Assessment in progress. Please wait...", Toast.LENGTH_SHORT).show();
            } else {
                runQualityAssessment();
            }
        });

        btnRetryAssessmentAction.setOnClickListener(v -> {
            if (isAssessing) {
                Toast.makeText(this, "Assessment in progress. Please wait...", Toast.LENGTH_SHORT).show();
            } else {
                runQualityAssessment();
            }
        });

        btnContinueToScreeningActive.setOnClickListener(v -> onContinueToScreeningPressed());

        if (btnQuickContinueToStage3 != null) {
            btnQuickContinueToStage3.setOnClickListener(v -> onContinueToScreeningPressed());
        }

        if (rowStage1 != null) {
            rowStage1.setOnClickListener(v -> finish());
        }

        if (rowStage2 != null) {
            rowStage2.setOnClickListener(v -> {
                if (!isAssessing) runQualityAssessment();
            });
        }

        if (rowStage3 != null) {
            rowStage3.setOnClickListener(v -> {
                if (currentResult != null && currentResult.getOverallState() == OverallState.PASSED) {
                    onContinueToScreeningPressed();
                } else {
                    Toast.makeText(this, "Stage 03 is locked until Optical Quality Assessment passes.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (rowStage4 != null) {
            rowStage4.setOnClickListener(v -> {
                Toast.makeText(this, "Stage 04 unlocks automatically after DR prediction is complete.", Toast.LENGTH_SHORT).show();
            });
        }

        if (rowStage5 != null) {
            rowStage5.setOnClickListener(v -> {
                Toast.makeText(this, "Stage 05 Screening Report will be available once screening finishes.", Toast.LENGTH_SHORT).show();
            });
        }

        btnContinueToScreeningLocked.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Optical Quality Gate Required")
                    .setMessage(
                            "DR Severity Grading and Lesion Segmentation are locked.\n\n" +
                            "Clinical Safety Rule: Automated model grading requires a verified retinal photograph without severe blur, glare, or darkness.\n\n" +
                            "Please retake the photo or select another clinical fundus image."
                    )
                    .setPositiveButton("Choose from Gallery", (d, w) -> launchGallery())
                    .setNegativeButton("Retake with Camera", (d, w) -> checkCameraPermissionAndLaunch())
                    .setNeutralButton("Close", null)
                    .show();
        });
    }

    // ─── Patient Context ──────────────────────────────────────────────────────

    private void loadPatientContext() {
        patientUuid = TokenManager.getUUID(this);
    }

    // ─── Image & Assessment Execution ─────────────────────────────────────────

    private void initializeImageAndRunAssessment() {
        if (currentImagePath == null || currentImagePath.trim().isEmpty() || !(new File(currentImagePath).exists())) {
            showEmptyState();
            return;
        }

        displayImagePreview();
        runQualityAssessment();
    }

    private void displayImagePreview() {
        cardImagePreviewContainer.setVisibility(View.VISIBLE);
        layoutEmptyImageState.setVisibility(View.GONE);

        File file = new File(currentImagePath);
        if (currentFileName == null || currentFileName.isEmpty()) {
            currentFileName = file.getName();
        }
        if (currentFileSizeBytes <= 0) {
            currentFileSizeBytes = file.length();
        }

        // Measure dimensions if not passed
        if (currentImageWidth <= 0 || currentImageHeight <= 0) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(currentImagePath, opts);
            currentImageWidth = opts.outWidth;
            currentImageHeight = opts.outHeight;
        }

        // Display bitmap in contained view safely with downsampling to avoid UI freeze / OOM
        try {
            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = calculateInSampleSize(currentImageWidth, currentImageHeight, 1024, 1024);
            decodeOpts.inJustDecodeBounds = false;
            Bitmap bmp = BitmapFactory.decodeFile(currentImagePath, decodeOpts);
            if (bmp != null) {
                ivRetinaPreview.setImageBitmap(bmp);
            } else {
                ivRetinaPreview.setImageURI(Uri.fromFile(file));
            }
        } catch (Throwable t) {
            try {
                ivRetinaPreview.setImageURI(Uri.fromFile(file));
            } catch (Throwable ignored) {}
        }

        tvFileName.setText(currentFileName);
        tvFileSize.setText(formatFileSize(currentFileSizeBytes));
        tvDimensionsFormat.setText(currentImageWidth + " × " + currentImageHeight + " px (JPEG)");
        tvImageResolutionOverlay.setText(currentImageWidth + " × " + currentImageHeight + " px");

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        tvIntakeTimestamp.setText(sdf.format(new Date(file.lastModified() > 0 ? file.lastModified() : System.currentTimeMillis())));

        // File validation badge
        badgeFileValidation.setVisibility(View.VISIBLE);
        tvFileValidationText.setText("File Validated");
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

    private void showEmptyState() {
        cardImagePreviewContainer.setVisibility(View.GONE);
        layoutEmptyImageState.setVisibility(View.VISIBLE);
        badgeFileValidation.setVisibility(View.GONE);

        // Overall decision in empty state
        cardOverallResult.setBackgroundResource(R.drawable.bg_quality_card_failed);
        ivOverallStatusIcon.setImageResource(R.drawable.ic_stage_failed);
        ivOverallStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_critical));

        tvOverallResultTitle.setText("No Retinal Image Available");
        tvOverallResultTimestamp.setText("Session intake pending");
        badgeOverallDecision.setText("Missing File");
        badgeOverallDecision.setBackgroundResource(R.drawable.bg_pill_status_poor);
        badgeOverallDecision.setTextColor(ContextCompat.getColor(this, R.color.netra_critical));

        tvOverallResultDesc.setText("Cannot perform image quality assessment without an uploaded retinal photograph. Please return to Page 02 to capture or upload a fundus image.");

        // Lock CTA
        btnContinueToScreeningActive.setVisibility(View.GONE);
        btnContinueToScreeningLocked.setVisibility(View.VISIBLE);
        tvLockedCtaText.setText("Downstream AI Screening Locked (No Image)");

        // Set dimensions to Not Assessed
        bindDimensions(new RetinaQualityAssessmentResult().getDimensions());
    }

    private void runQualityAssessment() {
        if (currentImagePath == null || !(new File(currentImagePath).exists())) {
            showEmptyState();
            return;
        }

        if (isAssessing) return;
        isAssessing = true;

        // Transition to In-Progress State
        cardOverallResult.setBackgroundResource(R.drawable.bg_quality_card_pending);
        ivOverallStatusIcon.setImageResource(R.drawable.ic_stage_in_progress);
        ivOverallStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));

        tvOverallResultTitle.setText("Assessment In Progress");
        tvOverallResultTimestamp.setText("Evaluating optical parameters...");
        badgeOverallDecision.setText("Evaluating");
        badgeOverallDecision.setBackgroundResource(R.drawable.bg_pill_status_not_assessed);
        badgeOverallDecision.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

        tvOverallResultDesc.setText("Evaluating retinal sharpness, contrast, illumination uniformity, and centration against clinical quality thresholds.");

        layoutAssessmentLoading.setVisibility(View.VISIBLE);
        tvLoadingStatusText.setText("Validating retinal image file integrity...");
        layoutDetectedIssuesList.setVisibility(View.GONE);

        ivStage2Icon.setImageResource(R.drawable.ic_stage_in_progress);
        tvStage2Status.setText("In Progress");
        tvStage2Status.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

        // Call repository
        RetinaQualityRepository.getInstance().assessQuality(
                this,
                documentId,
                currentImagePath,
                patientUuid,
                new RetinaQualityRepository.QualityAssessmentCallback() {
                    @Override
                    public void onProgress(String statusMessage) {
                        mainHandler.post(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            tvLoadingStatusText.setText(statusMessage);
                        });
                    }

                    @Override
                    public void onResult(RetinaQualityAssessmentResult result) {
                        mainHandler.post(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            isAssessing = false;
                            layoutAssessmentLoading.setVisibility(View.GONE);
                            currentResult = result;
                            applyAssessmentResult(result);
                        });
                    }

                    @Override
                    public void onError(String errorMessage, Throwable error) {
                        mainHandler.post(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            isAssessing = false;
                            layoutAssessmentLoading.setVisibility(View.GONE);
                            applyFailureState(errorMessage);
                        });
                    }
                }
        );
    }

    private void applyAssessmentResult(RetinaQualityAssessmentResult result) {
        if (result == null) {
            applyFailureState("Assessment completed without returning a valid clinical record.");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a, dd MMM yyyy", Locale.getDefault());
        String timeStr = result.getAssessmentTimestamp() > 0 ? sdf.format(new Date(result.getAssessmentTimestamp())) : "Pending";

        tvOverallResultTitle.setText(result.getStateTitle());
        tvOverallResultDesc.setText(result.getStateDescription());
        tvOverallResultTimestamp.setText("Assessment timestamp: " + timeStr);

        OverallState state = result.getOverallState();

        if (state == OverallState.PASSED) {
            // State 1: Quality Passed (Active CTA, Green Card)
            cardOverallResult.setBackgroundResource(R.drawable.bg_quality_card_passed);
            ivOverallStatusIcon.setImageResource(R.drawable.ic_stage_completed);
            ivOverallStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_success));

            badgeOverallDecision.setText("PASSED");
            badgeOverallDecision.setBackgroundResource(R.drawable.bg_pill_status_good);
            badgeOverallDecision.setTextColor(ContextCompat.getColor(this, R.color.netra_success));

            layoutDetectedIssuesList.setVisibility(View.GONE);

            // Stepper
            ivStage2Icon.setImageResource(R.drawable.ic_stage_completed);
            tvStage2Status.setText("Completed");
            tvStage2Status.setTextColor(ContextCompat.getColor(this, R.color.netra_success));

            // Unlock downstream Stage 03 in Stepper
            ivStage3Icon.setImageResource(R.drawable.ic_stage_in_progress);
            ivStage3Icon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));
            tvStage3Status.setText("Ready • Tap to Start");
            tvStage3Status.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

            // Queue Stage 04 & 05
            ivStage4Icon.setImageResource(R.drawable.ic_stage_pending);
            ivStage4Icon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));
            tvStage4Status.setText("Queued (After DR)");
            tvStage4Status.setTextColor(ContextCompat.getColor(this, R.color.netra_text_secondary));

            ivStage5Icon.setImageResource(R.drawable.ic_stage_pending);
            ivStage5Icon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));
            tvStage5Status.setText("Queued (Final)");
            tvStage5Status.setTextColor(ContextCompat.getColor(this, R.color.netra_text_secondary));

            // Unlock downstream Action Buttons
            if (btnQuickContinueToStage3 != null) {
                btnQuickContinueToStage3.setVisibility(View.VISIBLE);
            }
            btnContinueToScreeningActive.setVisibility(View.VISIBLE);
            btnContinueToScreeningLocked.setVisibility(View.GONE);

        } else if (state == OverallState.NEEDS_IMPROVEMENT) {
            // State 2: Needs Improvement (Amber Card, Locked CTA)
            cardOverallResult.setBackgroundResource(R.drawable.bg_quality_card_warning);
            ivOverallStatusIcon.setImageResource(R.drawable.ic_alert_triangle_red);
            ivOverallStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_warning));

            badgeOverallDecision.setText("NEEDS IMPROVEMENT");
            badgeOverallDecision.setBackgroundResource(R.drawable.bg_pill_status_acceptable);
            badgeOverallDecision.setTextColor(ContextCompat.getColor(this, R.color.netra_warning));

            // Show detected issues
            showDetectedIssues(result.getDetectedIssues());

            // Stepper
            ivStage2Icon.setImageResource(R.drawable.ic_stage_failed);
            tvStage2Status.setText("Issues Found");
            tvStage2Status.setTextColor(ContextCompat.getColor(this, R.color.netra_warning));

            // Lock downstream
            btnContinueToScreeningActive.setVisibility(View.GONE);
            btnContinueToScreeningLocked.setVisibility(View.VISIBLE);
            tvLockedCtaText.setText("Downstream AI Locked: Retake / Replace Image Required");

        } else if (state == OverallState.SERVICE_UNAVAILABLE) {
            // State 3a: Service Pending Integration (Honest Medical Reporting)
            cardOverallResult.setBackgroundResource(R.drawable.bg_quality_card_pending);
            ivOverallStatusIcon.setImageResource(R.drawable.ic_info_circle);
            ivOverallStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));

            badgeOverallDecision.setText("Service Pending");
            badgeOverallDecision.setBackgroundResource(R.drawable.bg_pill_status_not_assessed);
            badgeOverallDecision.setTextColor(ContextCompat.getColor(this, R.color.netra_deep_navy));

            layoutDetectedIssuesList.setVisibility(View.GONE);

            // Stepper
            ivStage2Icon.setImageResource(R.drawable.ic_stage_in_progress);
            tvStage2Status.setText("Pending Gate");
            tvStage2Status.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

            // Lock downstream
            btnContinueToScreeningActive.setVisibility(View.GONE);
            btnContinueToScreeningLocked.setVisibility(View.VISIBLE);
            tvLockedCtaText.setText("Downstream AI Locked (Quality Gate Pending Integration)");

        } else {
            // State 3b: Failed / Error
            applyFailureState(result.getStateDescription());
        }

        // Bind 7 Quality Dimensions
        bindDimensions(result.getDimensions());
    }

    private void applyFailureState(String reason) {
        cardOverallResult.setBackgroundResource(R.drawable.bg_quality_card_failed);
        ivOverallStatusIcon.setImageResource(R.drawable.ic_stage_failed);
        ivOverallStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_critical));

        tvOverallResultTitle.setText("Unable to Assess Image");
        tvOverallResultTimestamp.setText("Assessment could not be completed");
        badgeOverallDecision.setText("Failed");
        badgeOverallDecision.setBackgroundResource(R.drawable.bg_pill_status_poor);
        badgeOverallDecision.setTextColor(ContextCompat.getColor(this, R.color.netra_critical));

        tvOverallResultDesc.setText(reason != null && !reason.isEmpty() ? reason : "An error occurred during optical quality assessment. Please retry or upload another photograph.");

        layoutDetectedIssuesList.setVisibility(View.GONE);

        ivStage2Icon.setImageResource(R.drawable.ic_stage_failed);
        tvStage2Status.setText("Failed");
        tvStage2Status.setTextColor(ContextCompat.getColor(this, R.color.netra_critical));

        btnContinueToScreeningActive.setVisibility(View.GONE);
        btnContinueToScreeningLocked.setVisibility(View.VISIBLE);
        tvLockedCtaText.setText("Downstream AI Screening Locked (Assessment Failed)");

        // Bind default dimensions
        bindDimensions(new RetinaQualityAssessmentResult().getDimensions());
    }

    private void showDetectedIssues(List<String> issues) {
        if (issues != null && !issues.isEmpty()) {
            layoutDetectedIssuesList.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < issues.size(); i++) {
                sb.append("• ").append(issues.get(i));
                if (i < issues.size() - 1) sb.append("\n");
            }
            tvDetectedIssuesBody.setText(sb.toString());
        } else {
            layoutDetectedIssuesList.setVisibility(View.GONE);
        }
    }

    // ─── Dimensions Binding ───────────────────────────────────────────────────

    private void bindDimensions(List<RetinaQualityDimension> dims) {
        if (dims == null || dims.size() < 7) return;

        bindDimensionRow(itemDimSharpness, dims.get(0));
        bindDimensionRow(itemDimIllumination, dims.get(1));
        bindDimensionRow(itemDimContrast, dims.get(2));
        bindDimensionRow(itemDimFieldVisibility, dims.get(3));
        bindDimensionRow(itemDimBlur, dims.get(4));
        bindDimensionRow(itemDimReflections, dims.get(5));
        bindDimensionRow(itemDimObstruction, dims.get(6));
    }

    private void bindDimensionRow(View rowView, RetinaQualityDimension dim) {
        if (rowView == null || dim == null) return;

        TextView tvName   = rowView.findViewById(R.id.tvDimensionName);
        TextView tvVal    = rowView.findViewById(R.id.tvDimensionValue);
        TextView tvBadge  = rowView.findViewById(R.id.tvDimensionStatusBadge);
        TextView tvNote   = rowView.findViewById(R.id.tvDimensionNote);

        tvName.setText(dim.getName());

        String val = dim.getScoreOrValue();
        tvVal.setText(val != null && !val.isEmpty() ? val : "--");

        Status status = dim.getStatus();
        if (status == Status.GOOD) {
            tvBadge.setText("Good");
            tvBadge.setBackgroundResource(R.drawable.bg_pill_status_good);
            tvBadge.setTextColor(ContextCompat.getColor(this, R.color.netra_success));
        } else if (status == Status.ACCEPTABLE) {
            tvBadge.setText("Acceptable");
            tvBadge.setBackgroundResource(R.drawable.bg_pill_status_acceptable);
            tvBadge.setTextColor(ContextCompat.getColor(this, R.color.netra_warning));
        } else if (status == Status.POOR) {
            tvBadge.setText("Poor");
            tvBadge.setBackgroundResource(R.drawable.bg_pill_status_poor);
            tvBadge.setTextColor(ContextCompat.getColor(this, R.color.netra_critical));
        } else {
            tvBadge.setText("Not Assessed");
            tvBadge.setBackgroundResource(R.drawable.bg_pill_status_not_assessed);
            tvBadge.setTextColor(ContextCompat.getColor(this, R.color.netra_text_secondary));
        }

        String note = dim.getIssueDescription();
        if (note != null && !note.isEmpty()) {
            tvNote.setText(note);
            tvNote.setTextColor(status == Status.POOR ? ContextCompat.getColor(this, R.color.netra_critical) : ContextCompat.getColor(this, R.color.netra_text_secondary));
        } else {
            tvNote.setText(dim.getTechnicalDescription());
            tvNote.setTextColor(ContextCompat.getColor(this, R.color.netra_text_secondary));
        }
    }

    // ─── Dialogs & Modals ─────────────────────────────────────────────────────

    private void showQualityHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Retinal Quality Gate Architecture")
                .setMessage(
                        "NetraProof enforces strict quality control before executing deep learning diagnostic pipelines:\n\n" +
                        "1. Sharpness & Focus: Evaluates vascular contrast across the macular arcades.\n\n" +
                        "2. Illumination & Brightness: Checks dynamic range to avoid under/over-exposure.\n\n" +
                        "3. Contrast: Verifies differentiation of hemorrhages and microaneurysms.\n\n" +
                        "4. Field Visibility: Ensures optic disc and fovea are fully captured within frame.\n\n" +
                        "5. Artifacts & Glare: Filters corneal reflex glare and motion defocus blur.\n\n" +
                        "Gate Policy: Stage 03 (DR Grading) and Stage 04 (Lesion Segmentation) remain locked until an image achieves an authoritative quality pass."
                )
                .setPositiveButton("Close", null)
                .show();
    }

    private void showImageViewerDialog(boolean isZoomMode) {
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

        tvTitle.setText(isZoomMode ? "Zoom Retinal Inspection" : "Original Retinal Photograph");
        tvMeta.setText(currentImageWidth + " × " + currentImageHeight + " px • " + formatFileSize(currentFileSizeBytes));

        try {
            BitmapFactory.Options viewerOpts = new BitmapFactory.Options();
            viewerOpts.inSampleSize = calculateInSampleSize(currentImageWidth, currentImageHeight, 2048, 2048);
            viewerOpts.inJustDecodeBounds = false;
            Bitmap bmp = BitmapFactory.decodeFile(currentImagePath, viewerOpts);
            if (bmp != null) {
                ivFull.setImageBitmap(bmp);
            } else {
                ivFull.setImageURI(Uri.fromFile(new File(currentImagePath)));
            }
        } catch (Throwable t) {
            try {
                ivFull.setImageURI(Uri.fromFile(new File(currentImagePath)));
            } catch (Throwable ignored) {}
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnDone.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showReplaceImageDialog() {
        String[] options = {"Choose from Medical Gallery", "Capture with Camera"};
        new AlertDialog.Builder(this)
                .setTitle("Replace Retinal Image")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) launchGallery();
                    else checkCameraPermissionAndLaunch();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRemoveImageConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Remove Retinal Photograph?")
                .setMessage("Removing the retinal photograph will reset your current screening draft. You will need to capture or upload a new image.")
                .setPositiveButton("Remove", (d, w) -> {
                    currentImagePath = null;
                    currentFileName = null;
                    currentImageWidth = 0;
                    currentImageHeight = 0;
                    currentFileSizeBytes = 0;
                    currentResult = null;
                    showEmptyState();
                })
                .setNegativeButton("Keep Image", null)
                .show();
    }

    // ─── Camera & Gallery Operations ──────────────────────────────────────────

    private void launchGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            File cacheDir = new File(getCacheDir(), "netra_captures");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            currentTempCameraFile = new File(cacheDir, "retina_recapture_" + System.currentTimeMillis() + ".jpg");

            Uri photoUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", currentTempCameraFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not launch camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleNewImageSelected(Uri uri) {
        if (uri == null) return;

        new Thread(() -> {
            try {
                File screeningsDir = new File(getFilesDir(), "netra_screenings");
                if (!screeningsDir.exists()) screeningsDir.mkdirs();

                String newFilename = "screening_replaced_" + System.currentTimeMillis() + ".jpg";
                File targetFile = new File(screeningsDir, newFilename);

                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(targetFile)) {
                    if (in != null) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    }
                }

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(targetFile.getAbsolutePath(), opts);

                if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                    mainHandler.post(() -> {
                        Toast.makeText(this, "The selected image could not be decoded. Please choose an uncorrupted JPG or PNG.", Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                // Update current state
                currentImagePath = targetFile.getAbsolutePath();
                currentFileName = targetFile.getName();
                currentImageWidth = opts.outWidth;
                currentImageHeight = opts.outHeight;
                currentFileSizeBytes = targetFile.length();

                // Invalidate previous quality result
                currentResult = null;

                // Update Room DB
                if (documentId > 0) {
                    AppDatabase db = AppDatabaseProvider.getInstance(this);
                    HealthDocumentEntity doc = db.healthDocumentDao().getDocumentById(documentId);
                    if (doc != null) {
                        doc.internalFilePath = currentImagePath;
                        doc.displayName = "Retina Screening Draft (Updated)";
                        doc.extractionStatus = "draft_quality_pending";
                        doc.uploadTimestamp = System.currentTimeMillis();
                        db.healthDocumentDao().insertDocument(doc);
                    }
                }

                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, "Retinal image updated. Running fresh quality evaluation...", Toast.LENGTH_SHORT).show();
                    displayImagePreview();
                    runQualityAssessment();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "Failed to update image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // ─── Primary CTA Action ───────────────────────────────────────────────────

    private void onContinueToScreeningPressed() {
        if (currentImagePath == null || !(new File(currentImagePath).exists())) {
            Toast.makeText(this, "Please upload or capture a retinal image first.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Directly proceed to Page 04 (DR Severity Prediction)
        Intent intent = new Intent(this, RetinaDrPredictionActivity.class);
        intent.putExtra(RetinaDrPredictionActivity.EXTRA_DOCUMENT_ID, documentId);
        intent.putExtra(RetinaDrPredictionActivity.EXTRA_IMAGE_PATH, currentImagePath);
        intent.putExtra(RetinaDrPredictionActivity.EXTRA_FILE_NAME, currentFileName);
        intent.putExtra(RetinaDrPredictionActivity.EXTRA_IMAGE_WIDTH, currentImageWidth);
        intent.putExtra(RetinaDrPredictionActivity.EXTRA_IMAGE_HEIGHT, currentImageHeight);
        intent.putExtra(RetinaDrPredictionActivity.EXTRA_IMAGE_SIZE, currentFileSizeBytes);
        intent.putExtra(RetinaDrPredictionActivity.EXTRA_SESSION_ID, currentSessionId);
        intent.putExtra(RetinaDrPredictionActivity.EXTRA_QUALITY_PASSED, true);
        startActivity(intent);
    }

    // ─── Helper Utilities ─────────────────────────────────────────────────────

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "--";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}

