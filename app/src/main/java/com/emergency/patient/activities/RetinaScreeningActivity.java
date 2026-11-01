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
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.emergency.patient.security.TokenManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RetinaScreeningActivity — NetraProof Retina Screening Module (Page 02)
 *
 * Implements the complete clinical entry point for AI-assisted retinal screening:
 *  - Header with back navigation, history shortcut, and branding.
 *  - Authenticated Patient Information Card (dynamic EHR lookup).
 *  - 5-Stage Visual Workflow Stepper (strictly genuine progress states).
 *  - Retinal Image Upload: SAF Gallery Picker & High-Resolution Camera Capture.
 *  - Clinical Image Preview with dimensions, file size, and replace/remove controls.
 *  - Fundus Image Quality Guidance & Clinical Constraints.
 *  - AI Diagnostic Engine Technical Architecture (Model A: DR Grading, Model B: Lesion Segmentation).
 *  - Primary CTA: Verification, draft persistence in Room DB, and progression to Stage 02.
 */
public class RetinaScreeningActivity extends AppCompatActivity {

    // ─── View References ──────────────────────────────────────────────────────

    // Header
    private View btnBack, btnScreeningHistory;

    // Patient Information Card
    private TextView tvPatientName, tvPatientId, tvPatientDemographics, tvScreeningDate, tvScreeningStatusBadge;

    // Workflow Stepper Icons & Statuses
    private ImageView ivStage1Icon, ivStage2Icon, ivStage3Icon, ivStage4Icon, ivStage5Icon;
    private TextView tvStage1Status, tvStage2Status, tvStage3Status, tvStage4Status, tvStage5Status;

    // Upload & Preview Containers
    private LinearLayout layoutUploadOptions, cardImagePreview;
    private View btnChooseGallery, btnCaptureCamera, btnTryDemoFundus, btnReplaceImage, btnRemoveImage;
    private ImageView ivRetinaPreview;
    private TextView tvImageName, tvImageDimensions, tvImageSize;

    // Primary Action
    private View btnContinueQualityAssessment, btnAnalyzePreview;

    // ─── State ────────────────────────────────────────────────────────────────

