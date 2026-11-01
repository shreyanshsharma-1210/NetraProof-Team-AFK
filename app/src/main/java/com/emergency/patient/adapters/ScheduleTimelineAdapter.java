package com.emergency.patient.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.emergency.patient.R;
import com.emergency.patient.db.MedicationScheduleEntity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * ScheduleTimelineAdapter — Timeline-style medication schedule list for Medication Dashboard.
 *
 * Each row: [TIME] | [DOT+LINE] | [MEDICATION CARD]
 *
 * Features:
 *  - Timeline dot colour by status: green=TAKEN, amber=PENDING, red=MISSED, grey=UPCOMING
 *  - Status chip text and styling per status
 *  - Administration instruction chip (personalised if available, else "Take as directed")
 *  - Overflow popup menu: Mark Taken, Mark Missed, Mark Pending, Edit Time, Skip
 *  - Last item in list hides the connecting line
 */
public class ScheduleTimelineAdapter extends RecyclerView.Adapter<ScheduleTimelineAdapter.ViewHolder> {

    public interface OnScheduleActionListener {
        void onScheduleClick(MedicationScheduleEntity schedule);
        void onEditTime(MedicationScheduleEntity schedule);
        void onMarkStatus(MedicationScheduleEntity schedule, String newStatus);
    }

    private final List<MedicationScheduleEntity> items;
    private final OnScheduleActionListener listener;
    private boolean editMode = false;

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("hh:mm", Locale.getDefault());
    private static final SimpleDateFormat AM_PM_FMT = new SimpleDateFormat("a", Locale.getDefault());

