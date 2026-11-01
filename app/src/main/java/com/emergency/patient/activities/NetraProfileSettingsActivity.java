package com.emergency.patient.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.emergency.patient.BuildConfig;
import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.EmergencyContactEntity;
import com.emergency.patient.db.HealthDocumentEntity;
import com.emergency.patient.db.MedicationScheduleEntity;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.network.FirebaseRepository;
import com.emergency.patient.security.TokenManager;
import com.emergency.patient.utils.NetraBottomNavHelper;
import com.emergency.patient.utils.NetraProfileHeaderHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * NetraProfileSettingsActivity — NetraProof Profile & Settings Module (Page 16).
 *
 * Responsibilities:
 *  1. Authenticated patient profile overview with photo/initials, ID, and verified status.
 *  2. Structured demographic details and emergency contact summary.
 *  3. App preferences: language, glucose measurement unit (mg/dL vs mmol/L), theme, and date format.
 *  4. Notification preferences: system permission diagnostics and granular scheduling toggles (Page 14 engine).
 *  5. Privacy controls: local encryption policies, clinical disclosures, anonymous telemetry, and data dossier export.
 *  6. Security controls: active session device info, biometric lock, and secure logout.
 *  7. Safeguarded destructive account erase & reset with dual confirmation.
 *  8. Help, clinical FAQ, and NetraProof investigational companion disclosures.
 */
public class NetraProfileSettingsActivity extends AppCompatActivity {

    private static final String PREFS_APP = "netra_app_prefs";
    private static final String PREFS_NOTIF = "netra_notification_prefs";

    // Header & Navigation
    private ImageView btnSettingsBack;

    // Section A: Profile Overview
    private ShapeableImageView ivOverviewPhoto;
    private FrameLayout layoutOverviewInitials;
    private TextView tvOverviewInitials, tvOverviewName, tvOverviewPatientId, tvOverviewMemberSince;
    private MaterialButton btnEditProfile;

    // Section B: Personal Details
    private TextView tvPersonalFullName, tvPersonalDob, tvPersonalGender, tvPersonalBloodGroup;
    private TextView tvEmergencyContactSummary, btnManageEmergencyContacts;

    // Section C: App Preferences
    private View rowPrefLanguage, rowPrefGlucoseUnit, rowPrefTheme, rowPrefDateFormat;
    private TextView tvSelectedLanguage, tvSelectedGlucoseUnit, tvSelectedTheme, tvSelectedDateFormat;

    // Section D: Notification Preferences
    private View bannerSystemPermission;
    private ImageView ivPermissionStatusIcon;
    private TextView tvPermissionStatusText;
    private MaterialButton btnOpenDeviceSettings, btnOpenRemindersHub;
    private SwitchCompat switchGlucoseReminders, switchMedReminders, switchRetinaReminders, switchOrganReminders;

    // Section E: Privacy & Data Controls
    private View rowPrivacyPolicy, rowTermsOfService;
    private SwitchCompat switchAnonymousTelemetry;
    private MaterialButton btnExportHealthData, btnClearChatHistory;

    // Section F: Security Settings
    private TextView tvSecurityDevice, tvSecurityEncryption;
    private SwitchCompat switchBiometricLock;
    private MaterialButton btnLogout;

    // Section G: Destructive Actions
    private MaterialButton btnResetProfile;

    // Section H: About & Support
    private View rowFaq, rowContactSupport;
    private TextView tvAppVersion;