    private Uri currentSelectedUri;
    private File currentTempCameraFile;
    private String currentFileName;
    private int currentImageWidth = 0;
    private int currentImageHeight = 0;
    private long currentFileSizeBytes = 0;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── Activity Result Launchers ────────────────────────────────────────────

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchCamera();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Camera Permission Required")
                            .setMessage("NetraProof requires camera access to capture retinal fundus images through ophthalmic lens attachments. You can also upload existing fundus photos from your gallery.")
                            .setPositiveButton("Choose from Gallery", (d, w) -> launchGallery())
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    Uri uri = result.getData().getData();
                    processSelectedImage(uri);
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && currentTempCameraFile != null && currentTempCameraFile.exists()) {
                    Uri photoUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", currentTempCameraFile);
                    processSelectedImage(photoUri);
                }
            });

    // ─── Lifecycle ────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retina_screening);

        bindViews();
        setupClickListeners();
        loadPatientData();
        com.emergency.patient.utils.NetraProfileHeaderHelper.setup(this);
        com.emergency.patient.utils.NetraBottomNavHelper.setup(this, com.emergency.patient.utils.NetraBottomNavHelper.TAB_RETINA);
        com.emergency.patient.network.NetworkClient.pingHealth();
    }

    // ─── View Binding ─────────────────────────────────────────────────────────

    private void bindViews() {
        // Header
        btnBack             = findViewById(R.id.btnBack);
        btnScreeningHistory = findViewById(R.id.btnScreeningHistory);

        // Patient Info
        tvPatientName         = findViewById(R.id.tvPatientName);
        tvPatientId           = findViewById(R.id.tvPatientId);
        tvPatientDemographics = findViewById(R.id.tvPatientDemographics);
        tvScreeningDate       = findViewById(R.id.tvScreeningDate);
        tvScreeningStatusBadge= findViewById(R.id.tvScreeningStatusBadge);

        // Stepper
        ivStage1Icon   = findViewById(R.id.ivStage1Icon);
        tvStage1Status = findViewById(R.id.tvStage1Status);
        ivStage2Icon   = findViewById(R.id.ivStage2Icon);
        tvStage2Status = findViewById(R.id.tvStage2Status);
        ivStage3Icon   = findViewById(R.id.ivStage3Icon);
        tvStage3Status = findViewById(R.id.tvStage3Status);
        ivStage4Icon   = findViewById(R.id.ivStage4Icon);
        tvStage4Status = findViewById(R.id.tvStage4Status);
        ivStage5Icon   = findViewById(R.id.ivStage5Icon);
        tvStage5Status = findViewById(R.id.tvStage5Status);

        // Upload & Preview
        layoutUploadOptions = findViewById(R.id.layoutUploadOptions);
        btnChooseGallery    = findViewById(R.id.btnChooseGallery);
        btnCaptureCamera    = findViewById(R.id.btnCaptureCamera);
        btnTryDemoFundus    = findViewById(R.id.btnTryDemoFundus);

        cardImagePreview    = findViewById(R.id.cardImagePreview);
        ivRetinaPreview     = findViewById(R.id.ivRetinaPreview);
        tvImageName         = findViewById(R.id.tvImageName);
        tvImageDimensions   = findViewById(R.id.tvImageDimensions);
        tvImageSize         = findViewById(R.id.tvImageSize);
        btnReplaceImage     = findViewById(R.id.btnReplaceImage);
        btnRemoveImage      = findViewById(R.id.btnRemoveImage);
        btnAnalyzePreview   = findViewById(R.id.btnAnalyzePreview);

        // CTA
        btnContinueQualityAssessment = findViewById(R.id.btnContinueQualityAssessment);
    }

    // ─── Setup Click Listeners ────────────────────────────────────────────────

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnScreeningHistory.setOnClickListener(v -> showScreeningHistoryDialog());

        btnChooseGallery.setOnClickListener(v -> launchGallery());

        btnCaptureCamera.setOnClickListener(v -> checkCameraPermissionAndLaunch());

        if (btnTryDemoFundus != null) {
            btnTryDemoFundus.setOnClickListener(v -> showClinicalDemoSelector());
        }

        btnReplaceImage.setOnClickListener(v -> showReplaceImageDialog());

        btnRemoveImage.setOnClickListener(v -> resetImageSelection());

        if (btnAnalyzePreview != null) {
            btnAnalyzePreview.setOnClickListener(v -> onContinuePressed());
        }

        btnContinueQualityAssessment.setOnClickListener(v -> onContinuePressed());
    }

    // ─── Patient Information Loading ──────────────────────────────────────────

    private void loadPatientData() {
        String uuid = TokenManager.getUUID(this);
        SimpleDateFormat sdf = new SimpleDateFormat("d MMMM yyyy", Locale.getDefault());
        tvScreeningDate.setText(sdf.format(new Date()));

        new Thread(() -> {
            PatientEntity patient = null;
            try {
                patient = AppDatabaseProvider.getInstance(this).patientDao().getPatient(uuid);
            } catch (Exception ignored) {}

            final PatientEntity finalPatient = patient;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;

                if (finalPatient != null) {
                    // Name
                    String name = finalPatient.fullName != null && !finalPatient.fullName.trim().isEmpty()
                            ? finalPatient.fullName.trim() : "Patient";
                    tvPatientName.setText(name);

                    // ID
                    String displayId = "NP-" + (uuid != null && uuid.length() >= 6 ? uuid.substring(0, 6).toUpperCase() : "001");
                    tvPatientId.setText("ID: " + displayId);

                    // Demographics (Age & Gender)
                    String ageStr = "--";
                    if (finalPatient.dobMillis > 0) {
                        long diff = System.currentTimeMillis() - finalPatient.dobMillis;
                        long age = (long) (diff / (365.25 * 24 * 60 * 60 * 1000));
                        if (age > 0 && age < 120) {
                            ageStr = age + " yrs";
                        }
                    }
                    String genderStr = finalPatient.gender != null && !finalPatient.gender.trim().isEmpty()
                            ? finalPatient.gender.trim() : "Not specified";
                    tvPatientDemographics.setText("Age: " + ageStr + "  •  Gender: " + genderStr);
                } else {
                    tvPatientName.setText("Patient");
                    tvPatientId.setText("ID: NP-001");
                    tvPatientDemographics.setText("Age: --  •  Gender: --");
                }
            });
        }).start();
    }

    // ─── Image Picker & Camera Operations ─────────────────────────────────────

    private void launchGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            galleryLauncher.launch(Intent.createChooser(intent, "Select Retinal Photograph"));
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(fallback);
        }
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
            currentTempCameraFile = new File(cacheDir, "retina_capture_" + System.currentTimeMillis() + ".jpg");

            Uri photoUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", currentTempCameraFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not launch camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showReplaceImageDialog() {
        String[] options = {"Choose from Medical Gallery", "Capture with Camera", "Load Clinical Demo Fundus (Cloud Model Test)"};
        new AlertDialog.Builder(this)
                .setTitle("Replace Retinal Image")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) launchGallery();
                    else if (which == 1) checkCameraPermissionAndLaunch();
                    else showClinicalDemoSelector();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Image Processing & Validation ────────────────────────────────────────

    private void processSelectedImage(Uri uri) {
        if (uri == null) return;

        try {
            // Extract Display Name
            String displayName = queryDisplayName(uri);
            if (displayName == null || displayName.isEmpty()) {
                displayName = "retina_fundus_" + System.currentTimeMillis() + ".jpg";
            }
            currentFileName = displayName;

            // Extract Dimensions without full memory decode
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is != null) BitmapFactory.decodeStream(is, null, opts);
            }

            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                showCorruptedImageError();
                return;
            }

            currentImageWidth = opts.outWidth;
            currentImageHeight = opts.outHeight;

            // Extract File Size
            long sizeBytes = queryFileSize(uri);
            if (sizeBytes <= 0 && currentTempCameraFile != null && currentTempCameraFile.exists()) {
                sizeBytes = currentTempCameraFile.length();
            }
            currentFileSizeBytes = sizeBytes;

            // Save reference
            currentSelectedUri = uri;

            // Display in Preview with safe sampled bitmap
            try {
                BitmapFactory.Options thumbOpts = new BitmapFactory.Options();
                int sample = 1;
                while ((opts.outWidth / sample) > 1024 || (opts.outHeight / sample) > 1024) {
                    sample *= 2;
                }
                thumbOpts.inSampleSize = sample;
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    Bitmap thumbBmp = BitmapFactory.decodeStream(is, null, thumbOpts);
                    if (thumbBmp != null) {
                        ivRetinaPreview.setImageBitmap(thumbBmp);
                    } else {
                        ivRetinaPreview.setImageURI(uri);
                    }
                }
            } catch (Throwable t) {
                ivRetinaPreview.setImageURI(uri);
            }
            tvImageName.setText(currentFileName);
            tvImageDimensions.setText("Dimensions: " + currentImageWidth + " × " + currentImageHeight + " px");
            tvImageSize.setText("Size: " + formatFileSize(currentFileSizeBytes));

            // Switch to Preview State
            layoutUploadOptions.setVisibility(View.GONE);
            cardImagePreview.setVisibility(View.VISIBLE);

            // Update Stepper Stage 01 to Completed
            ivStage1Icon.setImageResource(R.drawable.ic_stage_completed);
            tvStage1Status.setText("Completed");
            tvStage1Status.setTextColor(ContextCompat.getColor(this, R.color.netra_success));

            tvScreeningStatusBadge.setText("Image Loaded");
            Toast.makeText(this, "Retinal image loaded. Tap 'Analyze Retinal Image' to proceed.", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            showCorruptedImageError();
        }
    }

    private void resetImageSelection() {
        currentSelectedUri = null;
        currentTempCameraFile = null;
        currentFileName = null;
        currentImageWidth = 0;
        currentImageHeight = 0;
        currentFileSizeBytes = 0;

        ivRetinaPreview.setImageDrawable(null);
        cardImagePreview.setVisibility(View.GONE);
        layoutUploadOptions.setVisibility(View.VISIBLE);

        // Reset Stepper Stage 01
        ivStage1Icon.setImageResource(R.drawable.ic_stage_in_progress);
        tvStage1Status.setText("In Progress");
        tvStage1Status.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

        tvScreeningStatusBadge.setText("Intake Ready");
    }

    private void showCorruptedImageError() {
        new AlertDialog.Builder(this)
                .setTitle("Invalid Retinal Image")
                .setMessage("The selected file could not be read as a valid retinal fundus image. Please ensure you are choosing an uncorrupted JPG or PNG photograph.")
                .setPositiveButton("Choose Another", (d, w) -> launchGallery())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Primary Action & Quality Assessment Bridge ───────────────────────────

    private void onContinuePressed() {
        if (currentSelectedUri == null) {
            new AlertDialog.Builder(this)
                    .setTitle("No Retinal Image Selected")
                    .setMessage("Please upload or capture a clear retinal fundus photograph before proceeding to Stage 02 (Image Quality Assessment).")
                    .setPositiveButton("Upload Now", (d, w) -> launchGallery())
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // Save Screening Draft to App Internal Storage & Room Database
        new Thread(() -> {
            try {
                File screeningsDir = new File(getFilesDir(), "netra_screenings");
                if (!screeningsDir.exists()) screeningsDir.mkdirs();

                String safeFilename = "draft_screening_" + System.currentTimeMillis() + ".jpg";
                File localDraftFile = new File(screeningsDir, safeFilename);

                // Copy stream to persistent app storage
                try (InputStream in = getContentResolver().openInputStream(currentSelectedUri);
                     FileOutputStream out = new FileOutputStream(localDraftFile)) {
                    if (in != null) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    }
                }

                // Associate with authenticated patient in Room DB
                String uuid = TokenManager.getUUID(this);
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
                String title = "Retina Screening Draft (" + sdf.format(new Date()) + ")";

                HealthDocumentEntity doc = new HealthDocumentEntity(uuid, title, localDraftFile.getAbsolutePath());
                doc.extractionStatus = "draft_quality_pending";
                doc.extractedJson = "{\"stage\":1,\"pipeline\":\"netraproof_v2\",\"file_dimensions\":\""
                        + currentImageWidth + "x" + currentImageHeight + "\"}";

                long docId = AppDatabaseProvider.getInstance(this).healthDocumentDao().insertDocument(doc);
                final int finalDocId = (int) docId;

                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    // Update Stepper Stage 02 Indicator to Active
                    ivStage2Icon.setImageResource(R.drawable.ic_stage_in_progress);
                    tvStage2Status.setText("In Progress");
                    tvStage2Status.setTextColor(ContextCompat.getColor(this, R.color.netra_primary_teal));

                    // Directly transition to Stage 02 (Quality Assessment) without dialog
                    Intent intent = new Intent(this, RetinaQualityAssessmentActivity.class);
                    intent.putExtra(RetinaQualityAssessmentActivity.EXTRA_DOCUMENT_ID, finalDocId);
                    intent.putExtra(RetinaQualityAssessmentActivity.EXTRA_IMAGE_PATH, localDraftFile.getAbsolutePath());
                    intent.putExtra(RetinaQualityAssessmentActivity.EXTRA_FILE_NAME, currentFileName);
                    intent.putExtra(RetinaQualityAssessmentActivity.EXTRA_IMAGE_WIDTH, currentImageWidth);
                    intent.putExtra(RetinaQualityAssessmentActivity.EXTRA_IMAGE_HEIGHT, currentImageHeight);
                    intent.putExtra(RetinaQualityAssessmentActivity.EXTRA_IMAGE_SIZE, currentFileSizeBytes);
                    intent.putExtra(RetinaQualityAssessmentActivity.EXTRA_SESSION_ID, "NP-SCR-" + String.format(Locale.getDefault(), "%05d", finalDocId > 0 ? finalDocId : (int)(System.currentTimeMillis() % 100000)));
                    startActivity(intent);
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "Failed to save draft: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // ─── Screening History Shortcut ───────────────────────────────────────────

    private void showScreeningHistoryDialog() {
        String uuid = TokenManager.getUUID(this);

        new Thread(() -> {
            List<HealthDocumentEntity> allDocs = new ArrayList<>();
            try {
                allDocs = AppDatabaseProvider.getInstance(this).healthDocumentDao().getDocumentsForPatient(uuid);
            } catch (Exception ignored) {}

            List<HealthDocumentEntity> retinaDocs = new ArrayList<>();
            if (allDocs != null) {
                for (HealthDocumentEntity d : allDocs) {
                    if (d.displayName != null && (d.displayName.contains("Retina") || d.displayName.contains("Screening"))) {
                        retinaDocs.add(d);
                    }
                }
            }

            final List<HealthDocumentEntity> finalRetinaDocs = retinaDocs;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;

                if (finalRetinaDocs.isEmpty()) {
                    new AlertDialog.Builder(this)
                            .setTitle("Retinal Screening History")
                            .setMessage("No previous retinal screening sessions found for this patient record.\n\nComplete your initial screening to establish your baseline ocular health score.")
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    String[] titles = new String[finalRetinaDocs.size()];
                    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
                    for (int i = 0; i < finalRetinaDocs.size(); i++) {
                        HealthDocumentEntity doc = finalRetinaDocs.get(i);
                        titles[i] = doc.displayName + " (" + sdf.format(new Date(doc.uploadTimestamp)) + ")";
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Retinal Screening History (" + finalRetinaDocs.size() + ")")
                            .setItems(titles, (d, which) -> {
                                HealthDocumentEntity selected = finalRetinaDocs.get(which);
                                Toast.makeText(this, "Selected: " + selected.displayName, Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Close", null)
                            .show();
                }
            });
        }).start();
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    
    private void showClinicalDemoSelector() {
        String[] demoTitles = {
                "Severe Proliferative Retinopathy (Grade 2+ Referable)",
                "Mild Non-Proliferative Retinopathy (Grade 1 Mild)",
                "Optical Motion Blur (Quality Gate Rejection Test)"
        };
        final String[] assetNames = {
                "demo_gradable_referable.jpg",
                "demo_gradable_mild.jpg",
                "demo_ungradable_blurry.jpg"
        };
        new AlertDialog.Builder(this)
                .setTitle("Select Clinical Demonstration Fundus")
                .setItems(demoTitles, (dialog, which) -> {
                    loadDemoFundusAsset(assetNames[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadDemoFundusAsset(String assetFilename) {
        new Thread(() -> {
            try {
                File cacheDir = new File(getCacheDir(), "netra_demos");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File targetFile = new File(cacheDir, assetFilename);

                try (InputStream in = getAssets().open(assetFilename);
                     FileOutputStream out = new FileOutputStream(targetFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }

                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    processSelectedImage(Uri.fromFile(targetFile));
                    Toast.makeText(this, "Loaded clinical demo fundus: " + assetFilename, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "Failed to load demo asset: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private String queryDisplayName(Uri uri) {
        String result = null;
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                result = cursor.getString(0);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private long queryFileSize(Uri uri) {
        long size = 0;
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                size = cursor.getLong(0);
            }
        } catch (Exception ignored) {}
        return size;
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "--";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
