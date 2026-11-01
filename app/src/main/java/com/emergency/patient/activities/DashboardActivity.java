package com.emergency.patient.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import android.net.Uri;
import android.provider.Settings;
import android.app.NotificationManager;
import androidx.appcompat.app.AlertDialog;

import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.network.FcmTokenSyncManager;
import com.emergency.patient.security.TokenManager;

import com.emergency.patient.network.FirebaseRepository;
import com.emergency.patient.services.EmergencyBackgroundService;
import com.emergency.patient.services.FallDetectionService;
import com.emergency.patient.services.MedicationSyncService;
import com.emergency.patient.scheduling.NotificationHelper;
import com.emergency.patient.utils.PermissionHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import java.util.concurrent.TimeUnit;
import com.emergency.patient.scheduling.MedicationExpiryWorker;

public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Route to onboarding if not yet complete
        String uuid = TokenManager.getUUID(this);
        
        new Thread(() -> {
            boolean isComplete;
            try {
                PatientEntity patient = AppDatabaseProvider.getInstance(this).patientDao().getPatient(uuid);
                isComplete = (patient != null && patient.isOnboardingComplete) || TokenManager.isOnboardingComplete(this);
            } catch (Exception e) {
                isComplete = TokenManager.isOnboardingComplete(this);
            }
            
            final boolean finalIsComplete = isComplete;
            runOnUiThread(() -> {
                if (!finalIsComplete) {
                    startActivity(new Intent(this, Step1BasicInfoActivity.class));
                    finish();
                    return;
                }

                PermissionHelper.requestAllPermissions(this);
                PermissionHelper.enforceBatteryOptimizationBypass(this);
                FcmTokenSyncManager.syncCurrentToken(this);
                com.emergency.patient.network.NetworkClient.pingHealth();
                
                // Schedule Expiry Check (Daily)
                PeriodicWorkRequest expiryWork = new PeriodicWorkRequest.Builder(MedicationExpiryWorker.class, 24, TimeUnit.HOURS).build();
                WorkManager.getInstance(this).enqueueUniquePeriodicWork("med_expiry_check", ExistingPeriodicWorkPolicy.KEEP, expiryWork);

                setContentView(R.layout.activity_main);
                EmergencyBackgroundService.stop(this);

                // Start cloud medication sync service
                MedicationSyncService.start(this);

                // Ensure notification channels exist
                NotificationHelper.createChannels(this);

                // Sync any adherence logs that failed to upload while offline
                FirebaseRepository.getInstance(this).syncPendingAdherenceLogs(this);

                // Fall detection completely disabled per user preference
                FallDetectionService.stop(this);

                // 2. Prompt battery whitelist if not already granted
                checkBatteryOptimizationWhitelist();

                // 3. Prompt display over other apps permission (SYSTEM_ALERT_WINDOW)
                checkOverlayPermission();

                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                        .setAppearanceLightStatusBars(true);

                setupFloatingNavBar();

                // Set default selection — Home dashboard is now the landing screen
                if (savedInstanceState == null) {
                    if (getIntent().getBooleanExtra("launch_teleconsult", false)) {
                        switchTab(null, new TeleconsultationFragment());
                    } else if (getIntent().getBooleanExtra("launch_chat", false)) {
                        switchTab(null, new ChatWebViewFragment());
                    } else {
                        selectFloatingTab(0);
                    }
                }
            });
        }).start();
    }

    private android.view.View navTabHome, navTabMedication, navTabMedicalId, navTabProfile, btnFloatingAi, floatingNavbarContainer;
    private android.widget.ImageView ivNavHome, ivNavMedication, ivNavMedicalId, ivNavProfile, ivFloatingAiIcon;
    private android.widget.TextView tvNavHome, tvNavMedication, tvNavMedicalId, tvNavProfile;
    private android.animation.ValueAnimator aiPulseAnimator;

    private void setupFloatingNavBar() {
        floatingNavbarContainer = findViewById(R.id.floatingNavbarContainer);
        navTabHome = findViewById(R.id.navTabHome);
        navTabMedication = findViewById(R.id.navTabMedication);
        navTabMedicalId = findViewById(R.id.navTabMedicalId);
        navTabProfile = findViewById(R.id.navTabProfile);
        btnFloatingAi = findViewById(R.id.btnFloatingAi);

        ivNavHome = findViewById(R.id.ivNavHome);
        ivNavMedication = findViewById(R.id.ivNavMedication);
        ivNavMedicalId = findViewById(R.id.ivNavMedicalId);
        ivNavProfile = findViewById(R.id.ivNavProfile);
        ivFloatingAiIcon = findViewById(R.id.ivFloatingAiIcon);

        tvNavHome = findViewById(R.id.tvNavHome);
        tvNavMedication = findViewById(R.id.tvNavMedication);
        tvNavMedicalId = findViewById(R.id.tvNavMedicalId);
        tvNavProfile = findViewById(R.id.tvNavProfile);
        if (tvNavProfile != null) tvNavProfile.setText("Diabetes360");

        if (navTabHome != null) navTabHome.setOnClickListener(v -> selectFloatingTab(0));
        if (navTabMedication != null) navTabMedication.setOnClickListener(v -> selectFloatingTab(1));
        if (navTabMedicalId != null) navTabMedicalId.setOnClickListener(v -> selectFloatingTab(2));
        if (navTabProfile != null) navTabProfile.setOnClickListener(v -> selectFloatingTab(3));

        if (btnFloatingAi != null) {
            btnFloatingAi.setOnClickListener(v -> {
                // Launch NetraProof AI Assistant (Page 13)
                startActivity(new Intent(this, NetraAiAssistantActivity.class));
            });
            startAiPulseAnimation();
        }

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            Fragment cur = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (cur instanceof HealthAssistantFragment || cur instanceof TeleconsultationFragment || cur instanceof ChatWebViewFragment) {
                setFloatingNavbarVisible(false);
            } else {
                setFloatingNavbarVisible(true);
            }
        });
    }

    public void setFloatingNavbarVisible(boolean visible) {
        if (floatingNavbarContainer != null) {
            floatingNavbarContainer.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void startAiPulseAnimation() {
        if (btnFloatingAi == null) return;
        aiPulseAnimator = android.animation.ValueAnimator.ofFloat(1.0f, 1.12f, 1.0f);
        aiPulseAnimator.setDuration(1600);
        aiPulseAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        aiPulseAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        aiPulseAnimator.addUpdateListener(animation -> {
            float s = (float) animation.getAnimatedValue();
            if (btnFloatingAi != null) {
                btnFloatingAi.setScaleX(s);
                btnFloatingAi.setScaleY(s);
            }
        });
        aiPulseAnimator.start();
    }

    public void selectFloatingTab(int index) {
        if (floatingNavbarContainer != null) floatingNavbarContainer.setVisibility(android.view.View.VISIBLE);
        int activeColor = android.graphics.Color.parseColor("#147D82");
        int inactiveColor = android.graphics.Color.parseColor("#71818D");

        if (ivNavHome != null) ivNavHome.setColorFilter(index == 0 ? activeColor : inactiveColor);
        if (tvNavHome != null) {
            tvNavHome.setTextColor(index == 0 ? activeColor : inactiveColor);
            tvNavHome.setTypeface(null, index == 0 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }

        if (ivNavMedication != null) ivNavMedication.setColorFilter(index == 1 ? activeColor : inactiveColor);
        if (tvNavMedication != null) {
            tvNavMedication.setTextColor(index == 1 ? activeColor : inactiveColor);
            tvNavMedication.setTypeface(null, index == 1 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }

        if (ivNavMedicalId != null) ivNavMedicalId.setColorFilter(index == 2 ? activeColor : inactiveColor);
        if (tvNavMedicalId != null) {
            tvNavMedicalId.setTextColor(index == 2 ? activeColor : inactiveColor);
            tvNavMedicalId.setTypeface(null, index == 2 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }

        if (ivNavProfile != null) ivNavProfile.setColorFilter(index == 3 ? activeColor : inactiveColor);
        if (tvNavProfile != null) {
            tvNavProfile.setTextColor(index == 3 ? activeColor : inactiveColor);
            tvNavProfile.setTypeface(null, index == 3 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }

        if (index == 0) {
            switchTab(null, new MedicationSafetyHomeFragment());
        } else if (index == 1) {
            // NetraProof Retina Screening Module (Page 02)
            startActivity(new Intent(this, RetinaScreeningActivity.class));
        } else if (index == 2) {
            // NetraProof AI Retinal Screening Report (Page 06)
            startActivity(new Intent(this, RetinaScreeningReportActivity.class));
        } else if (index == 3) {
            // NetraProof Profile & Settings Module (Page 16)
            startActivity(new Intent(this, Diabetes360DashboardActivity.class));
        }
    }

    private void highlightAiTab() {
        int inactiveColor = android.graphics.Color.parseColor("#71818D");
        if (ivNavHome != null) ivNavHome.setColorFilter(inactiveColor);
        if (tvNavHome != null) tvNavHome.setTextColor(inactiveColor);
        if (ivNavMedication != null) ivNavMedication.setColorFilter(inactiveColor);
        if (tvNavMedication != null) tvNavMedication.setTextColor(inactiveColor);
        if (ivNavMedicalId != null) ivNavMedicalId.setColorFilter(inactiveColor);
        if (tvNavMedicalId != null) tvNavMedicalId.setTextColor(inactiveColor);
        if (ivNavProfile != null) ivNavProfile.setColorFilter(inactiveColor);
        if (tvNavProfile != null) tvNavProfile.setTextColor(inactiveColor);
    }

    private void switchTab(String tag, Fragment fragment) {
        if (findViewById(R.id.fragment_container) == null || isFinishing() || isDestroyed()) return;
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, fragment)
                .commitAllowingStateLoss();
    }

    @Override
    protected void onResume() {
        super.onResume();
        selectFloatingTab(0);
        EmergencyBackgroundService.stop(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (aiPulseAnimator != null) {
            aiPulseAnimator.cancel();
            aiPulseAnimator = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int navItem = intent.getIntExtra("selected_nav_item", -1);
        if (navItem == R.id.nav_home) {
            selectFloatingTab(0);
        } else if (navItem == R.id.nav_dashboard) {
            selectFloatingTab(1);
        } else if (navItem == R.id.nav_medical_id) {
            selectFloatingTab(2);
        } else if (navItem == R.id.nav_profile) {
            selectFloatingTab(3);
        }
    }

    @Override
    public void onBackPressed() {
        // Check if current fragment is TeleconsultationFragment or ChatWebViewFragment and handle WebView back navigation
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof TeleconsultationFragment) {
            TeleconsultationFragment teleconsultFragment = (TeleconsultationFragment) currentFragment;
            if (teleconsultFragment.onBackPressed()) {
                // Fragment handled the back press (WebView navigation)
                return;
            }
        } else if (currentFragment instanceof ChatWebViewFragment) {
            ChatWebViewFragment chatFragment = (ChatWebViewFragment) currentFragment;
            if (chatFragment.onBackPressed()) {
                // Fragment handled the back press (WebView navigation)
                return;
            }
        } else if (currentFragment instanceof HealthAssistantFragment) {
            setFloatingNavbarVisible(true);
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStack();
            } else {
                selectFloatingTab(0);
            }
            return;
        }
        super.onBackPressed();
    }

    // ─── Fall Detection Permission Gate ──────────────────────────────────────

    private static final int FALL_DETECTION_PERMISSION_CODE = 201;
    private static final String TAG = "DashboardActivity";

    /**
     * Hard gate: Only starts FallDetectionService after confirming runtime grants.
     * ACTIVITY_RECOGNITION and BODY_SENSORS are dangerous permissions on Android 10+.
     */
    private void checkAndStartFallDetection() {
        // Fall detection completely disabled
        FallDetectionService.stop(this);
    }

    private void startFallDetectionService() {
        // Fall detection completely disabled
        FallDetectionService.stop(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == FALL_DETECTION_PERMISSION_CODE) {
            boolean anyGranted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    anyGranted = true;
                    break;
                }
            }
            if (anyGranted) {
                checkFullScreenIntentPermission();
                startFallDetectionService();
            } else {
                Toast.makeText(this,
                    "Fall detection requires sensor permissions. Enable in Settings for full protection.",
                    Toast.LENGTH_LONG).show();
                Log.w(TAG, "Fall detection disabled — sensor permissions denied.");
            }
        }
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this,
                        new String[] { android.Manifest.permission.POST_NOTIFICATIONS }, 101);
            }
        }
    }

    /**
     * Android 14+ Security: Checks if the app is allowed to show full-screen alerts.
     * This is required to bypass Background Activity Launch (BAL) restrictions.
     */
    private void checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                new AlertDialog.Builder(this)
                        .setTitle("Enable Emergency Alerts")
                        .setMessage("To ensure fall detection alerts show immediately on your lock screen, please enable 'Full Screen Intent' in settings.")
                        .setPositiveButton("Open Settings", (d, w) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Later", null)
                        .show();
            }
        }
    }

    /**
     * Checks if the app is allowed to draw over other apps (SYSTEM_ALERT_WINDOW).
     * Required for Fall Detection emergency alarms and full-screen medication alerts.
     */
    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                new AlertDialog.Builder(this)
                    .setTitle("Display Over Other Apps")
                    .setMessage("To ensure emergency alerts and medication reminders pop up immediately over any screen, please enable 'Display over other apps' for NetraProof.")
                    .setPositiveButton("Enable", (d, w) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to open overlay settings", e);
                        }
                    })
                    .setNegativeButton("Later", null)
                    .show();
            }
        }
    }

    /**
     * Requests ACTIVITY_RECOGNITION permission (required on Android 10+).
     * Without this, fall detection runs always-on at SENSOR_DELAY_GAME (higher battery).
     */
    private void requestActivityRecognitionPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACTIVITY_RECOGNITION)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.ACTIVITY_RECOGNITION}, 201);
            }
        }
    }

    /**
     * Checks if battery optimization is blocking the fall detection service.
     * Shows a one-time dialog explaining WHY this is critical for a safety app.
     *
     * 3-Layer Battery Defense:
     *   1. This whitelist → prevents Doze Mode killing sensor loop.
     *   2. PARTIAL_WAKE_LOCK in service → keeps CPU alive with screen off.
     *   3. ActivityRecognition gating → reduces sensor rate when STILL.
     */
    private void checkBatteryOptimizationWhitelist() {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null) return;
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) return; // Already whitelisted

        // Only prompt once — check a flag
        boolean prompted = getSharedPreferences("prefs", MODE_PRIVATE)
                .getBoolean("battery_whitelist_prompted", false);
        if (prompted) return;
        getSharedPreferences("prefs", MODE_PRIVATE).edit()
                .putBoolean("battery_whitelist_prompted", true).apply();

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enable Reliable Fall Guard")
            .setMessage(
                "NetraProof needs unrestricted background access to monitor " +
                "for falls while your screen is off. Without this, Android may " +
                "pause the sensor during deep sleep, causing missed detections.\n\n" +
                "Tap 'Allow' → select 'Unrestricted' to ensure 24/7 protection."
            )
            .setPositiveButton("Allow", (d, w) -> {
                android.content.Intent intent = new android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            })
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show();
    }
}

