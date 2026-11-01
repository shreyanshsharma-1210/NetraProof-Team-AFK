package com.emergency.patient.activities;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.emergency.patient.BuildConfig;
import com.emergency.patient.R;
import com.emergency.patient.db.AdherenceLogEntity;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.HealthDocumentEntity;
import com.emergency.patient.db.MedicationScheduleEntity;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.scheduling.ActionReceiver;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.services.EmergencyDispatcher;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MedicationSafetyHomeFragment — NetraProof Health Dashboard Controller
 *
 * Implements the 11 clinical layers for AI-Powered Diabetes Screening & Whole-Body Health:
 *  A. Header & Welcome (Personalized profile greeting, date, notifications)
 *  B. Unified Search & Netra AI Shortcut (Voice input & AI assistant)
 *  C. Primary Hero — Retina Intelligence (Quality check, DR grading, lesion segmentation, dual CTAs)
 *  D. Diabetes360 Health Overview (Glucose, Heart, HbA1c, Kidney — strictly zero fabricated data)
 *  E. Health Quick Actions (Screening, Upload Report, Log Glucose, Health Passport)
 *  F. Recent Health Activity (Live timeline from Room database)
 *  G. Upcoming Reminders (Live medication schedules from Room database)
 *  H. Health Insights & Netra AI (Evidence-based clinical guidance)
 *  I. Doctor Connect (Teleconsultation bridge)
 *  J. Emergency Assistance (Preserved multi-channel SOS emergency dispatch)
 */
public class MedicationSafetyHomeFragment extends Fragment {

    // ─── View References ──────────────────────────────────────────────────────

    // Header & Welcome
    private View brandContainer, btnNotification, notifDot, avatarContainer;
    private TextView tvGreeting, tvUserName, tvAvatarInitials, tvCurrentDate, tvBrandTagline;

    // Search & AI
    private View searchSection, searchBar, btnMicSearch, btnAskAi;
    private TextView tvSearchPlaceholder;

    // Retina Intelligence Hero
    private View heroRetinaCard, pillFeatQuality, pillFeatGrading, pillFeatSegmentation;
    private View btnStartRetinalScreening, btnViewPreviousScreenings;
    private ImageView ivRetinaHeroGraphic;

    // Diabetes360 Metric Cards
    private View cardGlucoseMetric, cardHeartMetric, cardHba1cMetric, cardKidneyMetric;
    private TextView tvGlucoseValue, tvGlucoseTimestamp, tvGlucoseAction;
    private TextView tvHeartValue, tvHeartTimestamp, tvHeartAction;
    private TextView tvHba1cValue, tvHba1cTimestamp, tvHba1cAction;
    private TextView tvKidneyValue, tvKidneyTimestamp, tvKidneyAction;

    // Health Quick Actions
    private View btnQuickScreening, btnQuickUploadReport, btnQuickLogGlucose, btnQuickHealthPassport;

    // Activity & Reminders
    private LinearLayout layoutActivityTimeline, layoutActivityEmpty;
    private View btnEmptyUploadRecord;
    private LinearLayout layoutRemindersContainer, layoutRemindersEmpty;

    // Health Insights & Netra AI
    private View cardHealthInsights;
    private TextView tvInsightContent, btnViewInsightEvidence, btnAskNetraAiInsight;

    // Doctor Connect
    private View cardDoctorConnect, btnConnectDoctor;

    // Emergency Assistance
    private View cardEmergencyAssistance, btnTriggerEmergency, btnEmergencyContacts;

    // ─── State & Utilities ────────────────────────────────────────────────────

