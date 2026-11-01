package com.emergency.patient.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.emergency.patient.R;
import com.emergency.patient.activities.EditProfileActivity;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.security.TokenManager;

import java.util.Locale;

/**
 * NetraProfileHeaderHelper — Binds the unified top navbar patient profile avatar across NetraProof pages.
 */
public final class NetraProfileHeaderHelper {

    private NetraProfileHeaderHelper() {}

    public static void setup(Activity activity) {
        if (activity == null) return;

        View avatarBtn = activity.findViewById(R.id.btnProfileAvatar);
        TextView tvInitials = activity.findViewById(R.id.tvAvatarInitials);
        if (avatarBtn == null && tvInitials == null) return;

        String uuid = TokenManager.getUUID(activity);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            PatientEntity patient = null;
            try {
                if (uuid != null) {
                    patient = AppDatabaseProvider.getInstance(activity).patientDao().getPatient(uuid);
                }
            } catch (Exception ignored) {}

            final PatientEntity finalPatient = patient;
            mainHandler.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;

                String initials = "NP";
                String displayName = "Patient";
                String displayId = "NP-" + (uuid != null && uuid.length() >= 6 ? uuid.substring(0, 6).toUpperCase() : "001");

                if (finalPatient != null && finalPatient.fullName != null && !finalPatient.fullName.trim().isEmpty()) {
                    displayName = finalPatient.fullName.trim();
                    String[] parts = displayName.split("\\s+");
                    if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                        initials = ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase(Locale.getDefault());
                    } else if (displayName.length() >= 2) {
                        initials = displayName.substring(0, 2).toUpperCase(Locale.getDefault());
                    } else {
                        initials = displayName.substring(0, 1).toUpperCase(Locale.getDefault());
                    }
                }

                if (tvInitials != null) {
                    tvInitials.setText(initials);
                }

                if (avatarBtn != null) {
                    avatarBtn.setOnClickListener(v -> {
                        if (!(activity instanceof com.emergency.patient.activities.NetraProfileSettingsActivity)) {
                            activity.startActivity(new Intent(activity, com.emergency.patient.activities.NetraProfileSettingsActivity.class));
                        }
                    });
                }
            });
        }).start();
    }
}

