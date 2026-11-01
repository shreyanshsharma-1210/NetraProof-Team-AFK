package com.emergency.patient.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.emergency.patient.R;
import com.emergency.patient.models.DrPredictionResult;
import com.emergency.patient.models.DrPredictionResult.PredictionState;
import com.emergency.patient.models.DrSeverityGrade;
import com.emergency.patient.network.DrPredictionRepository;
import com.emergency.patient.security.TokenManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RetinaDrPredictionActivity — NetraProof DR Severity Prediction & Results (Page 04)
 *
 * Implements Stage 03 of the NetraProof screening pipeline:
 *  - Predicts diabetic retinopathy severity using an on-device EfficientNet-B5 model with ordinal regression.
 *  - Validates that Stage 02 Optical Quality Assessment passed before executing.
 *  - Visualizes the 5 discrete ICDR severity tiers (Grade 0 through Grade 4).
 *  - Educational, non-diagnostic guidance and contextual recommended next steps.
 *  - Enforces independent gate control: keeps downstream lesion segmentation strictly locked
 *    until a valid grading prediction is confirmed.
 */
public class RetinaDrPredictionActivity extends AppCompatActivity {

    // ─── Intent Extra Keys ───────────────────────────────────────────────────
    public static final String EXTRA_DOCUMENT_ID    = "extra_document_id";
    public static final String EXTRA_IMAGE_PATH     = "extra_image_path";
    public static final String EXTRA_FILE_NAME      = "extra_file_name";
    public static final String EXTRA_IMAGE_WIDTH    = "extra_image_width";
    public static final String EXTRA_IMAGE_HEIGHT   = "extra_image_height";
    public static final String EXTRA_IMAGE_SIZE     = "extra_image_size";
    public static final String EXTRA_SESSION_ID     = "extra_session_id";
    public static final String EXTRA_QUALITY_PASSED = "extra_quality_passed";

    // ─── View References ──────────────────────────────────────────────────────

    // Header
    private View btnBack, btnDrGradingHelp;
    private TextView tvSessionIdBadge;

    // Stepper
    private ImageView ivStage1Icon, ivStage2Icon, ivStage3Icon, ivStage4Icon, ivStage5Icon;
    private TextView tvStage3Status, tvStage3Subtitle, tvStage4Status, tvStage5Status, tvPipelineStageIndicator;

    // Image Preview & Metadata
    private LinearLayout cardImagePreviewContainer, layoutEmptyImageState;
    private ImageView ivRetinaPreview;
    private TextView tvImageResolutionOverlay, tvFileName, tvDimensionsFormat, tvQualityGateStatus;
    private View btnZoomImage, btnViewOriginal, btnReturnToUpload;

    // Primary Result Card
    private LinearLayout cardGradingResult;
    private ImageView ivGradingStatusIcon;
    private TextView tvPredictedCategoryEyebrow, tvPredictedCategoryTitle, badgePredictedGrade;
    private TextView tvPredictedCategoryExplanation, tvModelVersion, tvInferenceTimestamp;
    private LinearLayout layoutPredictionLoading;
    private TextView tvLoadingStatusText;

    // 5-Grade Severity Scale Items
    private View itemGrade0, itemGrade1, itemGrade2, itemGrade3, itemGrade4;

    // Result Interpretation & Next Steps
    private LinearLayout cardResultInterpretation, cardNextSteps;
    private TextView tvInterpretationTitle, tvInterpretationBody, tvNextStepsBody;

    // Downstream Lesion Segmentation Gate
    private View btnContinueToSegmentationActive, btnContinueToSegmentationLocked;
    private TextView tvLockedCtaText;
    private View btnRetryPrediction;
    private View btnQuickContinueToStage4;

    // Stepper interactive rows
    private View rowStage1, rowStage2, rowStage3, rowStage4, rowStage5;

    // ─── State ────────────────────────────────────────────────────────────────
    private int documentId = -1;
    private String currentImagePath;
    private String currentFileName;
    private int currentImageWidth = 0;
    private int currentImageHeight = 0;
    private long currentFileSizeBytes = 0;
    private String currentSessionId;
    private boolean qualityGatePassed = true;
    private String patientUuid;