    private static final int REQUEST_CAMERA_RETINA = 3001;
    private static final int REQUEST_GALLERY_RETINA = 3002;
    private static final int REQUEST_SPEECH_INPUT = 3003;
    private Uri currentPhotoUri;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String[] CLINICAL_INSIGHTS = {
        "Annual Retinal Screening Protocol: Early detection of microaneurysms and hard exudates prevents over 95% of diabetes-related severe vision impairment.",
        "Glycemic Variability Insight: Maintaining time-in-range (70–180 mg/dL) significantly slows progression of diabetic retinopathy and peripheral neuropathy.",
        "Renal & Eye Microvascular Link: Glomerular and retinal vessels share identical basement membranes. An abnormal urine albumin often co-occurs with early retinopathy.",
        "HbA1c Target Advisory: Each 1% reduction in HbA1c decreases risk of microvascular diabetes complications by up to 37% (UKPDS Clinical Trial)."
    };
    private int currentInsightIndex = 0;
    private final Handler insightHandler = new Handler(Looper.getMainLooper());
    private Runnable insightRotator;

    private final BroadcastReceiver scheduleUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadDashboardData();
        }
    };

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home_dashboard, container, false);
        bindViews(root);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupClickListeners();
        loadGreetingAndDate();
        loadDashboardData();
        startInsightRotation();
    }

    @Override
    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(scheduleUpdatedReceiver,
                new IntentFilter(ActionReceiver.ACTION_SCHEDULE_UPDATED));
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(scheduleUpdatedReceiver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        insightHandler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacksAndMessages(null);
    }

    // ─── View Binding ─────────────────────────────────────────────────────────

    private void bindViews(View root) {
        // Section A: Header & Welcome
        brandContainer   = root.findViewById(R.id.brandContainer);
        btnNotification  = root.findViewById(R.id.btnNotification);
        notifDot         = root.findViewById(R.id.notifDot);
        avatarContainer  = root.findViewById(R.id.avatarContainer);
        tvGreeting       = root.findViewById(R.id.tvGreeting);
        tvUserName       = root.findViewById(R.id.tvUserName);
        tvAvatarInitials = root.findViewById(R.id.tvAvatarInitials);
        tvCurrentDate    = root.findViewById(R.id.tvCurrentDate);
        tvBrandTagline   = root.findViewById(R.id.tvBrandTagline);

        // Section B: Search & AI
        searchSection       = root.findViewById(R.id.searchSection);
        searchBar           = root.findViewById(R.id.searchBar);
        tvSearchPlaceholder = root.findViewById(R.id.tvSearchPlaceholder);
        btnMicSearch        = root.findViewById(R.id.btnMicSearch);
        btnAskAi            = root.findViewById(R.id.btnAskAi);

        // Section C: Retina Intelligence Hero
        heroRetinaCard             = root.findViewById(R.id.heroRetinaCard);
        pillFeatQuality            = root.findViewById(R.id.pillFeatQuality);
        pillFeatGrading            = root.findViewById(R.id.pillFeatGrading);
        pillFeatSegmentation       = root.findViewById(R.id.pillFeatSegmentation);
        btnStartRetinalScreening   = root.findViewById(R.id.btnStartRetinalScreening);
        btnViewPreviousScreenings  = root.findViewById(R.id.btnViewPreviousScreenings);
        ivRetinaHeroGraphic        = root.findViewById(R.id.ivRetinaHeroGraphic);

        // Section D: Diabetes360 Metric Cards
        cardGlucoseMetric  = root.findViewById(R.id.cardGlucoseMetric);
        tvGlucoseValue     = root.findViewById(R.id.tvGlucoseValue);
        tvGlucoseTimestamp = root.findViewById(R.id.tvGlucoseTimestamp);
        tvGlucoseAction    = root.findViewById(R.id.tvGlucoseAction);

        cardHeartMetric    = root.findViewById(R.id.cardHeartMetric);
        tvHeartValue       = root.findViewById(R.id.tvHeartValue);
        tvHeartTimestamp   = root.findViewById(R.id.tvHeartTimestamp);
        tvHeartAction      = root.findViewById(R.id.tvHeartAction);

        cardHba1cMetric    = root.findViewById(R.id.cardHba1cMetric);
        tvHba1cValue       = root.findViewById(R.id.tvHba1cValue);
        tvHba1cTimestamp   = root.findViewById(R.id.tvHba1cTimestamp);
        tvHba1cAction      = root.findViewById(R.id.tvHba1cAction);

        cardKidneyMetric   = root.findViewById(R.id.cardKidneyMetric);
        tvKidneyValue      = root.findViewById(R.id.tvKidneyValue);
        tvKidneyTimestamp  = root.findViewById(R.id.tvKidneyTimestamp);
        tvKidneyAction     = root.findViewById(R.id.tvKidneyAction);

        // Section E: Health Quick Actions
        btnQuickScreening      = root.findViewById(R.id.btnQuickScreening);
        btnQuickUploadReport   = root.findViewById(R.id.btnQuickUploadReport);
        btnQuickLogGlucose     = root.findViewById(R.id.btnQuickLogGlucose);
        btnQuickHealthPassport = root.findViewById(R.id.btnQuickHealthPassport);

        View btnOpenDiabetes360Dashboard = root.findViewById(R.id.btnOpenDiabetes360Dashboard);
        if (btnOpenDiabetes360Dashboard != null) {
            btnOpenDiabetes360Dashboard.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), Diabetes360DashboardActivity.class));
            });
        }

        // Section F & G: Activity & Reminders
        layoutActivityTimeline   = root.findViewById(R.id.layoutActivityTimeline);
        layoutActivityEmpty      = root.findViewById(R.id.layoutActivityEmpty);
        btnEmptyUploadRecord     = root.findViewById(R.id.btnEmptyUploadRecord);
        layoutRemindersContainer = root.findViewById(R.id.layoutRemindersContainer);
        layoutRemindersEmpty     = root.findViewById(R.id.layoutRemindersEmpty);

        // Section H: Health Insights
        cardHealthInsights     = root.findViewById(R.id.cardHealthInsights);
        tvInsightContent       = root.findViewById(R.id.tvInsightContent);
        btnViewInsightEvidence = root.findViewById(R.id.btnViewInsightEvidence);
        btnAskNetraAiInsight   = root.findViewById(R.id.btnAskNetraAiInsight);

        // Section I: Doctor Connect
        cardDoctorConnect = root.findViewById(R.id.cardDoctorConnect);
        btnConnectDoctor  = root.findViewById(R.id.btnConnectDoctor);

        // Section J: Emergency Assistance
        cardEmergencyAssistance = root.findViewById(R.id.cardEmergencyAssistance);
        btnTriggerEmergency     = root.findViewById(R.id.btnTriggerEmergency);
        btnEmergencyContacts    = root.findViewById(R.id.btnEmergencyContacts);
    }

    // ─── Setup Click Listeners ────────────────────────────────────────────────

    private void setupClickListeners() {
        // Section A: Header Actions
        if (brandContainer != null) {
            brandContainer.setOnClickListener(v -> {
                View scroll = getView() != null ? getView().findViewById(R.id.homeScrollView) : null;
                if (scroll != null) scroll.scrollTo(0, 0);
                Toast.makeText(requireContext(), "NetraProof Dashboard refreshed", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnNotification != null) {
            btnNotification.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), NotificationsRemindersActivity.class));
            });
        }

        if (avatarContainer != null) {
            avatarContainer.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), EditProfileActivity.class));
            });
        }

        // Section B: Search & Netra AI
        if (searchBar != null) {
            searchBar.setOnClickListener(v -> showUnifiedSearchDialog());
        }

        if (btnMicSearch != null) {
            btnMicSearch.setOnClickListener(v -> startVoiceRecognition());
        }

        if (btnAskAi != null) {
            btnAskAi.setOnClickListener(v -> openNetraAi(null));
        }

        // Section C: Retina Intelligence Primary Hero -> Launches RetinaScreeningActivity
        if (btnStartRetinalScreening != null) {
            btnStartRetinalScreening.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), RetinaScreeningActivity.class));
            });
        }

        if (btnViewPreviousScreenings != null) {
            btnViewPreviousScreenings.setOnClickListener(v -> openScreeningHistory());
        }

        if (pillFeatQuality != null) {
            pillFeatQuality.setOnClickListener(v -> showFeatureInfo(
                "Image Quality Assessment",
                "Automated real-time ocular quality filter. Checks illumination, field centration, focus sharpness, and lens artifacts before passing images to the diagnostic model."
            ));
        }

        if (pillFeatGrading != null) {
            pillFeatGrading.setOnClickListener(v -> showFeatureInfo(
                "Diabetic Retinopathy Severity Grading",
                "Five-tier clinical staging based on the International Clinical Diabetic Retinopathy (ICDR) scale:\n\n• Grade 0: No Apparent Retinopathy\n• Grade 1: Mild NPDR (Microaneurysms only)\n• Grade 2: Moderate NPDR (Microaneurysms, hemorrhages, hard exudates)\n• Grade 3: Severe NPDR (4-2-1 Rule)\n• Grade 4: Proliferative DR (Neovascularization)"
            ));
        }

        if (pillFeatSegmentation != null) {
            pillFeatSegmentation.setOnClickListener(v -> showFeatureInfo(
                "Retinal Lesion Segmentation",
                "Deep learning pixel-level spatial masks outlining microaneurysms, intraretinal hemorrhages, hard exudates, and cotton wool spots with clinical explainability overlays."
            ));
        }

        // Section D: Diabetes360 Cards
        if (cardGlucoseMetric != null) {
            cardGlucoseMetric.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), GlucoseHba1cTrackingActivity.class));
            });
        }

        if (cardHeartMetric != null) {
            cardHeartMetric.setOnClickListener(v -> openHeartHealth());
        }

        if (cardHba1cMetric != null) {
            cardHba1cMetric.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), GlucoseHba1cTrackingActivity.class));
            });
        }

        if (cardKidneyMetric != null) {
            cardKidneyMetric.setOnClickListener(v -> openKidneyHealth());
        }

        // Section E: Health Quick Actions -> Launches RetinaScreeningActivity
        if (btnQuickScreening != null) {
            btnQuickScreening.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), RetinaScreeningActivity.class));
            });
        }

        if (btnQuickUploadReport != null) {
            btnQuickUploadReport.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), DocumentUploadActivity.class));
            });
        }

        if (btnQuickLogGlucose != null) {
            btnQuickLogGlucose.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), GlucoseHba1cTrackingActivity.class));
            });
        }

        if (btnQuickHealthPassport != null) {
            btnQuickHealthPassport.setOnClickListener(v -> {
                // Replaced Health Passport with Page 06 Retinal Screening Report per user request
                startActivity(new Intent(requireContext(), RetinaScreeningReportActivity.class));
            });
        }

        // Section F: Recent Activity Empty CTA
        if (btnEmptyUploadRecord != null) {
            btnEmptyUploadRecord.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), DocumentUploadActivity.class));
            });
        }

        // Section H: Insights Actions
        if (btnViewInsightEvidence != null) {
            btnViewInsightEvidence.setOnClickListener(v -> showClinicalEvidenceDialog());
        }

        if (btnAskNetraAiInsight != null) {
            btnAskNetraAiInsight.setOnClickListener(v -> {
                String question = tvInsightContent != null ? tvInsightContent.getText().toString() : "Explain diabetic microvascular screening";
                openNetraAi(question);
            });
        }

        // Section I: Doctor Connect
        if (btnConnectDoctor != null) {
            btnConnectDoctor.setOnClickListener(v -> {
                // Switch to TeleconsultationFragment
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.fragment_container, new TeleconsultationFragment())
                        .addToBackStack("teleconsult")
                        .commit();
                }
            });
        }

        // Section J: Emergency Assistance
        if (btnTriggerEmergency != null) {
            btnTriggerEmergency.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                    .setTitle("Activate Emergency SOS?")
                    .setMessage("This will immediately broadcast your GPS coordinates to your registered emergency contacts and initiate ambulance response protocol.")
                    .setPositiveButton("ACTIVATE NOW", (dialog, which) -> {
                        EmergencyDispatcher.triggerEmergency(requireContext(), "MANUAL_SOS");
                        startActivity(new Intent(requireContext(), EmergencyActiveActivity.class));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }

        if (btnEmergencyContacts != null) {
            btnEmergencyContacts.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), ManageEmergencyContactsActivity.class));
            });
        }
    }

    // ─── Data Loading & State Management ──────────────────────────────────────

    private void loadGreetingAndDate() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        if (tvGreeting != null) {
            if (hour >= 5 && hour < 12) {
                tvGreeting.setText(R.string.header_greeting_morning);
            } else if (hour >= 12 && hour < 17) {
                tvGreeting.setText(R.string.header_greeting_afternoon);
            } else {
                tvGreeting.setText(R.string.header_greeting_evening);
            }
        }

        if (tvCurrentDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());
            tvCurrentDate.setText(sdf.format(new Date()));
        }
    }

    private void loadDashboardData() {
        String uuid = TokenManager.getUUID(requireContext());

        new Thread(() -> {
            AppDatabase db = AppDatabaseProvider.getInstance(requireContext());

            // 1. Load Patient Profile Name
            PatientEntity patient = null;
            try {
                patient = db.patientDao().getPatient(uuid);
            } catch (Exception ignored) {}

            final PatientEntity finalPatient = patient;

            // 2. Load Recent Health Documents
            List<HealthDocumentEntity> documents = new ArrayList<>();
            try {
                documents = db.healthDocumentDao().getDocumentsForPatient(uuid);
            } catch (Exception ignored) {}
            final List<HealthDocumentEntity> finalDocs = documents != null ? documents : new ArrayList<>();

            // 3. Load Pending Medication Schedules for Today
            List<MedicationScheduleEntity> pendingSchedules = new ArrayList<>();
            try {
                db.scheduleDao().markPendingBeforeAsMissed(System.currentTimeMillis());
                List<MedicationScheduleEntity> all = db.scheduleDao().getAll();
                if (all != null) {
                    Calendar calStart = Calendar.getInstance();
                    calStart.set(Calendar.HOUR_OF_DAY, 0);
                    calStart.set(Calendar.MINUTE, 0);
                    calStart.set(Calendar.SECOND, 0);
                    calStart.set(Calendar.MILLISECOND, 0);
                    long startToday = calStart.getTimeInMillis();

                    Calendar calEnd = Calendar.getInstance();
                    calEnd.set(Calendar.HOUR_OF_DAY, 23);
                    calEnd.set(Calendar.MINUTE, 59);
                    calEnd.set(Calendar.SECOND, 59);
                    calEnd.set(Calendar.MILLISECOND, 999);
                    long endToday = calEnd.getTimeInMillis();

                    for (MedicationScheduleEntity s : all) {
                        if ("PENDING".equalsIgnoreCase(s.status) && s.timeMillis >= startToday && s.timeMillis <= endToday) {
                            pendingSchedules.add(s);
                        }
                    }
                }
            } catch (Exception ignored) {}
            final List<MedicationScheduleEntity> finalSchedules = pendingSchedules;

            // 4. Load Stored Glucose Reading from SharedPreferences
            android.content.SharedPreferences prefs = requireContext().getSharedPreferences("netra_health_metrics", Context.MODE_PRIVATE);
            String savedGlucose = prefs.getString("latest_glucose_value", null);
            String savedGlucoseTime = prefs.getString("latest_glucose_timestamp", null);

            mainHandler.post(() -> {
                if (!isAdded()) return;

                // Update Patient Name & Initials
                if (finalPatient != null && finalPatient.fullName != null && !finalPatient.fullName.trim().isEmpty()) {
                    String name = finalPatient.fullName.trim();
                    if (tvUserName != null) tvUserName.setText(name);

                    if (tvAvatarInitials != null) {
                        String[] parts = name.split("\\s+");
                        if (parts.length >= 2 && parts[0].length() > 0 && parts[1].length() > 0) {
                            tvAvatarInitials.setText(("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase());
                        } else if (parts.length >= 1 && parts[0].length() > 0) {
                            tvAvatarInitials.setText(("" + parts[0].charAt(0)).toUpperCase());
                        }
                    }
                } else {
                    if (tvUserName != null) tvUserName.setText("Patient");
                    if (tvAvatarInitials != null) tvAvatarInitials.setText("NP");
                }

                // Update Glucose Metric Card (Data Integrity: Only show real data)
                if (savedGlucose != null && !savedGlucose.isEmpty()) {
                    if (tvGlucoseValue != null) tvGlucoseValue.setText(savedGlucose + " mg/dL");
                    if (tvGlucoseTimestamp != null) tvGlucoseTimestamp.setText(savedGlucoseTime != null ? savedGlucoseTime : "Recent");
                    if (tvGlucoseAction != null) tvGlucoseAction.setText("Log / Trends →");
                } else {
                    if (tvGlucoseValue != null) tvGlucoseValue.setText("--");
                    if (tvGlucoseTimestamp != null) tvGlucoseTimestamp.setText(R.string.no_records_yet);
                    if (tvGlucoseAction != null) tvGlucoseAction.setText("+ Log Glucose →");
                }

                // Update Heart & Vascular Metric Card
                String savedHeart = prefs.getString("latest_heart_value", null);
                String savedHeartTime = prefs.getString("latest_heart_timestamp", null);
                if (savedHeart != null && !savedHeart.isEmpty()) {
                    if (tvHeartValue != null) tvHeartValue.setText(savedHeart);
                    if (tvHeartTimestamp != null) tvHeartTimestamp.setText(savedHeartTime != null ? savedHeartTime : "Recent");
                    if (tvHeartAction != null) tvHeartAction.setText("View Cardio →");
                } else {
                    if (tvHeartValue != null) tvHeartValue.setText("--");
                    if (tvHeartTimestamp != null) tvHeartTimestamp.setText(R.string.no_records_yet);
                    if (tvHeartAction != null) tvHeartAction.setText("+ Log BP →");
                }

                // Update Kidney Metric Card
                String savedKidney = prefs.getString("latest_kidney_value", null);
                String savedKidneyTime = prefs.getString("latest_kidney_timestamp", null);
                if (savedKidney != null && !savedKidney.isEmpty()) {
                    if (tvKidneyValue != null) tvKidneyValue.setText(savedKidney);
                    if (tvKidneyTimestamp != null) tvKidneyTimestamp.setText(savedKidneyTime != null ? savedKidneyTime : "Recent");
                    if (tvKidneyAction != null) tvKidneyAction.setText("View Renal →");
                } else {
                    if (tvKidneyValue != null) tvKidneyValue.setText("--");
                    if (tvKidneyTimestamp != null) tvKidneyTimestamp.setText(R.string.no_records_yet);
                    if (tvKidneyAction != null) tvKidneyAction.setText("+ Add Lab →");
                }

                // Update Timeline Activity
                populateActivityTimeline(finalDocs);

                // Update Reminders Container
                populateRemindersContainer(finalSchedules);
            });
        }).start();
    }

    private void populateActivityTimeline(List<HealthDocumentEntity> documents) {
        if (layoutActivityTimeline == null) return;
        layoutActivityTimeline.removeAllViews();

        if (documents == null || documents.isEmpty()) {
            if (layoutActivityEmpty != null) layoutActivityTimeline.addView(layoutActivityEmpty);
            return;
        }

        // Show up to 3 most recent documents
        int count = Math.min(documents.size(), 3);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

        for (int i = 0; i < count; i++) {
            HealthDocumentEntity doc = documents.get(i);
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_dashboard_activity_row, layoutActivityTimeline, false);

            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvTime = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);
            ImageView ivIcon = row.findViewById(R.id.ivActivityRowIcon);

            if (tvTitle != null) tvTitle.setText(doc.displayName != null ? doc.displayName : "Health Report");
            if (tvTime != null) tvTime.setText(sdf.format(new Date(doc.uploadTimestamp)));
            if (tvStatus != null) {
                tvStatus.setText("success".equalsIgnoreCase(doc.extractionStatus) ? "Verified" : "Processed");
                tvStatus.setTextColor(android.graphics.Color.parseColor("#25865A"));
            }
            if (ivIcon != null) {
                ivIcon.setImageResource(R.drawable.ic_action_upload);
            }

            row.setOnClickListener(v -> {
                // View report details or prompt user
                Toast.makeText(requireContext(), "Document: " + doc.displayName, Toast.LENGTH_SHORT).show();
            });

            layoutActivityTimeline.addView(row);
        }
    }

    private void populateRemindersContainer(List<MedicationScheduleEntity> schedules) {
        if (layoutRemindersContainer == null) return;
        layoutRemindersContainer.removeAllViews();

        if (schedules == null || schedules.isEmpty()) {
            if (layoutRemindersEmpty != null) layoutRemindersContainer.addView(layoutRemindersEmpty);
            return;
        }

        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        int count = Math.min(schedules.size(), 3);

        for (int i = 0; i < count; i++) {
            MedicationScheduleEntity item = schedules.get(i);
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_dashboard_activity_row, layoutRemindersContainer, false);

            TextView tvTitle = row.findViewById(R.id.tvActivityRowTitle);
            TextView tvTime = row.findViewById(R.id.tvActivityRowTime);
            TextView tvStatus = row.findViewById(R.id.tvActivityRowStatus);
            ImageView ivIcon = row.findViewById(R.id.ivActivityRowIcon);

            if (tvTitle != null) tvTitle.setText(item.drugName != null ? item.drugName : "Scheduled Medication");
            if (tvTime != null) tvTime.setText("Scheduled: " + timeFormat.format(new Date(item.timeMillis)));
            if (tvStatus != null) {
                tvStatus.setText("Take Dose");
                tvStatus.setTextColor(android.graphics.Color.parseColor("#147D82"));
            }
            if (ivIcon != null) {
                ivIcon.setImageResource(R.drawable.ic_nav_medication);
            }

            row.setOnClickListener(v -> {
                // Mark dose as taken in background
                new Thread(() -> {
                    item.status = "TAKEN";
                    AppDatabaseProvider.getInstance(requireContext()).scheduleDao().update(item);
                    mainHandler.post(() -> {
                        Toast.makeText(requireContext(), "Marked " + item.drugName + " as taken", Toast.LENGTH_SHORT).show();
                        loadDashboardData();
                    });
                }).start();
            });

            layoutRemindersContainer.addView(row);
        }
    }

    // ─── Interactive Dialogs & Navigation Actions ─────────────────────────────

    private void openScreeningHistory() {
        new AlertDialog.Builder(requireContext())
            .setTitle("Screening History")
            .setMessage("No previous retinal screenings recorded on this device yet. Start your first AI retinal screening to establish your baseline ocular score.")
            .setPositiveButton("Start Screening Now", (dialog, which) -> {
                startActivity(new Intent(requireContext(), RetinaScreeningActivity.class));
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void showLogGlucoseDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Log Blood Glucose");

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 12);

        final RadioGroup rgType = new RadioGroup(requireContext());
        android.widget.RadioButton rbFasting = new android.widget.RadioButton(requireContext());
        rbFasting.setText("Fasting (Before Breakfast)");
        rbFasting.setChecked(true);
        android.widget.RadioButton rbPost = new android.widget.RadioButton(requireContext());
        rbPost.setText("Postprandial (2h After Meal)");
        rgType.addView(rbFasting);
        rgType.addView(rbPost);
        layout.addView(rgType);

        final EditText etReading = new EditText(requireContext());
        etReading.setHint("e.g. 110 mg/dL");
        etReading.setInputType(InputType.TYPE_CLASS_NUMBER);
        etReading.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(etReading);

        builder.setView(layout);

        builder.setPositiveButton("Save Reading", (dialog, which) -> {
            String val = etReading.getText().toString().trim();
            if (val.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a valid glucose number", Toast.LENGTH_SHORT).show();
                return;
            }

            String type = rbFasting.isChecked() ? "Fasting" : "Postprandial";
            String timestamp = new SimpleDateFormat("Today, hh:mm a", Locale.getDefault()).format(new Date());

            // Save to SharedPreferences for fast, persistent access
            requireContext().getSharedPreferences("netra_health_metrics", Context.MODE_PRIVATE)
                .edit()
                .putString("latest_glucose_value", val)
                .putString("latest_glucose_type", type)
                .putString("latest_glucose_timestamp", timestamp + " (" + type + ")")
                .apply();

            Toast.makeText(requireContext(), "Glucose logged: " + val + " mg/dL (" + type + ")", Toast.LENGTH_SHORT).show();
            loadDashboardData();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void openHeartHealth() {
        startActivity(new Intent(requireContext(), HeartHealthActivity.class));
    }

    private void openKidneyHealth() {
        startActivity(new Intent(requireContext(), KidneyHealthActivity.class));
    }

    private void showNotificationDialog() {
        new AlertDialog.Builder(requireContext())
            .setTitle("Notifications & System Alerts")
            .setMessage("• AI Retinal Triage Engine: v2.4 initialized\n• Annual Eye Exam: Due within 30 days\n• Fall Guard Sensor: Active & Monitoring\n• Cloud Sync: Connected")
            .setPositiveButton("Dismiss All", (d, w) -> {
                if (notifDot != null) notifDot.setVisibility(View.GONE);
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void showUnifiedSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Search Health Records");

        final EditText input = new EditText(requireContext());
        input.setHint("Enter medication, symptom, or lab test…");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(48, 16, 48, 16);
        input.setLayoutParams(lp);

        builder.setView(input);
        builder.setPositiveButton("Search", (dialog, which) -> {
            String query = input.getText().toString().trim();
            if (!query.isEmpty()) {
                openNetraAi("Search records for: " + query);
            }
        });
        builder.setNeutralButton("Open Health Documents", (dialog, which) -> {
            startActivity(new Intent(requireContext(), DocumentUploadActivity.class));
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Netra AI about your diabetes or eye health…");
        try {
            startActivityForResult(intent, REQUEST_SPEECH_INPUT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "Voice input not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void openNetraAi(@Nullable String initialQuery) {
        Intent intent = new Intent(requireContext(), NetraAiAssistantActivity.class);
        if (initialQuery != null && !initialQuery.isEmpty()) {
            intent.putExtra("initial_query", initialQuery);
        }
        startActivity(intent);
    }

    private void showFeatureInfo(String title, String message) {
        new AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show();
    }

    private void showClinicalEvidenceDialog() {
        new AlertDialog.Builder(requireContext())
            .setTitle("Clinical Evidence & Guidelines")
            .setMessage(
                "• American Diabetes Association (ADA) Standards of Care in Diabetes — 2026: Recommends annual screening for diabetic retinopathy beginning at diagnosis for type 2 diabetes.\n\n" +
                "• International Council of Ophthalmology (ICO) Guidelines: Validates automated AI triage algorithms when sensitivity for referable DR exceeds 90%.\n\n" +
                "• UK Prospective Diabetes Study (UKPDS): Proves tight glycemic control (HbA1c < 7.0%) prevents irreversible retinal microvascular damage."
            )
            .setPositiveButton("Understood", null)
            .show();
    }

    private void startInsightRotation() {
        insightRotator = new Runnable() {
            @Override
            public void run() {
                if (tvInsightContent != null && isAdded()) {
                    currentInsightIndex = (currentInsightIndex + 1) % CLINICAL_INSIGHTS.length;
                    tvInsightContent.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                        if (tvInsightContent != null) {
                            tvInsightContent.setText(CLINICAL_INSIGHTS[currentInsightIndex]);
                            tvInsightContent.animate().alpha(1f).setDuration(300).start();
                        }
                    }).start();
                }
                insightHandler.postDelayed(this, 12000); // Rotate every 12 seconds
            }
        };
        insightHandler.postDelayed(insightRotator, 12000);
    }
}