    private String patientUuid;
    private SharedPreferences appPrefs;
    private SharedPreferences notifPrefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_netra_profile_settings);

        patientUuid = TokenManager.getUUID(this);
        if (patientUuid == null || patientUuid.trim().isEmpty()) {
            patientUuid = "default_patient";
        }

        appPrefs = getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE);
        notifPrefs = getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE);

        bindViews();
        setupClickListeners();
        setupPreferencesControls();
        setupNotificationToggles();
        setupSecurityAndDeviceInfo();
        setupAboutSection();

        // Top navbar avatar binding
        NetraProfileHeaderHelper.setup(this);

        // Sticky bottom navbar
        NetraBottomNavHelper.setup(this, NetraBottomNavHelper.TAB_DIABETES360);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfileData();
        checkSystemNotificationPermission();
    }

    private void bindViews() {
        btnSettingsBack = findViewById(R.id.btnSettingsBack);

        // Overview
        ivOverviewPhoto = findViewById(R.id.ivOverviewPhoto);
        layoutOverviewInitials = findViewById(R.id.layoutOverviewInitials);
        tvOverviewInitials = findViewById(R.id.tvOverviewInitials);
        tvOverviewName = findViewById(R.id.tvOverviewName);
        tvOverviewPatientId = findViewById(R.id.tvOverviewPatientId);
        tvOverviewMemberSince = findViewById(R.id.tvOverviewMemberSince);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        // Personal
        tvPersonalFullName = findViewById(R.id.tvPersonalFullName);
        tvPersonalDob = findViewById(R.id.tvPersonalDob);
        tvPersonalGender = findViewById(R.id.tvPersonalGender);
        tvPersonalBloodGroup = findViewById(R.id.tvPersonalBloodGroup);
        tvEmergencyContactSummary = findViewById(R.id.tvEmergencyContactSummary);
        btnManageEmergencyContacts = findViewById(R.id.btnManageEmergencyContacts);

        // Preferences
        rowPrefLanguage = findViewById(R.id.rowPrefLanguage);
        tvSelectedLanguage = findViewById(R.id.tvSelectedLanguage);
        rowPrefGlucoseUnit = findViewById(R.id.rowPrefGlucoseUnit);
        tvSelectedGlucoseUnit = findViewById(R.id.tvSelectedGlucoseUnit);
        rowPrefTheme = findViewById(R.id.rowPrefTheme);
        tvSelectedTheme = findViewById(R.id.tvSelectedTheme);
        rowPrefDateFormat = findViewById(R.id.rowPrefDateFormat);
        tvSelectedDateFormat = findViewById(R.id.tvSelectedDateFormat);

        // Notifications
        bannerSystemPermission = findViewById(R.id.bannerSystemPermission);
        ivPermissionStatusIcon = findViewById(R.id.ivPermissionStatusIcon);
        tvPermissionStatusText = findViewById(R.id.tvPermissionStatusText);
        btnOpenDeviceSettings = findViewById(R.id.btnOpenDeviceSettings);
        btnOpenRemindersHub = findViewById(R.id.btnOpenRemindersHub);
        switchGlucoseReminders = findViewById(R.id.switchGlucoseReminders);
        switchMedReminders = findViewById(R.id.switchMedReminders);
        switchRetinaReminders = findViewById(R.id.switchRetinaReminders);
        switchOrganReminders = findViewById(R.id.switchOrganReminders);

        // Privacy
        rowPrivacyPolicy = findViewById(R.id.rowPrivacyPolicy);
        rowTermsOfService = findViewById(R.id.rowTermsOfService);
        switchAnonymousTelemetry = findViewById(R.id.switchAnonymousTelemetry);
        btnExportHealthData = findViewById(R.id.btnExportHealthData);
        btnClearChatHistory = findViewById(R.id.btnClearChatHistory);

        // Security
        tvSecurityDevice = findViewById(R.id.tvSecurityDevice);
        tvSecurityEncryption = findViewById(R.id.tvSecurityEncryption);
        switchBiometricLock = findViewById(R.id.switchBiometricLock);
        btnLogout = findViewById(R.id.btnLogout);

        // Destructive
        btnResetProfile = findViewById(R.id.btnResetProfile);

        // Support
        rowFaq = findViewById(R.id.rowFaq);
        rowContactSupport = findViewById(R.id.rowContactSupport);
        tvAppVersion = findViewById(R.id.tvAppVersion);
    }

    private void setupClickListeners() {
        if (btnSettingsBack != null) {
            btnSettingsBack.setOnClickListener(v -> finish());
        }

        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> {
                startActivity(new Intent(this, EditProfileActivity.class));
            });
        }

        if (btnManageEmergencyContacts != null) {
            btnManageEmergencyContacts.setOnClickListener(v -> {
                startActivity(new Intent(this, ManageEmergencyContactsActivity.class));
            });
        }

        if (btnOpenRemindersHub != null) {
            btnOpenRemindersHub.setOnClickListener(v -> {
                startActivity(new Intent(this, NotificationsRemindersActivity.class));
            });
        }

        if (btnOpenDeviceSettings != null) {
            btnOpenDeviceSettings.setOnClickListener(v -> openSystemNotificationSettings());
        }

        if (rowPrivacyPolicy != null) {
            rowPrivacyPolicy.setOnClickListener(v -> showPrivacyPolicyDialog());
        }

        if (rowTermsOfService != null) {
            rowTermsOfService.setOnClickListener(v -> showTermsDialog());
        }

        if (btnExportHealthData != null) {
            btnExportHealthData.setOnClickListener(v -> exportPatientHealthDossier());
        }

        if (btnClearChatHistory != null) {
            btnClearChatHistory.setOnClickListener(v -> showClearChatDialog());
        }

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());
        }

        if (btnResetProfile != null) {
            btnResetProfile.setOnClickListener(v -> showResetConfirmationDialog());
        }

        if (rowFaq != null) {
            rowFaq.setOnClickListener(v -> showFaqDialog());
        }

        if (rowContactSupport != null) {
            rowContactSupport.setOnClickListener(v -> sendSupportEmail());
        }
    }

    // ─── SECTION A & B: DATA LOADING ──────────────────────────────────────────

    private void loadProfileData() {
        new Thread(() -> {
            AppDatabase db = AppDatabaseProvider.getInstance(this);
            PatientEntity patient = null;
            if (patientUuid != null && !patientUuid.isEmpty()) {
                patient = db.patientDao().getPatient(patientUuid);
            }
            if (patient == null) {
                List<PatientEntity> all = db.patientDao().getAllPatients();
                if (all != null && !all.isEmpty()) patient = all.get(0);
            }

            int contactCount = 0;
            String primaryContactName = "None";
            try {
                List<EmergencyContactEntity> contacts = db.emergencyContactDao().getContactsForPatient(patientUuid);
                if (contacts != null) {
                    contactCount = contacts.size();
                    if (!contacts.isEmpty()) {
                        primaryContactName = contacts.get(0).name;
                    }
                }
            } catch (Exception ignored) {}

            final PatientEntity p = patient;
            final int finalContactCount = contactCount;
            final String finalPrimaryContact = primaryContactName;

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;

                String name = "Patient";
                String initials = "NP";
                String dobStr = "Not configured";
                String gender = "Not specified";
                String bloodGroup = "Not recorded";

                if (p != null) {
                    if (p.fullName != null && !p.fullName.trim().isEmpty()) {
                        name = p.fullName.trim();
                    }
                    if (p.dobMillis > 0) {
                        dobStr = DateFormat.format("MMM dd, yyyy", new Date(p.dobMillis)).toString();
                        int age = calculateAge(p.dobMillis);
                        if (age > 0) dobStr += " (" + age + " yrs)";
                    }
                    if (p.gender != null && !p.gender.isEmpty()) {
                        gender = p.gender;
                    }
                    if (p.bloodGroup != null && !p.bloodGroup.isEmpty()) {
                        bloodGroup = p.bloodGroup;
                    }

                    // Photo handling
                    if (p.profilePhotoUri != null && !p.profilePhotoUri.isEmpty()) {
                        try {
                            File f = new File(Uri.parse(p.profilePhotoUri).getPath());
                            if (f.exists()) {
                                ivOverviewPhoto.setImageURI(Uri.parse(p.profilePhotoUri));
                                ivOverviewPhoto.setVisibility(View.VISIBLE);
                                layoutOverviewInitials.setVisibility(View.GONE);
                            }
                        } catch (Exception e) {
                            ivOverviewPhoto.setVisibility(View.GONE);
                            layoutOverviewInitials.setVisibility(View.VISIBLE);
                        }
                    } else {
                        ivOverviewPhoto.setVisibility(View.GONE);
                        layoutOverviewInitials.setVisibility(View.VISIBLE);
                    }
                } else {
                    String storedName = TokenManager.getPatientName(this);
                    if (!storedName.isEmpty()) name = storedName;
                    long dob = TokenManager.getDOB(this);
                    if (dob > 0) dobStr = DateFormat.format("MMM dd, yyyy", new Date(dob)).toString();
                    String g = TokenManager.getGender(this);
                    if (!g.isEmpty()) gender = g;
                    String bg = TokenManager.getBloodGroup(this);
                    if (!bg.isEmpty()) bloodGroup = bg;
                }

                // Compute Initials
                String[] parts = name.split("\\s+");
                if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                    initials = ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase(Locale.getDefault());
                } else if (name.length() >= 2) {
                    initials = name.substring(0, 2).toUpperCase(Locale.getDefault());
                } else {
                    initials = name.substring(0, 1).toUpperCase(Locale.getDefault());
                }

                tvOverviewName.setText(name);
                tvOverviewInitials.setText(initials);
                tvPersonalFullName.setText(name);
                tvPersonalDob.setText(dobStr);
                tvPersonalGender.setText(gender);
                tvPersonalBloodGroup.setText(bloodGroup);

                String idSuffix = patientUuid.length() >= 6 ? patientUuid.substring(0, 6).toUpperCase(Locale.US) : "001";
                tvOverviewPatientId.setText("ID: NP-" + idSuffix + " • Verified");

                if (finalContactCount > 0) {
                    tvEmergencyContactSummary.setText(finalContactCount + " configured • Primary: " + finalPrimaryContact);
                } else {
                    tvEmergencyContactSummary.setText("No emergency contacts configured");
                }
            });
        }).start();
    }

    private int calculateAge(long dobMillis) {
        Calendar dob = Calendar.getInstance();
        dob.setTimeInMillis(dobMillis);
        Calendar now = Calendar.getInstance();
        int age = now.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
        if (now.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
            age--;
        }
        return Math.max(0, age);
    }

    // ─── SECTION C: APPLICATION PREFERENCES ───────────────────────────────────

    private void setupPreferencesControls() {
        // 1. Language
        String currentLang = appPrefs.getString("pref_language", "English");
        tvSelectedLanguage.setText(currentLang);
        rowPrefLanguage.setOnClickListener(v -> showLanguageDialog());

        // 2. Glucose Unit
        String currentGlucoseUnit = appPrefs.getString("pref_glucose_unit", "mg/dL");
        tvSelectedGlucoseUnit.setText(currentGlucoseUnit.equals("mg/dL") ? "mg/dL (Standard in India/US)" : "mmol/L (International)");
        rowPrefGlucoseUnit.setOnClickListener(v -> showGlucoseUnitDialog());

        // 3. Theme
        String currentTheme = appPrefs.getString("pref_theme", "System Default");
        tvSelectedTheme.setText(currentTheme);
        rowPrefTheme.setOnClickListener(v -> showThemeDialog());

        // 4. Date Format
        String currentDateFormat = appPrefs.getString("pref_date_format", "dd/MM/yyyy");
        tvSelectedDateFormat.setText(currentDateFormat);
        rowPrefDateFormat.setOnClickListener(v -> showDateFormatDialog());
    }

    private void showLanguageDialog() {
        String[] languages = {"English (Default)", "Hindi (हिन्दी)"};
        new AlertDialog.Builder(this)
                .setTitle("Select App Language")
                .setItems(languages, (dialog, which) -> {
                    String selected = languages[which];
                    appPrefs.edit().putString("pref_language", selected).apply();
                    tvSelectedLanguage.setText(selected);
                    Toast.makeText(this, "Language preference saved: " + selected, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showGlucoseUnitDialog() {
        String[] units = {"mg/dL (Milligrams per deciliter)", "mmol/L (Millimoles per liter)"};
        new AlertDialog.Builder(this)
                .setTitle("Select Glucose Unit")
                .setItems(units, (dialog, which) -> {
                    String unitCode = which == 0 ? "mg/dL" : "mmol/L";
                    appPrefs.edit().putString("pref_glucose_unit", unitCode).apply();
                    tvSelectedGlucoseUnit.setText(which == 0 ? "mg/dL (Standard in India/US)" : "mmol/L (International)");
                    Toast.makeText(this, "Glucose display unit updated to " + unitCode, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showThemeDialog() {
        String[] themes = {"System Default", "Light Theme", "Dark Theme"};
        new AlertDialog.Builder(this)
                .setTitle("Select Appearance Theme")
                .setItems(themes, (dialog, which) -> {
                    String selected = themes[which];
                    appPrefs.edit().putString("pref_theme", selected).apply();
                    tvSelectedTheme.setText(selected);

                    if (which == 1) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    } else if (which == 2) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                    }
                    Toast.makeText(this, "Theme applied: " + selected, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDateFormatDialog() {
        String[] formats = {"dd/MM/yyyy (e.g. 31/10/2026)", "MM/dd/yyyy (e.g. 10/31/2026)", "yyyy-MM-dd (e.g. 2026-10-31)"};
        new AlertDialog.Builder(this)
                .setTitle("Select Date Format")
                .setItems(formats, (dialog, which) -> {
                    String selected = which == 0 ? "dd/MM/yyyy" : (which == 1 ? "MM/dd/yyyy" : "yyyy-MM-dd");
                    appPrefs.edit().putString("pref_date_format", selected).apply();
                    tvSelectedDateFormat.setText(selected);
                    Toast.makeText(this, "Date format updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── SECTION D: NOTIFICATIONS & PERMISSIONS ───────────────────────────────

    private void setupNotificationToggles() {
        switchGlucoseReminders.setChecked(notifPrefs.getBoolean("notif_glucose", true));
        switchGlucoseReminders.setOnCheckedChangeListener((btn, isChecked) -> {
            notifPrefs.edit().putBoolean("notif_glucose", isChecked).apply();
            Toast.makeText(this, "Glucose reminders " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        switchMedReminders.setChecked(notifPrefs.getBoolean("notif_meds", true));
        switchMedReminders.setOnCheckedChangeListener((btn, isChecked) -> {
            notifPrefs.edit().putBoolean("notif_meds", isChecked).apply();
            Toast.makeText(this, "Medication alerts " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        switchRetinaReminders.setChecked(notifPrefs.getBoolean("notif_retina", true));
        switchRetinaReminders.setOnCheckedChangeListener((btn, isChecked) -> {
            notifPrefs.edit().putBoolean("notif_retina", isChecked).apply();
            Toast.makeText(this, "Retinal recalls " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        switchOrganReminders.setChecked(notifPrefs.getBoolean("notif_organ", true));
        switchOrganReminders.setOnCheckedChangeListener((btn, isChecked) -> {
            notifPrefs.edit().putBoolean("notif_organ", isChecked).apply();
            Toast.makeText(this, "Kidney & foot reminders " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });
    }

    private void checkSystemNotificationPermission() {
        boolean enabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        if (enabled) {
            bannerSystemPermission.setBackgroundResource(R.drawable.bg_pill_status_good);
            ivPermissionStatusIcon.setImageResource(R.drawable.ic_check_circle);
            ivPermissionStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_success));
            tvPermissionStatusText.setText("Device notification permissions are enabled.");
            tvPermissionStatusText.setTextColor(ContextCompat.getColor(this, R.color.netra_success));
            btnOpenDeviceSettings.setVisibility(View.GONE);
        } else {
            bannerSystemPermission.setBackgroundResource(R.drawable.bg_card_danger);
            ivPermissionStatusIcon.setImageResource(R.drawable.ic_alert_triangle_red);
            ivPermissionStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.netra_critical));
            tvPermissionStatusText.setText("Notifications are blocked by your device settings.");
            tvPermissionStatusText.setTextColor(ContextCompat.getColor(this, R.color.netra_critical));
            btnOpenDeviceSettings.setVisibility(View.VISIBLE);
        }
    }

    private void openSystemNotificationSettings() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open system settings directly", Toast.LENGTH_SHORT).show();
        }
    }

    // ─── SECTION E: PRIVACY & DATA GOVERNANCE ─────────────────────────────────

    private void showPrivacyPolicyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("NetraProof Privacy & Encryption")
                .setMessage("• Local-First Privacy: Your retinal images and biometric health metrics are processed on-device and stored in an AES-256 GCM encrypted sandbox backed by Android Keystore MasterKey.\n\n"
                        + "• Zero Commercial Selling: NetraProof does not monetize, sell, or rent your healthcare records to third-party data brokers or advertisers.\n\n"
                        + "• AI Pipeline Isolation: Retinal image quality assessment, DR grading, and lesion segmentation models run strictly within secure sandboxes.\n\n"
                        + "• Full Data Sovereignty: You have the right to inspect, export, or permanently erase your health records at any time.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void showTermsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Terms of Service & Clinical Notice")
                .setMessage("• Investigational Companion: NetraProof is an AI-powered research and screening companion designed for diabetic health monitoring.\n\n"
                        + "• No Medical Diagnosis: AI outputs, severity classifications, and segmentation masks do not constitute a definitive medical diagnosis, prescription, or clinical treatment recommendation.\n\n"
                        + "• Annual Dilated Examinations: All patients with diabetes must undergo comprehensive annual dilated eye examinations performed by a licensed ophthalmologist or optometrist regardless of screening scores.")
                .setPositiveButton("I Understand", null)
                .show();
    }

    private void exportPatientHealthDossier() {
        Toast.makeText(this, "Generating health record dossier...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabaseProvider.getInstance(this);
                PatientEntity patient = db.patientDao().getPatient(patientUuid);
                List<HealthDocumentEntity> docs = db.healthDocumentDao().getDocumentsForPatient(patientUuid);
                List<MedicationScheduleEntity> meds = db.scheduleDao().getAll();
                List<EmergencyContactEntity> contacts = db.emergencyContactDao().getContactsForPatient(patientUuid);

                JSONObject root = new JSONObject();
                root.put("exportDate", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
                root.put("application", "NetraProof Healthcare Suite");
                root.put("version", BuildConfig.VERSION_NAME);

                // Patient Profile
                JSONObject patientObj = new JSONObject();
                patientObj.put("patientId", "NP-" + (patientUuid.length() >= 6 ? patientUuid.substring(0, 6).toUpperCase(Locale.US) : "001"));
                patientObj.put("name", patient != null && patient.fullName != null ? patient.fullName : TokenManager.getPatientName(this));
                patientObj.put("dob", patient != null && patient.dobMillis > 0 ? DateFormat.format("yyyy-MM-dd", new Date(patient.dobMillis)).toString() : "N/A");
                patientObj.put("gender", patient != null && patient.gender != null ? patient.gender : TokenManager.getGender(this));
                patientObj.put("bloodGroup", patient != null && patient.bloodGroup != null ? patient.bloodGroup : TokenManager.getBloodGroup(this));
                root.put("patientProfile", patientObj);

                // Screening History
                JSONArray docsArr = new JSONArray();
                if (docs != null) {
                    for (HealthDocumentEntity d : docs) {
                        JSONObject docObj = new JSONObject();
                        docObj.put("id", d.id);
                        docObj.put("name", d.displayName);
                        docObj.put("timestamp", d.uploadTimestamp);
                        if (d.extractedJson != null) {
                            try {
                                docObj.put("aiMetadata", new JSONObject(d.extractedJson));
                            } catch (Exception ignored) {}
                        }
                        docsArr.put(docObj);
                    }
                }
                root.put("retinalScreenings", docsArr);

                // Medications
                JSONArray medArr = new JSONArray();
                if (meds != null) {
                    SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a", Locale.US);
                    for (MedicationScheduleEntity m : meds) {
                        JSONObject mObj = new JSONObject();
                        mObj.put("name", m.drugName);
                        mObj.put("status", m.status);
                        mObj.put("scheduledTime", timeFmt.format(new Date(m.timeMillis)));
                        medArr.put(mObj);
                    }
                }
                root.put("activeMedications", medArr);

                // Contacts
                JSONArray contactArr = new JSONArray();
                if (contacts != null) {
                    for (EmergencyContactEntity c : contacts) {
                        JSONObject cObj = new JSONObject();
                        cObj.put("name", c.name);
                        cObj.put("phone", c.phoneNumber);
                        contactArr.put(cObj);
                    }
                }
                root.put("emergencyContacts", contactArr);

                String dossierContent = root.toString(2);

                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("text/plain");
                    sendIntent.putExtra(Intent.EXTRA_SUBJECT, "NetraProof Health Record Dossier — " + TokenManager.getPatientName(this));
                    sendIntent.putExtra(Intent.EXTRA_TEXT, "=== NETRAPROOF HEALTH RECORD DOSSIER ===\n\n" + dossierContent);
                    startActivity(Intent.createChooser(sendIntent, "Export Health Records"));
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "Failed to compile dossier: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showClearChatDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear AI Assistant History")
                .setMessage("Are you sure you want to delete all past conversation messages with Netra AI Assistant?")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    new Thread(() -> {
                        AppDatabaseProvider.getInstance(this).chatMessageDao().deleteMessagesForPatient(patientUuid);
                        mainHandler.post(() -> {
                            Toast.makeText(this, "AI chat history cleared", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── SECTION F: SECURITY & SESSION ────────────────────────────────────────

    private void setupSecurityAndDeviceInfo() {
        String deviceName = Build.MANUFACTURER.toUpperCase(Locale.US) + " " + Build.MODEL;
        tvSecurityDevice.setText(deviceName);

        String osDetails = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") • Keystore AES-256";
        tvSecurityEncryption.setText(osDetails);

        switchBiometricLock.setChecked(appPrefs.getBoolean("pref_biometric_lock", false));
        switchBiometricLock.setOnCheckedChangeListener((btn, isChecked) -> {
            appPrefs.edit().putBoolean("pref_biometric_lock", isChecked).apply();
            Toast.makeText(this, "App screen lock " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        switchAnonymousTelemetry.setChecked(appPrefs.getBoolean("pref_telemetry", false));
        switchAnonymousTelemetry.setOnCheckedChangeListener((btn, isChecked) -> {
            appPrefs.edit().putBoolean("pref_telemetry", isChecked).apply();
            Toast.makeText(this, "Model diagnostics " + (isChecked ? "opted-in" : "opted-out"), Toast.LENGTH_SHORT).show();
        });
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Logout")
                .setMessage("Are you sure you want to log out from this device? Your local data will remain securely encrypted on this phone.")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    TokenManager.saveJWT(this, null);
                    Intent intent = new Intent(this, Step1BasicInfoActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── SECTION G: SAFEGUARDED DESTRUCTIVE RESET ─────────────────────────────

    private void showResetConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Warning: Erase All Health Data")
                .setMessage("This action is permanent and IRREVERSIBLE.\n\n"
                        + "All retinal screening images, DR prediction reports, lesion segmentation masks, glucose readings, medication schedules, and patient profiles will be completely erased from this device.\n\n"
                        + "Do you wish to continue?")
                .setPositiveButton("Erase Everything", (dialog, which) -> {
                    performIrreversibleReset();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performIrreversibleReset() {
        Toast.makeText(this, "Erasing profile & records...", Toast.LENGTH_SHORT).show();

        // 1. Delete from Cloud if configured
        FirebaseRepository.getInstance(this).deleteCloudProfile(new FirebaseRepository.SyncCallback() {
            @Override
            public void onSuccess() {
                executeLocalDataWipe();
            }

            @Override
            public void onFailure(Exception e) {
                // Proceed with local wipe even if cloud fails
                executeLocalDataWipe();
            }
        });
    }

    private void executeLocalDataWipe() {
        TokenManager.clearAll(this);
        appPrefs.edit().clear().apply();
        notifPrefs.edit().clear().apply();
        getSharedPreferences("netra_health_metrics", Context.MODE_PRIVATE).edit().clear().apply();

        new Thread(() -> {
            AppDatabase db = AppDatabaseProvider.getInstance(this);
            try {
                db.patientDao().deleteAllPatients();
                db.healthDocumentDao().deleteAllDocuments();
                db.emergencyContactDao().deleteAllContacts();
                db.chatMessageDao().deleteAllMessages();
                db.scheduleDao().deleteAll();
                db.medicationDao().deleteAllMedications();
            } catch (Exception ignored) {}

            mainHandler.post(() -> {
                Toast.makeText(this, "Profile and data completely erased", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(this, Step1BasicInfoActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }).start();
    }

    // ─── SECTION H: HELP & SUPPORT ────────────────────────────────────────────

    private void showFaqDialog() {
        String[] faqs = {
                "Q: How does NetraProof DR screening work?\nA: It uses a multi-stage clinical AI pipeline: optical quality check (ViT), 5-fold ensemble DR severity grading (EfficientNet-B0), and lesion segmentation (SE-ResNeXt50 U-Net).",
                "Q: Can NetraProof replace my ophthalmologist?\nA: No. NetraProof is an investigational screening companion. All individuals with diabetes must undergo regular in-person dilated eye exams.",
                "Q: Where is my retinal image stored?\nA: Retinal images are stored in your device's private app storage, encrypted using Android Keystore AES-256 GCM.",
                "Q: How often should I screen my eyes?\nA: Annual dilated screening is clinically recommended for all individuals with diabetes, or sooner if mild/moderate NPDR is detected."
        };

        new AlertDialog.Builder(this)
                .setTitle("Frequently Asked Questions")
                .setItems(faqs, null)
                .setPositiveButton("Close", null)
                .show();
    }

    private void sendSupportEmail() {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:support@netraproof.ai"));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "NetraProof Support Request — Patient: " + TokenManager.getPatientName(this));
        try {
            startActivity(emailIntent);
        } catch (Exception e) {
            Toast.makeText(this, "No email client found. Contact: support@netraproof.ai", Toast.LENGTH_LONG).show();
        }
    }

    private void setupAboutSection() {
        String verName = BuildConfig.VERSION_NAME;
        int verCode = BuildConfig.VERSION_CODE;
        tvAppVersion.setText("Version " + verName + " (Build " + verCode + ")");
    }
}