    private boolean isPredicting = false;
    private DrPredictionResult currentResult;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retina_dr_prediction);

        extractIntentExtras();
        bindViews();
        setupClickListeners();
        loadPatientContext();
        initializeSeverityScaleView();
        checkInputsAndExecutePrediction();
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
            qualityGatePassed = intent.getBooleanExtra(EXTRA_QUALITY_PASSED, true);
        }

        if (currentSessionId == null || currentSessionId.trim().isEmpty()) {
            currentSessionId = "NP-SCR-" + (documentId > 0 ? String.format(Locale.getDefault(), "%05d", documentId) : System.currentTimeMillis() % 100000);
        }
    }

    // ─── View Binding ─────────────────────────────────────────────────────────

    private void bindViews() {
        // Header
        btnBack            = findViewById(R.id.btnBack);
        btnDrGradingHelp   = findViewById(R.id.btnDrGradingHelp);
        tvSessionIdBadge   = findViewById(R.id.tvSessionIdBadge);

        // Stepper
        ivStage1Icon             = findViewById(R.id.ivStage1Icon);
        ivStage2Icon             = findViewById(R.id.ivStage2Icon);
        ivStage3Icon             = findViewById(R.id.ivStage3Icon);
        tvStage3Status           = findViewById(R.id.tvStage3Status);
        tvStage3Subtitle         = findViewById(R.id.tvStage3Subtitle);
        ivStage4Icon             = findViewById(R.id.ivStage4Icon);
        tvStage4Status           = findViewById(R.id.tvStage4Status);
        ivStage5Icon             = findViewById(R.id.ivStage5Icon);
        tvStage5Status           = findViewById(R.id.tvStage5Status);
        tvPipelineStageIndicator = findViewById(R.id.tvPipelineStageIndicator);

        // Image Preview & Metadata
        cardImagePreviewContainer = findViewById(R.id.cardImagePreviewContainer);
        layoutEmptyImageState     = findViewById(R.id.layoutEmptyImageState);
        ivRetinaPreview           = findViewById(R.id.ivRetinaPreview);
        tvImageResolutionOverlay  = findViewById(R.id.tvImageResolutionOverlay);
        tvFileName                = findViewById(R.id.tvFileName);
        tvDimensionsFormat        = findViewById(R.id.tvDimensionsFormat);
        tvQualityGateStatus       = findViewById(R.id.tvQualityGateStatus);
        btnZoomImage              = findViewById(R.id.btnZoomImage);
        btnViewOriginal           = findViewById(R.id.btnViewOriginal);
        btnReturnToUpload         = findViewById(R.id.btnReturnToUpload);

        // Result Card
        cardGradingResult              = findViewById(R.id.cardGradingResult);
        ivGradingStatusIcon            = findViewById(R.id.ivGradingStatusIcon);
        tvPredictedCategoryEyebrow     = findViewById(R.id.tvPredictedCategoryEyebrow);
        tvPredictedCategoryTitle       = findViewById(R.id.tvPredictedCategoryTitle);
        badgePredictedGrade            = findViewById(R.id.badgePredictedGrade);
        tvPredictedCategoryExplanation = findViewById(R.id.tvPredictedCategoryExplanation);
        layoutPredictionLoading        = findViewById(R.id.layoutPredictionLoading);
        tvLoadingStatusText            = findViewById(R.id.tvLoadingStatusText);
        tvModelVersion                 = findViewById(R.id.tvModelVersion);
        tvInferenceTimestamp           = findViewById(R.id.tvInferenceTimestamp);

        // 5-Grade Scale Items
        itemGrade0 = findViewById(R.id.itemGrade0);
        itemGrade1 = findViewById(R.id.itemGrade1);
        itemGrade2 = findViewById(R.id.itemGrade2);
        itemGrade3 = findViewById(R.id.itemGrade3);
        itemGrade4 = findViewById(R.id.itemGrade4);

        // Interpretation & Next Steps
        cardResultInterpretation = findViewById(R.id.cardResultInterpretation);
        tvInterpretationTitle    = findViewById(R.id.tvInterpretationTitle);
        tvInterpretationBody     = findViewById(R.id.tvInterpretationBody);
        cardNextSteps            = findViewById(R.id.cardNextSteps);
        tvNextStepsBody          = findViewById(R.id.tvNextStepsBody);

        // Downstream CTAs
        btnContinueToSegmentationActive = findViewById(R.id.btnContinueToSegmentationActive);
        btnContinueToSegmentationLocked = findViewById(R.id.btnContinueToSegmentationLocked);
        tvLockedCtaText                 = findViewById(R.id.tvLockedCtaText);
        btnRetryPrediction              = findViewById(R.id.btnRetryPrediction);
        btnQuickContinueToStage4        = findViewById(R.id.btnQuickContinueToStage4);

        // Stepper Interactive Rows
        rowStage1 = findViewById(R.id.rowStage1);
        rowStage2 = findViewById(R.id.rowStage2);
        rowStage3 = findViewById(R.id.rowStage3);
        rowStage4 = findViewById(R.id.rowStage4);
        rowStage5 = findViewById(R.id.rowStage5);

        tvSessionIdBadge.setText("SESSION: " + currentSessionId);
    }

    // ─── Setup Click Listeners ────────────────────────────────────────────────

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        if (btnQuickContinueToStage4 != null) {
            btnQuickContinueToStage4.setOnClickListener(v -> onContinueToSegmentationPressed());
        }

        if (rowStage1 != null) {
            rowStage1.setOnClickListener(v -> finish());
        }
        if (rowStage2 != null) {
            rowStage2.setOnClickListener(v -> finish());
        }
        if (rowStage3 != null) {
            rowStage3.setOnClickListener(v -> Toast.makeText(this, "You are currently on Stage 03 (DR Severity Prediction)", Toast.LENGTH_SHORT).show());
        }
        if (rowStage4 != null) {
            rowStage4.setOnClickListener(v -> {
                if (currentResult != null && currentResult.isDownstreamUnlocked()) {
                    launchSegmentationActivity();
                } else {
                    Toast.makeText(this, "Stage 04 unlocks automatically upon completion of DR prediction.", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (rowStage5 != null) {
            rowStage5.setOnClickListener(v -> {
                if (currentResult != null && currentResult.isDownstreamUnlocked()) {
                    Intent reportIntent = new Intent(this, RetinaScreeningReportActivity.class);
                    reportIntent.putExtra(RetinaScreeningReportActivity.EXTRA_DOCUMENT_ID, documentId);
                    reportIntent.putExtra(RetinaScreeningReportActivity.EXTRA_IMAGE_PATH, currentImagePath);
                    reportIntent.putExtra(RetinaScreeningReportActivity.EXTRA_FILE_NAME, currentFileName);
                    reportIntent.putExtra(RetinaScreeningReportActivity.EXTRA_IMAGE_WIDTH, currentImageWidth);
                    reportIntent.putExtra(RetinaScreeningReportActivity.EXTRA_IMAGE_HEIGHT, currentImageHeight);
                    reportIntent.putExtra(RetinaScreeningReportActivity.EXTRA_IMAGE_SIZE, currentFileSizeBytes);
                    reportIntent.putExtra(RetinaScreeningReportActivity.EXTRA_SESSION_ID, currentSessionId);
                    if (currentResult != null && currentResult.getPredictedGrade() != null) {
                        reportIntent.putExtra(RetinaScreeningReportActivity.EXTRA_PREDICTED_GRADE, currentResult.getPredictedGrade().name());
                    }
                    startActivity(reportIntent);
                } else {
                    Toast.makeText(this, "Screening report will be available once prediction completes.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnDrGradingHelp.setOnClickListener(v -> showDrGradingHelpDialog());

        btnZoomImage.setOnClickListener(v -> showImageViewerDialog(true));

        btnViewOriginal.setOnClickListener(v -> showImageViewerDialog(false));

        btnReturnToUpload.setOnClickListener(v -> finish());

        btnRetryPrediction.setOnClickListener(v -> {
            if (isPredicting) {
                Toast.makeText(this, "Prediction in progress. Please wait...", Toast.LENGTH_SHORT).show();
            } else {
                executePrediction();
            }
        });

        btnContinueToSegmentationActive.setOnClickListener(v -> onContinueToSegmentationPressed());

        btnContinueToSegmentationLocked.setOnClickListener(v -> {
            Toast.makeText(this, "DR severity grading inference in progress. Stage 04 unlocks automatically upon completion.", Toast.LENGTH_SHORT).show();
        });
    }

    // ─── Patient Context ──────────────────────────────────────────────────────

    private void loadPatientContext() {
        patientUuid = TokenManager.getUUID(this);
    }

    // ─── 5-Grade Scale Static Initializer ─────────────────────────────────────

    private void initializeSeverityScaleView() {
        populateGradeRow(itemGrade0, DrSeverityGrade.GRADE_0, false);
        populateGradeRow(itemGrade1, DrSeverityGrade.GRADE_1, false);
        populateGradeRow(itemGrade2, DrSeverityGrade.GRADE_2, false);
        populateGradeRow(itemGrade3, DrSeverityGrade.GRADE_3, false);
        populateGradeRow(itemGrade4, DrSeverityGrade.GRADE_4, false);
    }

    private void populateGradeRow(View rowView, DrSeverityGrade grade, boolean isSelected) {
        if (rowView == null || grade == null) return;

        View card = rowView.findViewById(R.id.cardSeverityItem);
        if (card == null) {
            card = rowView;
        }

        TextView tvBadge  = rowView.findViewById(R.id.tvSeverityGradeBadge);
        TextView tvTitle  = rowView.findViewById(R.id.tvSeverityCategoryTitle);
        TextView tvDesc   = rowView.findViewById(R.id.tvSeveritySummary);
        View badgePredict = rowView.findViewById(R.id.badgePredictedIndicator);

        if (tvBadge != null) tvBadge.setText(grade.getGradeBadgeText());
        if (tvTitle != null) tvTitle.setText(grade.getShortCategoryName());
        if (tvDesc != null) tvDesc.setText(grade.getTechnicalSummary());

        if (isSelected) {
            card.setBackgroundResource(R.drawable.bg_dr_grade_card_selected);
            if (tvBadge != null) {
                tvBadge.setBackgroundResource(grade.getBadgeBgResId());
                tvBadge.setTextColor(ContextCompat.getColor(this, grade.getColorResId()));
            }
            if (tvTitle != null) {
                tvTitle.setTextColor(ContextCompat.getColor(this, grade.getColorResId()));
            }
            if (badgePredict != null) badgePredict.setVisibility(View.VISIBLE);
        } else {
            card.setBackgroundResource(R.drawable.bg_dr_grade_card_unselected);
            if (tvBadge != null) {
                tvBadge.setBackgroundResource(R.drawable.bg_pill_status_not_assessed);
                tvBadge.setTextColor(ContextCompat.getColor(this, R.color.netra_text_secondary));
            }
            if (tvTitle != null) {
                tvTitle.setTextColor(ContextCompat.getColor(this, R.color.netra_text_primary));
            }
            if (badgePredict != null) badgePredict.setVisibility(View.GONE);
        }
    }

    // ─── Input Validation & Prediction Execution ──────────────────────────────

    private void checkInputsAndExecutePrediction() {
        if (currentImagePath == null || currentImagePath.trim().isEmpty() || !(new File(currentImagePath).exists())) {
            showEmptyState();
            return;
        }

        displayImagePreview();

        if (!qualityGatePassed) {
            applyQualityGateFailedState();
            return;
        }

        executePrediction();
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

        if (currentImageWidth <= 0 || currentImageHeight <= 0) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(currentImagePath, opts);
            currentImageWidth = opts.outWidth;
            currentImageHeight = opts.outHeight;
        }

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
        tvDimensionsFormat.setText(currentImageWidth + " × " + currentImageHeight + " px • " + formatFileSize(currentFileSizeBytes));
        tvImageResolutionOverlay.setText(currentImageWidth + " × " + currentImageHeight + " px");
        tvQualityGateStatus.setText("Quality Verified");
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

        cardGradingResult.setBackgroundResource(R.drawable.bg_quality_card_failed);
        ivGradingStatusIcon.setImageResource(R.drawable.ic_stage_failed);
        ivGradingStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_critical));

        tvPredictedCategoryTitle.setText("No Retinal Image Available");
        tvPredictedCategoryExplanation.setText("DR severity prediction requires a verified retinal photograph from Stage 01 and Stage 02.");
        badgePredictedGrade.setText("Missing File");
        badgePredictedGrade.setBackgroundResource(R.drawable.bg_pill_status_poor);
        badgePredictedGrade.setTextColor(ContextCompat.getColor(this, R.color.netra_critical));

        btnContinueToSegmentationActive.setVisibility(View.GONE);
        btnContinueToSegmentationLocked.setVisibility(View.VISIBLE);
        tvLockedCtaText.setText("Downstream Segmentation Locked (No Image)");
    }

    private void applyQualityGateFailedState() {
        cardGradingResult.setBackgroundResource(R.drawable.bg_quality_card_warning);
        ivGradingStatusIcon.setImageResource(R.drawable.ic_stage_failed);
        ivGradingStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_warning));

        tvPredictedCategoryTitle.setText("Quality Gate Required");
        tvPredictedCategoryExplanation.setText("Clinical Safety Rule: DR severity prediction is locked because the retinal image did not achieve a confirmed pass in Stage 02 Optical Quality Assessment.");
        badgePredictedGrade.setText("Gate Locked");
        badgePredictedGrade.setBackgroundResource(R.drawable.bg_pill_status_acceptable);
        badgePredictedGrade.setTextColor(ContextCompat.getColor(this, R.color.netra_warning));

        btnContinueToSegmentationActive.setVisibility(View.GONE);
        btnContinueToSegmentationLocked.setVisibility(View.VISIBLE);
        tvLockedCtaText.setText("Downstream Segmentation Locked (Quality Gate Required)");
    }

    private void executePrediction() {
        if (isPredicting) return;
        isPredicting = true;

        // Transition to In-Progress UI
        cardGradingResult.setBackgroundResource(R.drawable.bg_quality_card_pending);
        ivGradingStatusIcon.setImageResource(R.drawable.ic_smart_chip);
        ivGradingStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));

        tvPredictedCategoryEyebrow.setText("MODEL INFERENCE ACTIVE");
        tvPredictedCategoryTitle.setText("Analyzing Retinal Image");
        tvPredictedCategoryExplanation.setText("Evaluating microvascular morphology and non-proliferative features with on-device EfficientNet-B5 ordinal regression...");
        badgePredictedGrade.setText("Analyzing");
        badgePredictedGrade.setBackgroundResource(R.drawable.bg_pill_status_not_assessed);
        badgePredictedGrade.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

        layoutPredictionLoading.setVisibility(View.VISIBLE);
        tvLoadingStatusText.setText("Executing 5-fold ensemble ordinal regression...");

        ivStage3Icon.setImageResource(R.drawable.ic_stage_in_progress);
        tvStage3Status.setText("In Progress");
        tvStage3Status.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

        // Call repository
        DrPredictionRepository.getInstance().predictDrSeverity(
                this,
                documentId,
                currentImagePath,
                patientUuid,
                qualityGatePassed,
                new DrPredictionRepository.DrPredictionCallback() {
                    @Override
                    public void onProgress(String statusMessage) {
                        mainHandler.post(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            tvLoadingStatusText.setText(statusMessage);
                        });
                    }

                    @Override
                    public void onResult(DrPredictionResult result) {
                        mainHandler.post(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            isPredicting = false;
                            layoutPredictionLoading.setVisibility(View.GONE);
                            currentResult = result;
                            applyPredictionResult(result);
                        });
                    }

                    @Override
                    public void onError(String errorMessage, Throwable error) {
                        mainHandler.post(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            isPredicting = false;
                            layoutPredictionLoading.setVisibility(View.GONE);
                            applyFailureState(errorMessage);
                        });
                    }
                }
        );
    }

    private void applyPredictionResult(DrPredictionResult result) {
        if (result == null) {
            applyFailureState("Prediction pipeline completed without returning a valid clinical result.");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a, dd MMM yyyy", Locale.getDefault());
        String timeStr = result.getAssessmentTimestamp() > 0 ? sdf.format(new Date(result.getAssessmentTimestamp())) : "Pending";
        tvInferenceTimestamp.setText(timeStr);
        tvModelVersion.setText("Model: " + result.getModelVersion() + " (" + result.getEnsembleFolds() + "-fold)");

        PredictionState state = result.getPredictionState();

        if (state == PredictionState.SUCCESS && result.getPredictedGrade() != null) {
            DrSeverityGrade grade = result.getPredictedGrade();

            // Result Card Styling based on predicted grade
            cardGradingResult.setBackgroundResource(R.drawable.bg_quality_card_passed);
            ivGradingStatusIcon.setImageResource(R.drawable.ic_stage_completed);
            ivGradingStatusIcon.setColorFilter(ContextCompat.getColor(this, grade.getColorResId()));

            tvPredictedCategoryEyebrow.setText("PREDICTED ICDR CLASSIFICATION (ON-DEVICE LOCAL AI)");
            tvPredictedCategoryTitle.setText("AI screening indicates: " + grade.getShortCategoryName());
            String expl = grade.getClinicalInterpretation();
            if (result.getModelScore() != null) {
                expl += "\n\n• 5-Fold Ensemble Regression Score: " + String.format(Locale.getDefault(), "%.3f", result.getModelScore()) +
                        "\n• Referral Recommended: " + (grade.getGradeNumber() >= 2 ? "YES (Ophthalmologist Consultation)" : "NO (Routine Follow-up)");
            }
            tvPredictedCategoryExplanation.setText(expl);

            badgePredictedGrade.setText(grade.getGradeBadgeText());
            badgePredictedGrade.setBackgroundResource(grade.getBadgeBgResId());
            badgePredictedGrade.setTextColor(ContextCompat.getColor(this, grade.getColorResId()));

            // Highlight in 5-grade visualization scale
            highlightPredictedGrade(grade);

            // Update Interpretation Section
            tvInterpretationTitle.setText("Interpretation — " + grade.getFullCategoryName());
            tvInterpretationBody.setText(grade.getClinicalInterpretation());

            // Update Recommended Next Steps
            updateNextSteps(grade.getRecommendedNextSteps());

            // Stepper Stage 03 to Completed
            ivStage3Icon.setImageResource(R.drawable.ic_stage_completed);
            tvStage3Status.setText("Completed");
            tvStage3Status.setTextColor(ContextCompat.getColor(this, R.color.netra_success));

            // Stepper Stage 04 to Ready
            if (ivStage4Icon != null) {
                ivStage4Icon.setImageResource(R.drawable.ic_stage_in_progress);
                ivStage4Icon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));
            }
            if (tvStage4Status != null) {
                tvStage4Status.setText("Ready • Tap to Start");
                tvStage4Status.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));
            }
            if (tvStage5Status != null) {
                tvStage5Status.setText("Queued (Final)");
            }

            // Unlock Downstream Lesion Segmentation
            btnContinueToSegmentationActive.setVisibility(View.VISIBLE);
            btnContinueToSegmentationLocked.setVisibility(View.GONE);
            if (btnQuickContinueToStage4 != null) {
                btnQuickContinueToStage4.setVisibility(View.VISIBLE);
            }

        } else if (state == PredictionState.SERVICE_UNAVAILABLE) {
            // Service Pending Integration (Strict medical honesty)
            cardGradingResult.setBackgroundResource(R.drawable.bg_quality_card_pending);
            ivGradingStatusIcon.setImageResource(R.drawable.ic_info_circle);
            ivGradingStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_primary_teal));

            tvPredictedCategoryEyebrow.setText("MODEL DEPLOYMENT STATUS");
            tvPredictedCategoryTitle.setText("Prediction Service Pending Integration");
            tvPredictedCategoryExplanation.setText(
                    "The NetraProof DR severity grading service (On-Device EfficientNet-B5 with ordinal regression) " +
                    "is pending server deployment. In compliance with medical software standards, synthetic diagnoses are never simulated. " +
                    "Downstream lesion segmentation remains locked until a verified prediction is obtained."
            );

            badgePredictedGrade.setText("Service Pending");
            badgePredictedGrade.setBackgroundResource(R.drawable.bg_pill_status_not_assessed);
            badgePredictedGrade.setTextColor(ContextCompat.getColor(this, R.color.netra_deep_navy));

            // Reset 5-grade scale to unselected
            initializeSeverityScaleView();

            // Interpretation
            tvInterpretationTitle.setText("Prediction Pending Deployment");
            tvInterpretationBody.setText("Once the remote inference server is connected, the predicted ICDR classification (Grade 0–4) and clinical explanation will appear here.");

            // Next steps
            tvNextStepsBody.setText(
                    "• Maintain scheduled comprehensive eye examinations with an ophthalmologist.\n" +
                    "• Continue monitoring fasting and postprandial glucose and blood pressure.\n" +
                    "• Re-test DR severity prediction once backend model integration is completed."
            );

            // Stepper
            ivStage3Icon.setImageResource(R.drawable.ic_stage_in_progress);
            tvStage3Status.setText("Pending Gate");
            tvStage3Status.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

            // Lock Downstream
            btnContinueToSegmentationActive.setVisibility(View.GONE);
            btnContinueToSegmentationLocked.setVisibility(View.VISIBLE);
            tvLockedCtaText.setText("Downstream Segmentation Locked (Pending Grading Service)");

        } else {
            applyFailureState(result.getFailureReason());
        }
    }

    private void highlightPredictedGrade(DrSeverityGrade selectedGrade) {
        populateGradeRow(itemGrade0, DrSeverityGrade.GRADE_0, selectedGrade == DrSeverityGrade.GRADE_0);
        populateGradeRow(itemGrade1, DrSeverityGrade.GRADE_1, selectedGrade == DrSeverityGrade.GRADE_1);
        populateGradeRow(itemGrade2, DrSeverityGrade.GRADE_2, selectedGrade == DrSeverityGrade.GRADE_2);
        populateGradeRow(itemGrade3, DrSeverityGrade.GRADE_3, selectedGrade == DrSeverityGrade.GRADE_3);
        populateGradeRow(itemGrade4, DrSeverityGrade.GRADE_4, selectedGrade == DrSeverityGrade.GRADE_4);
    }

    private void updateNextSteps(List<String> steps) {
        if (steps == null || steps.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            sb.append("• ").append(steps.get(i));
            if (i < steps.size() - 1) sb.append("\n");
        }
        tvNextStepsBody.setText(sb.toString());
    }

    private void applyFailureState(String reason) {
        cardGradingResult.setBackgroundResource(R.drawable.bg_quality_card_failed);
        ivGradingStatusIcon.setImageResource(R.drawable.ic_stage_failed);
        ivGradingStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_critical));

        tvPredictedCategoryEyebrow.setText("MODEL INFERENCE FAILED");
        tvPredictedCategoryTitle.setText("Unable to Complete DR Screening");
        tvPredictedCategoryExplanation.setText(reason != null && !reason.isEmpty() ? reason : "An error occurred while analyzing the retinal photograph. Please check your network connection and retry.");

        badgePredictedGrade.setText("Failed");
        badgePredictedGrade.setBackgroundResource(R.drawable.bg_pill_status_poor);
        badgePredictedGrade.setTextColor(ContextCompat.getColor(this, R.color.netra_critical));

        initializeSeverityScaleView();

        ivStage3Icon.setImageResource(R.drawable.ic_stage_failed);
        tvStage3Status.setText("Failed");
        tvStage3Status.setTextColor(ContextCompat.getColor(this, R.color.netra_critical));

        btnContinueToSegmentationActive.setVisibility(View.GONE);
        btnContinueToSegmentationLocked.setVisibility(View.VISIBLE);
        tvLockedCtaText.setText("Downstream Segmentation Locked (Prediction Failed)");
    }

    // ─── Dialogs & Modals ─────────────────────────────────────────────────────

    private void showDrGradingHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("DR Severity Grading Architecture")
                .setMessage(
                        "NetraProof employs an on-device INT8-quantized EfficientNet-B5 deep neural network formulated with ordinal regression:\n\n" +
                        "• Classification Tiers:\n" +
                        "  - Grade 0: No Apparent DR\n" +
                        "  - Grade 1: Mild NPDR (Microaneurysms only)\n" +
                        "  - Grade 2: Moderate NPDR (Intraretinal hemorrhages, hard exudates)\n" +
                        "  - Grade 3: Severe NPDR (4-2-1 rule criteria)\n" +
                        "  - Grade 4: Proliferative DR (Neovascularization, vitreous involvement)\n\n" +
                        "• Architectural Independence: Lesion segmentation (Model B: U-Net + SE-ResNeXt50) operates as an independent pipeline and is not a justification for the grading model."
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

        tvTitle.setText(isZoomMode ? "Zoom Retinal Inspection" : "Original Screened Photograph");
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

    private void onContinueToSegmentationPressed() {
        if (currentResult == null || !currentResult.isDownstreamUnlocked()) {
            Toast.makeText(this, "Cannot continue: DR severity grading has not completed successfully.", Toast.LENGTH_LONG).show();
            return;
        }

        launchSegmentationActivity();
    }

    private void launchSegmentationActivity() {
        Intent intent = new Intent(this, RetinaLesionSegmentationActivity.class);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_DOCUMENT_ID, documentId);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_IMAGE_PATH, currentImagePath);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_FILE_NAME, currentFileName);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_IMAGE_WIDTH, currentImageWidth);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_IMAGE_HEIGHT, currentImageHeight);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_IMAGE_SIZE, currentFileSizeBytes);
        intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_SESSION_ID, currentSessionId);
        if (currentResult != null && currentResult.getPredictedGrade() != null) {
            intent.putExtra(RetinaLesionSegmentationActivity.EXTRA_PREDICTED_GRADE, currentResult.getPredictedGrade().name());
        }
        startActivity(intent);
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "--";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}

