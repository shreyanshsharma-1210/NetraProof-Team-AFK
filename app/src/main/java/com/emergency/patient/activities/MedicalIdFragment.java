package com.emergency.patient.activities;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import android.webkit.MimeTypeMap;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.HealthDocumentEntity;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.network.SmsFallbackManager;
import com.emergency.patient.network.SocketManager;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.utils.QrGenerator;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MedicalIdFragment extends Fragment {

    // ─── Views ────────────────────────────────────────────────────────────────
    private TextView tvCardUniqueId, tvPatientName, tvBloodGroup, tvPatientAge;
    private TextView tvMedIdAllergies, tvMedIdConditions, tvMedIdDonor, tvMedIdEmergencyContact;
    private ImageView ivQrCode, ivAvatar, ivQrFullscreen;
    private FrameLayout sliderContainer, sliderThumb, countdownOverlay, qrFullscreenOverlay;
    private TextView tvSlideHint, tvCountdown, tvCountdownLabel;
    private Button btnCancelSos;

    // Uploaded Documents Views
    private LinearLayout llMedIdDocumentsContainer, llMedIdDocsEmptyState;
    private TextView btnUploadNewDocMedId;

    // ─── Slider State ─────────────────────────────────────────────────────────
    private CountDownTimer countDownTimer;
    private ToneGenerator toneGenerator;
    private float initialX;
    private boolean isSosTriggered = false;

    // ─── Keep generated QR bitmap for fullscreen ───────────────────────────
    private Bitmap lastQrBitmap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_medical_id, container, false);
        bindViews(view);
        setupQrFullscreen(view);
        setupSlider();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        cancelCountdown();
        populatePatientData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelCountdown();
    }

    // ─── Binding ─────────────────────────────────────────────────────────────

    private void bindViews(View view) {
        tvCardUniqueId         = view.findViewById(R.id.tvCardUniqueId);
        tvPatientName          = view.findViewById(R.id.tv_patient_name_med_id);
        tvBloodGroup           = view.findViewById(R.id.tv_blood_group_med_id);
        tvPatientAge           = view.findViewById(R.id.tv_patient_age_med_id);
        tvMedIdAllergies       = view.findViewById(R.id.tvMedIdAllergies);
        tvMedIdConditions      = view.findViewById(R.id.tvMedIdConditions);
        tvMedIdDonor           = view.findViewById(R.id.tvMedIdDonor);
        tvMedIdEmergencyContact = view.findViewById(R.id.tvMedIdEmergencyContact);

        ivQrCode               = view.findViewById(R.id.iv_qr_code_med_id);
        ivAvatar               = view.findViewById(R.id.iv_avatar_med_id);
        ivQrFullscreen         = view.findViewById(R.id.iv_qr_fullscreen);
        qrFullscreenOverlay    = view.findViewById(R.id.fl_qr_fullscreen);
        sliderContainer        = view.findViewById(R.id.slider_container_med);
        sliderThumb            = view.findViewById(R.id.slider_thumb_med);
        tvSlideHint            = view.findViewById(R.id.tv_slide_hint_med);
        countdownOverlay       = view.findViewById(R.id.fl_med_countdown_overlay);
        tvCountdown            = view.findViewById(R.id.tv_med_countdown);
        tvCountdownLabel       = view.findViewById(R.id.tv_med_countdown_label);
        btnCancelSos           = view.findViewById(R.id.btn_cancel_med_sos);

        llMedIdDocumentsContainer = view.findViewById(R.id.ll_med_id_documents_container);
        llMedIdDocsEmptyState     = view.findViewById(R.id.ll_med_id_docs_empty_state);
        btnUploadNewDocMedId      = view.findViewById(R.id.btn_upload_new_doc_med_id);

        if (btnUploadNewDocMedId != null) {
            btnUploadNewDocMedId.setOnClickListener(v -> {
                startActivity(new Intent(getContext(), DocumentUploadActivity.class));
            });
        }

        btnCancelSos.setOnClickListener(v -> cancelCountdown());
    }

    // ─── Patient Data ────────────────────────────────────────────────────────

    private void populatePatientData() {
        if (getContext() == null) return;

        String uuid = TokenManager.getUUID(getContext());

        // Run DB query in background to avoid Main Thread violations
        new Thread(() -> {
            AppDatabase db = AppDatabaseProvider.getInstance(getContext());
            PatientEntity patient = db.patientDao().getPatient(uuid);
            java.util.List<com.emergency.patient.db.EmergencyContactEntity> contacts = 
                    db.emergencyContactDao().getContactsForPatient(uuid);
            List<HealthDocumentEntity> docs = db.healthDocumentDao().getDocumentsForPatient(uuid);

            String primaryContact = null;
            if (contacts != null && !contacts.isEmpty()) {
                primaryContact = contacts.get(0).name + " (" + contacts.get(0).phoneNumber + ")";
            }

            final String finalContact = primaryContact;
            final List<HealthDocumentEntity> finalDocs = docs;
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                bindPatientToUI(patient, uuid, finalContact);
                populateDocumentsUI(finalDocs, uuid);
            });
        }).start();
    }

    private void populateDocumentsUI(List<HealthDocumentEntity> docs, String uuid) {
        if (llMedIdDocumentsContainer == null || getContext() == null) return;

        llMedIdDocumentsContainer.removeAllViews();

        if (docs == null || docs.isEmpty()) {
            if (llMedIdDocsEmptyState != null) llMedIdDocsEmptyState.setVisibility(View.VISIBLE);
            llMedIdDocumentsContainer.setVisibility(View.GONE);
            return;
        }

        if (llMedIdDocsEmptyState != null) llMedIdDocsEmptyState.setVisibility(View.GONE);
        llMedIdDocumentsContainer.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

        for (HealthDocumentEntity doc : docs) {
            View itemView = inflater.inflate(R.layout.item_medical_id_doc, llMedIdDocumentsContainer, false);

            TextView tvDocName = itemView.findViewById(R.id.tv_med_id_doc_name);
            TextView tvDocDate = itemView.findViewById(R.id.tv_med_id_doc_date);
            TextView tvDocStatus = itemView.findViewById(R.id.tv_med_id_doc_status);
            ImageView ivDelete = itemView.findViewById(R.id.iv_med_id_delete_doc);

            if (tvDocName != null) tvDocName.setText(doc.displayName != null ? doc.displayName : "Health Document");
            if (tvDocDate != null) {
                String dateStr = doc.uploadTimestamp > 0 ? sdf.format(new Date(doc.uploadTimestamp)) : "Recently added";
                tvDocDate.setText("Uploaded " + dateStr);
            }
            if (tvDocStatus != null) {
                if ("success".equalsIgnoreCase(doc.extractionStatus)) {
                    tvDocStatus.setText("• AI Ready");
                    tvDocStatus.setTextColor(android.graphics.Color.parseColor("#087F68"));
                } else if ("failed".equalsIgnoreCase(doc.extractionStatus)) {
                    tvDocStatus.setText("• Uploaded");
                    tvDocStatus.setTextColor(android.graphics.Color.parseColor("#687790"));
                } else {
                    tvDocStatus.setText("• Synced");
                    tvDocStatus.setTextColor(android.graphics.Color.parseColor("#2563EB"));
                }
            }

            // Click on document to open/preview
            itemView.setOnClickListener(v -> openDocument(doc.internalFilePath, doc.displayName));

            // Delete document
            if (ivDelete != null) {
                ivDelete.setOnClickListener(v -> {
                    if (getContext() == null) return;
                    new AlertDialog.Builder(getContext())
                            .setTitle("Delete Document")
                            .setMessage("Are you sure you want to remove '" + doc.displayName + "'?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                // 1. Trigger Cloud Deletion
                                com.emergency.patient.network.FirebaseRepository.getInstance(getContext())
                                        .deleteDocumentFromCloud(doc.cloudPublicId, doc.firestoreId, new com.emergency.patient.network.FirebaseRepository.SyncCallback() {
                                            @Override
                                            public void onSuccess() {
                                                new Thread(() -> {
                                                    AppDatabaseProvider.getInstance(getContext()).healthDocumentDao().deleteDocument(doc.id);
                                                    if (getActivity() != null) {
                                                        getActivity().runOnUiThread(() -> populatePatientData());
                                                    }
                                                }).start();
                                            }

                                            @Override
                                            public void onFailure(Exception e) {
                                                new Thread(() -> {
                                                    AppDatabaseProvider.getInstance(getContext()).healthDocumentDao().deleteDocument(doc.id);
                                                    if (getActivity() != null) {
                                                        getActivity().runOnUiThread(() -> populatePatientData());
                                                    }
                                                }).start();
                                            }
                                        });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            }

            llMedIdDocumentsContainer.addView(itemView);
        }
    }

    private void openDocument(String innerPath, String docName) {
        if (getContext() == null || innerPath == null || innerPath.isEmpty()) return;

        File file = new File(innerPath);
        if (!file.exists()) {
            Toast.makeText(getContext(), "Document file not found on device.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                    getContext(),
                    getContext().getApplicationContext().getPackageName() + ".provider",
                    file);

            String type = getContext().getContentResolver().getType(uri);
            if (type == null) {
                String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            }
            if (type == null) type = "*/*";

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, type);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(Intent.createChooser(intent, "Open " + docName));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Please install a PDF or image viewer app to open this document.", Toast.LENGTH_LONG).show();
        }
    }

    private void bindPatientToUI(PatientEntity patient, String uuid, String emergencyContact) {
        if (getContext() == null) return;

        // 0. Virtual ID Code
        String cardCode = "SANJ-2026-IND-";
        if (uuid != null && uuid.length() >= 4) {
            cardCode += uuid.substring(0, 4).toUpperCase();
        } else {
            cardCode += "8821";
        }
        if (tvCardUniqueId != null) {
            tvCardUniqueId.setText(cardCode);
        }

        // 1. Full name
        String name = (patient != null && patient.fullName != null && !patient.fullName.trim().isEmpty())
                ? patient.fullName : TokenManager.getPatientName(getContext());
        tvPatientName.setText((name == null || name.trim().isEmpty()) ? "Registered Citizen" : name);

        // 2. Blood Group
        String blood = (patient != null && patient.bloodGroup != null && !patient.bloodGroup.trim().isEmpty())
                ? patient.bloodGroup : TokenManager.getBloodGroup(getContext());
        tvBloodGroup.setText("Blood Group: " + (blood != null && !blood.isEmpty() ? blood : "O+ (Auto-Assigned)"));

        // 3. Age & Gender
        long dobMillis = (patient != null && patient.dobMillis > 0)
                ? patient.dobMillis : TokenManager.getDOB(getContext());
        String gender = (patient != null && patient.gender != null && !patient.gender.isEmpty())
                ? patient.gender : TokenManager.getGender(getContext());
        if (gender == null || gender.isEmpty()) gender = "Citizen";

        if (dobMillis > 0) {
            int age = calculateAge(dobMillis);
            tvPatientAge.setText(age + " yrs • " + gender);
            tvPatientAge.setVisibility(View.VISIBLE);
        } else {
            tvPatientAge.setText(gender);
            tvPatientAge.setVisibility(View.VISIBLE);
        }

        // 4. Allergies
        if (tvMedIdAllergies != null) {
            String allergies = (patient != null && patient.clinicalAllergies != null && !patient.clinicalAllergies.trim().isEmpty())
                    ? patient.clinicalAllergies : "None Reported (NKDA)";
            tvMedIdAllergies.setText(allergies);
        }

        // 5. Conditions
        if (tvMedIdConditions != null) {
            String conditions = (patient != null && patient.pastMedicalDiagnosis != null && !patient.pastMedicalDiagnosis.trim().isEmpty())
                    ? patient.pastMedicalDiagnosis : "None Recorded";
            tvMedIdConditions.setText(conditions);
        }

        // 6. Organ Donor Status
        if (tvMedIdDonor != null) {
            tvMedIdDonor.setText("YES • REGISTERED");
        }

        // 7. Emergency Contact
        if (tvMedIdEmergencyContact != null) {
            if (emergencyContact != null && !emergencyContact.isEmpty()) {
                tvMedIdEmergencyContact.setText(emergencyContact);
            } else {
                tvMedIdEmergencyContact.setText("112 / 108 (National SOS)");
            }
        }

        // 8. Profile Photo
        String photoUri = (patient != null) ? patient.profilePhotoUri : null;
        if (photoUri != null && !photoUri.isEmpty()) {
            try {
                ivAvatar.setImageURI(Uri.parse(photoUri));
                ivAvatar.setPadding(0, 0, 0, 0); // remove default padding when real photo is loaded
            } catch (Exception e) {
                ivAvatar.setImageResource(R.drawable.ic_blank_profile);
                ivAvatar.setPadding(0, 0, 0, 0);
            }
        } else {
            ivAvatar.setImageResource(R.drawable.ic_blank_profile);
            ivAvatar.setPadding(0, 0, 0, 0);
        }

        // 9. Generate QR (Encoded with Firebase UID for doctor app lookup)
        String qrData = TokenManager.getFirebaseUID(getContext());
        if (qrData == null || qrData.isEmpty()) {
            qrData = (patient != null) ? patient.uuid : uuid; // Fallback
        }

        if (qrData != null) {
            lastQrBitmap = QrGenerator.generate(qrData, 512);
            if (lastQrBitmap != null) {
                ivQrCode.setImageBitmap(lastQrBitmap);
            }
        }
    }

    // ─── QR Full Screen ──────────────────────────────────────────────────────

    private void setupQrFullscreen(View view) {
        // Tap the QR card to expand
        View qrCard = view.findViewById(R.id.card_qr_tap);
        qrCard.setOnClickListener(v -> {
            if (lastQrBitmap != null) {
                ivQrFullscreen.setImageBitmap(lastQrBitmap);
                qrFullscreenOverlay.setVisibility(View.VISIBLE);
            }
        });

        // Tap the overlay to dismiss
        qrFullscreenOverlay.setOnClickListener(v ->
                qrFullscreenOverlay.setVisibility(View.GONE));
    }

    // ─── Slider ──────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private void setupSlider() {
        sliderThumb.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = event.getRawX();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - initialX;
                    float maxTranslation = sliderContainer.getWidth() - sliderThumb.getWidth() - 16;

                    if (deltaX < 0) deltaX = 0;
                    if (deltaX > maxTranslation) deltaX = maxTranslation;

                    sliderThumb.setTranslationX(deltaX);

                    float alpha = 1.0f - (deltaX / (maxTranslation / 1.5f));
                    tvSlideHint.setAlpha(Math.max(0, alpha));
                    return true;

                case MotionEvent.ACTION_UP:
                    float finalDeltaX = sliderThumb.getTranslationX();
                    float threshold = (sliderContainer.getWidth() - sliderThumb.getWidth()) * 0.85f;

                    if (finalDeltaX >= threshold) {
                        startCountdown();
                    } else {
                        sliderThumb.animate().translationX(0).setDuration(200).start();
                        tvSlideHint.animate().alpha(1.0f).setDuration(200).start();
                    }
                    return true;
            }
            return false;
        });
    }

    private void startCountdown() {
        if (isSosTriggered) return;
        isSosTriggered = true;
        countdownOverlay.setVisibility(View.VISIBLE);

        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (Exception ignored) {}

        countDownTimer = new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000) + 1;
                tvCountdown.setText(String.valueOf(seconds));
                if (toneGenerator != null) {
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400);
                }
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("0");
                triggerEmergency();
            }
        }.start();
    }

    private void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        isSosTriggered = false;
        if (countdownOverlay != null) countdownOverlay.setVisibility(View.GONE);
        if (sliderThumb != null) sliderThumb.setTranslationX(0);
        if (tvSlideHint != null) tvSlideHint.setAlpha(1.0f);
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    private void triggerEmergency() {
        try {
            if (getActivity() == null) return;
            android.util.Log.wtf("DEBUG_PING", "!!! MedicalIdFragment: triggerEmergency() CALLED !!!");

            double lat = 22.7196;
            double lng = 75.8577;
            try {
                android.location.LocationManager locationManager = (android.location.LocationManager) getActivity().getSystemService(android.content.Context.LOCATION_SERVICE);
                if (androidx.core.content.ContextCompat.checkSelfPermission(getActivity(), android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    android.location.Location loc = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                    if (loc == null) loc = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                    if (loc != null) {
                        lat = loc.getLatitude();
                        lng = loc.getLongitude();
                    }
                }
            } catch (Exception e) {
                // Ignore and use fallback
            }

            // Trigger cloud webhook IMMEDIATELY with actual inline location
            com.emergency.patient.network.AmbulancePingManager.getInstance(getActivity()).sendPing(lat, lng);

            tvCountdownLabel.setText(R.string.processing_ai);
            SocketManager.getInstance(getActivity()).connect();

        // Fetch real GPS location

        com.emergency.patient.utils.LocationHelper.getLastKnownLocation(getActivity(), new com.emergency.patient.utils.LocationHelper.LocationCallback() {
            @Override
            public void onLocationResult(double lat, double lng) {
                if (getActivity() == null) return;
                
                // Emit real-time emergency to backend
                SocketManager.getInstance(getActivity()).emitEmergency(lat, lng, new SocketManager.DispatchCallback() {
                    @Override
                    public void onSuccess(String channel) {
                        if (getActivity() == null) return;
                        android.content.Intent intent = new android.content.Intent(getActivity(), EmergencyActiveActivity.class);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_LAT, lat);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_LNG, lng);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_CHANNEL, channel);
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }

                    @Override
                    public void onFallbackRequired(double lat, double lng, String uuid) {
                        if (getActivity() == null) return;
                        android.content.Intent intent = new android.content.Intent(getActivity(), EmergencyActiveActivity.class);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_LAT, lat);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_LNG, lng);
                        intent.putExtra(EmergencyActiveActivity.EXTRA_CHANNEL, "SMS Fallback");
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }
                });
            }
        });
        } catch (Exception e) {
            android.util.Log.e("MedicalIdFragment", "SOS trigger failed", e);
            if (getActivity() != null) {
                android.content.Intent intent = new android.content.Intent(getActivity(), EmergencyActiveActivity.class);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private int calculateAge(long dobMillis) {
        Calendar dob = Calendar.getInstance();
        dob.setTimeInMillis(dobMillis);
        Calendar today = Calendar.getInstance();
        int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
        if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
            age--;
        }
        return age;
    }
}