    public ScheduleTimelineAdapter(List<MedicationScheduleEntity> items, OnScheduleActionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_schedule_timeline, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        MedicationScheduleEntity schedule = items.get(position);
        Context ctx = h.itemView.getContext();

        // ── Time column ────────────────────────────────────────────────
        h.tvTimeHour.setText(TIME_FMT.format(new java.util.Date(schedule.timeMillis)));
        h.tvTimeAmPm.setText(AM_PM_FMT.format(new java.util.Date(schedule.timeMillis)));

        // ── Drug name + dosage ─────────────────────────────────────────
        String drugName = schedule.drugName != null ? schedule.drugName : "Medication";
        h.tvMedName.setText(drugName);
        h.tvDosage.setText("1 Dose");

        // ── Load Medication Image if available ──────────────────────────
        loadMedicationIcon(ctx, h.ivMedIcon, drugName);

        // ── Status dot + chip ──────────────────────────────────────────
        String status = schedule.status != null ? schedule.status.toUpperCase(Locale.ROOT) : "PENDING";
        applyStatusStyle(ctx, h, status, schedule.timeMillis);

        // ── Administration chip ────────────────────────────────────────
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(schedule.timeMillis);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        String adminEmoji;
        String adminNote;
        if (hour >= 5 && hour < 12) {
            adminEmoji = "☀";
            adminNote = "Morning dose • With water";
        } else if (hour >= 12 && hour < 17) {
            adminEmoji = "🍽";
            adminNote = "Afternoon dose • After lunch";
        } else if (hour >= 17 && hour < 21) {
            adminEmoji = "💧";
            adminNote = "Evening dose • With meal";
        } else {
            adminEmoji = "🌙";
            adminNote = "Night dose • Before sleep";
        }
        h.tvAdminChip.setText(adminEmoji + " " + adminNote);

        // ── Hide connecting line for last item ─────────────────────────
        h.viewLine.setVisibility(position == items.size() - 1 ? View.INVISIBLE : View.VISIBLE);

        // ── Card click ─────────────────────────────────────────────────
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onScheduleClick(schedule);
        });

        // ── Overflow menu ──────────────────────────────────────────────
        h.ivOverflow.setOnClickListener(v -> showOverflowMenu(h.ivOverflow, schedule));
    }

    private void loadMedicationIcon(Context ctx, ImageView ivIcon, String drugName) {
        if (ivIcon == null || ctx == null) return;
        new Thread(() -> {
            try {
                com.emergency.patient.db.MedicationEntity med =
                    com.emergency.patient.db.AppDatabaseProvider.getInstance(ctx).medicationDao().findByName(drugName);
                if (med != null && med.imageUri != null && !med.imageUri.trim().isEmpty()) {
                    android.net.Uri uri = android.net.Uri.parse(med.imageUri);
                    android.graphics.Bitmap bitmap = null;
                    if (med.imageUri.startsWith("file://") || med.imageUri.startsWith("/")) {
                        String path = uri.getPath();
                        if (path != null) {
                            bitmap = android.graphics.BitmapFactory.decodeFile(path);
                        }
                    } else {
                        try {
                            bitmap = android.graphics.BitmapFactory.decodeStream(ctx.getContentResolver().openInputStream(uri));
                        } catch (Exception ignored) {}
                    }
                    if (bitmap != null) {
                        final android.graphics.Bitmap finalBmp = bitmap;
                        ((android.app.Activity) ctx).runOnUiThread(() -> {
                            ivIcon.setImageBitmap(finalBmp);
                            ivIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            ivIcon.setPadding(0, 0, 0, 0);
                            ivIcon.setColorFilter(null);
                        });
                        return;
                    }
                }
            } catch (Exception ignored) {}

            ((android.app.Activity) ctx).runOnUiThread(() -> {
                ivIcon.setImageResource(R.drawable.ic_nav_medication);
                ivIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                ivIcon.setColorFilter(android.graphics.Color.parseColor("#087F68"));
            });
        }).start();
    }

    private void applyStatusStyle(Context ctx, ViewHolder h, String status, long timeMillis) {
        long now = System.currentTimeMillis();
        boolean isFuture = timeMillis > now;

        switch (status) {
            case "TAKEN":
                h.viewDot.setBackground(ctx.getDrawable(R.drawable.bg_status_chip_taken));
                h.tvStatusChip.setText("✓ Taken");
                h.tvStatusChip.setTextColor(Color.WHITE);
                h.tvStatusChip.setBackground(ctx.getDrawable(R.drawable.bg_status_chip_taken));
                break;

            case "MISSED":
                h.viewDot.setBackground(ctx.getDrawable(R.drawable.bg_status_chip_missed));
                h.tvStatusChip.setText("✕ Missed");
                h.tvStatusChip.setTextColor(Color.parseColor("#D92D32"));
                h.tvStatusChip.setBackground(ctx.getDrawable(R.drawable.bg_status_chip_missed));
                break;

            case "SKIPPED":
                h.viewDot.setBackground(ctx.getDrawable(R.drawable.bg_status_chip_upcoming));
                h.tvStatusChip.setText("— Skipped");
                h.tvStatusChip.setTextColor(Color.parseColor("#687790"));
                h.tvStatusChip.setBackground(ctx.getDrawable(R.drawable.bg_status_chip_upcoming));
                break;

            case "PENDING":
            default:
                if (isFuture) {
                    h.viewDot.setBackground(ctx.getDrawable(R.drawable.bg_status_chip_upcoming));
                    h.tvStatusChip.setText("⏰ Upcoming");
                    h.tvStatusChip.setTextColor(Color.parseColor("#687790"));
                    h.tvStatusChip.setBackground(ctx.getDrawable(R.drawable.bg_status_chip_upcoming));
                } else {
                    h.viewDot.setBackground(ctx.getDrawable(R.drawable.bg_status_chip_pending));
                    h.tvStatusChip.setText("◷ Pending");
                    h.tvStatusChip.setTextColor(Color.parseColor("#D97706"));
                    h.tvStatusChip.setBackground(ctx.getDrawable(R.drawable.bg_status_chip_pending));
                }
                break;
        }
    }

    private void showOverflowMenu(View anchor, MedicationScheduleEntity schedule) {
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        popup.getMenu().add(0, 0, 0, "✅ Mark as Taken");
        popup.getMenu().add(0, 1, 1, "❌ Mark as Missed");
        popup.getMenu().add(0, 2, 2, "⏳ Mark as Pending");
        popup.getMenu().add(0, 3, 3, "🕒 Edit Reminder Time");
        popup.getMenu().add(0, 4, 4, "— Skip this dose");

        popup.setOnMenuItemClickListener(item -> {
            if (listener == null) return false;
            switch (item.getItemId()) {
                case 0: listener.onMarkStatus(schedule, "TAKEN"); return true;
                case 1: listener.onMarkStatus(schedule, "MISSED"); return true;
                case 2: listener.onMarkStatus(schedule, "PENDING"); return true;
                case 3: listener.onEditTime(schedule); return true;
                case 4: listener.onMarkStatus(schedule, "SKIPPED"); return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTimeHour, tvTimeAmPm;
        View viewDot, viewLine;
        ImageView ivMedIcon, ivOverflow;
        TextView tvMedName, tvDosage, tvAdminChip, tvStatusChip;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimeHour   = itemView.findViewById(R.id.tvScheduleTimeHour);
            tvTimeAmPm   = itemView.findViewById(R.id.tvScheduleTimeAmPm);
            viewDot      = itemView.findViewById(R.id.viewTimelineDot);
            viewLine     = itemView.findViewById(R.id.viewTimelineLine);
            ivMedIcon    = itemView.findViewById(R.id.ivMedIcon);
            ivOverflow   = itemView.findViewById(R.id.ivMedOverflow);
            tvMedName    = itemView.findViewById(R.id.tvMedName);
            tvDosage     = itemView.findViewById(R.id.tvMedDosage);
            tvAdminChip  = itemView.findViewById(R.id.tvAdminChip);
            tvStatusChip = itemView.findViewById(R.id.tvStatusChip);
        }
    }
}
