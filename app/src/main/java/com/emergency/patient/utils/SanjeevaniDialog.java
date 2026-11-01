package com.emergency.patient.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.emergency.patient.R;

/**
 * SanjeevaniDialog — Premium Theme-aligned Dialog Builder.
 * Ensures consistent design tokens (#F7FCF9 canvas, #087F68 primary, #0D2140 dark text).
 */
public class SanjeevaniDialog {

    public interface OnDialogClickListener {
        void onClick(Dialog dialog);
    }

    public static void showInfo(@NonNull Context context,
                                @NonNull String title,
                                @NonNull String message,
                                @Nullable String positiveText,
                                @Nullable OnDialogClickListener onPositive) {
        showCustom(context, 0, title, message, positiveText, onPositive, null, null);
    }

    public static void showWithAction(@NonNull Context context,
                                      @DrawableRes int iconRes,
                                      @NonNull String title,
                                      @NonNull String message,
                                      @NonNull String positiveText,
                                      @Nullable OnDialogClickListener onPositive,
                                      @Nullable String negativeText,
                                      @Nullable OnDialogClickListener onNegative) {
        showCustom(context, iconRes, title, message, positiveText, onPositive, negativeText, onNegative);
    }

    public static void showCustom(@NonNull Context context,
                                  @DrawableRes int iconRes,
                                  @NonNull String title,
                                  @NonNull String message,
                                  @Nullable String positiveText,
                                  @Nullable OnDialogClickListener onPositive,
                                  @Nullable String negativeText,
                                  @Nullable OnDialogClickListener onNegative) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_styled_info, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                (int) (context.getResources().getDisplayMetrics().widthPixels * 0.90),
                FrameLayout.LayoutParams.WRAP_CONTENT
            );
        }

        // Icon
        FrameLayout iconContainer = view.findViewById(R.id.dialogIconContainer);
        ImageView ivIcon = view.findViewById(R.id.dialogIcon);
        if (iconRes != 0) {
            ivIcon.setImageResource(iconRes);
            iconContainer.setVisibility(View.VISIBLE);
        } else {
            iconContainer.setVisibility(View.GONE);
        }

        // Title & Message
        TextView tvTitle = view.findViewById(R.id.dialogTitle);
        TextView tvMessage = view.findViewById(R.id.dialogMessage);
        tvTitle.setText(title);
        tvMessage.setText(message);

        // Positive Button
        TextView btnPositive = view.findViewById(R.id.dialogBtnPositive);
        btnPositive.setText(positiveText != null ? positiveText : "OK");
        btnPositive.setOnClickListener(v -> {
            if (onPositive != null) {
                onPositive.onClick(dialog);
            }
            dialog.dismiss();
        });

        // Negative Button
        TextView btnNegative = view.findViewById(R.id.dialogBtnNegative);
        if (negativeText != null && !negativeText.isEmpty()) {
            btnNegative.setText(negativeText);
            btnNegative.setVisibility(View.VISIBLE);
            btnNegative.setOnClickListener(v -> {
                if (onNegative != null) {
                    onNegative.onClick(dialog);
                }
                dialog.dismiss();
            });
        } else {
            btnNegative.setVisibility(View.GONE);
        }

        dialog.show();
    }
}
