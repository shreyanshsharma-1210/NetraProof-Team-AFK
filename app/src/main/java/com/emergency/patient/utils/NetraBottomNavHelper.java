package com.emergency.patient.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.emergency.patient.R;
import com.emergency.patient.activities.DashboardActivity;
import com.emergency.patient.activities.Diabetes360DashboardActivity;
import com.emergency.patient.activities.NetraAiAssistantActivity;
import com.emergency.patient.activities.RetinaScreeningActivity;
import com.emergency.patient.activities.RetinaScreeningReportActivity;

/**
 * NetraBottomNavHelper — Manages the sticky floating navigation bar across NetraProof pages.
 */
public final class NetraBottomNavHelper {

    public static final int TAB_HOME        = 0;
    public static final int TAB_RETINA      = 1;
    public static final int TAB_AI          = 2;
    public static final int TAB_REPORTS     = 3;
    public static final int TAB_DIABETES360 = 4;

    private NetraBottomNavHelper() {}

    public static void setup(Activity activity, int activeTab) {
        if (activity == null) return;

        View container = activity.findViewById(R.id.stickyBottomNavContainer);
        if (container == null) return;

        View tabHome = activity.findViewById(R.id.bottomNavHome);
        View tabRetina = activity.findViewById(R.id.bottomNavRetina);
        View tabReports = activity.findViewById(R.id.bottomNavReports);
        View tabProfile = activity.findViewById(R.id.bottomNavProfile);
        View tabAi = activity.findViewById(R.id.bottomNavFloatingAi);

        ImageView ivHome = activity.findViewById(R.id.ivBottomNavHome);
        TextView tvHome = activity.findViewById(R.id.tvBottomNavHome);

        ImageView ivRetina = activity.findViewById(R.id.ivBottomNavRetina);
        TextView tvRetina = activity.findViewById(R.id.tvBottomNavRetina);

        ImageView ivReports = activity.findViewById(R.id.ivBottomNavReports);
        TextView tvReports = activity.findViewById(R.id.tvBottomNavReports);

        ImageView ivProfile = activity.findViewById(R.id.ivBottomNavProfile);
        TextView tvProfile = activity.findViewById(R.id.tvBottomNavProfile);

        int activeColor = ContextCompat.getColor(activity, R.color.netra_primary_teal);
        int inactiveColor = Color.parseColor("#687790");

        // Apply active states
        if (ivHome != null) ivHome.setColorFilter(activeTab == TAB_HOME ? activeColor : inactiveColor);
        if (tvHome != null) {
            tvHome.setTextColor(activeTab == TAB_HOME ? activeColor : inactiveColor);
            tvHome.setTypeface(null, activeTab == TAB_HOME ? Typeface.BOLD : Typeface.NORMAL);
        }

        if (ivRetina != null) ivRetina.setColorFilter(activeTab == TAB_RETINA ? activeColor : inactiveColor);
        if (tvRetina != null) {
            tvRetina.setTextColor(activeTab == TAB_RETINA ? activeColor : inactiveColor);
            tvRetina.setTypeface(null, activeTab == TAB_RETINA ? Typeface.BOLD : Typeface.NORMAL);
        }

        if (ivReports != null) ivReports.setColorFilter(activeTab == TAB_REPORTS ? activeColor : inactiveColor);
        if (tvReports != null) {
            tvReports.setTextColor(activeTab == TAB_REPORTS ? activeColor : inactiveColor);
            tvReports.setTypeface(null, activeTab == TAB_REPORTS ? Typeface.BOLD : Typeface.NORMAL);
        }

        if (ivProfile != null) ivProfile.setColorFilter(activeTab == TAB_DIABETES360 ? activeColor : inactiveColor);
        if (tvProfile != null) {
            tvProfile.setTextColor(activeTab == TAB_DIABETES360 ? activeColor : inactiveColor);
            tvProfile.setTypeface(null, activeTab == TAB_DIABETES360 ? Typeface.BOLD : Typeface.NORMAL);
        }

        // Click listeners with single-task navigation
        if (tabHome != null) {
            tabHome.setOnClickListener(v -> {
                if (activeTab != TAB_HOME) {
                    Intent intent = new Intent(activity, DashboardActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    activity.startActivity(intent);
                }
            });
        }

        if (tabRetina != null) {
            tabRetina.setOnClickListener(v -> {
                if (activeTab != TAB_RETINA) {
                    Intent intent = new Intent(activity, RetinaScreeningActivity.class);
                    activity.startActivity(intent);
                }
            });
        }

        if (tabAi != null) {
            tabAi.setOnClickListener(v -> {
                if (activeTab != TAB_AI) {
                    Intent intent = new Intent(activity, NetraAiAssistantActivity.class);
                    activity.startActivity(intent);
                }
            });
        }

        if (tabReports != null) {
            tabReports.setOnClickListener(v -> {
                if (activeTab != TAB_REPORTS) {
                    Intent intent = new Intent(activity, RetinaScreeningReportActivity.class);
                    activity.startActivity(intent);
                }
            });
        }

        if (tabProfile != null) {
            tabProfile.setOnClickListener(v -> {
                if (activeTab != TAB_DIABETES360) {
                    Intent intent = new Intent(activity, Diabetes360DashboardActivity.class);
                    activity.startActivity(intent);
                }
            });
        }
    }
}

