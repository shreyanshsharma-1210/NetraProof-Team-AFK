package com.emergency.patient.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.emergency.patient.R;
import com.emergency.patient.db.MedicationScheduleEntity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private final List<MedicationScheduleEntity> schedules;
    private boolean isEditMode = false;
    private OnScheduleClickListener clickListener;

    public interface OnScheduleClickListener {
        void onScheduleClick(MedicationScheduleEntity schedule);
        void onEditTime(MedicationScheduleEntity schedule);
    }

    public ScheduleAdapter(List<MedicationScheduleEntity> schedules, OnScheduleClickListener listener) {
        this.schedules = schedules;
        this.clickListener = listener;
    }

    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MedicationScheduleEntity item = schedules.get(position);
        
        holder.drugNameText.setText(item.drugName);
        
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(item.timeMillis);
        
        SimpleDateFormat timeSdf = new SimpleDateFormat("hh:mm", Locale.getDefault());
        SimpleDateFormat amPmSdf = new SimpleDateFormat("a", Locale.getDefault());
        
        holder.timeText.setText(timeSdf.format(cal.getTime()));

        String status = item.status != null ? item.status : "PENDING";
        
        // Status badge — always visible outside edit mode;
        // also shown in edit mode for TAKEN rows (they cannot be edited)
        boolean isTaken = status.equalsIgnoreCase("TAKEN");

        if (isEditMode) {
            holder.statusContainer.setVisibility(isTaken ? View.VISIBLE : View.GONE);
            holder.editTimeButton.setVisibility(isTaken ? View.GONE : View.VISIBLE);
            if (!isTaken) {
                holder.editTimeButton.setOnClickListener(v -> {
                    if (clickListener != null) clickListener.onEditTime(item);
                });
                holder.editTimeButton.setAlpha(1.0f);
            }
        } else {
            holder.editTimeButton.setVisibility(View.GONE);
            holder.statusContainer.setVisibility(View.VISIBLE);
        }

        // Clicking the item or status chip allows changing status (Taken / Missed / Pending / Edit Time)
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onScheduleClick(item);
        });
        holder.statusContainer.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onScheduleClick(item);
        });

        if (status.equalsIgnoreCase("TAKEN")) {
            holder.statusText.setText("Taken");
            holder.statusText.setTextColor(Color.WHITE);
            holder.statusContainer.setCardBackgroundColor(Color.parseColor("#4CAF50")); // Material Green
        } else if (status.equalsIgnoreCase("MISSED")) {
            holder.statusText.setText("Missed");
            holder.statusText.setTextColor(Color.WHITE);
            holder.statusContainer.setCardBackgroundColor(Color.parseColor("#FF9800")); // Material Orange
        } else if (status.equalsIgnoreCase("SKIPPED")
                || status.equalsIgnoreCase("DISMISSED")) {
            holder.statusText.setText("Skipped");
            holder.statusText.setTextColor(Color.WHITE);
            holder.statusContainer.setCardBackgroundColor(Color.parseColor("#F44336")); // Material Red
        } else if (status.equalsIgnoreCase("SNOOZED")) {
            holder.statusText.setText("Snoozed");
            holder.statusText.setTextColor(Color.WHITE);
            holder.statusContainer.setCardBackgroundColor(Color.parseColor("#9C27B0")); // Material Purple
        } else {
            holder.statusText.setText("Pending");
            holder.statusText.setTextColor(Color.parseColor("#1976D2")); // Material Blue
            holder.statusContainer.setCardBackgroundColor(Color.parseColor("#E3F2FD")); // Very Light Blue
        }
    }

    @Override
    public int getItemCount() {
        return schedules == null ? 0 : schedules.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView drugNameText, timeText, dosageText, timeRangeText, statusText;
        androidx.cardview.widget.CardView statusContainer;
        ImageView editTimeButton;

        ViewHolder(View view) {
            super(view);
            drugNameText = view.findViewById(R.id.drugNameText);
            timeText = view.findViewById(R.id.timeText);
            dosageText = view.findViewById(R.id.dosageText);
            timeRangeText = view.findViewById(R.id.timeRangeText);
            statusText = view.findViewById(R.id.statusText);
            statusContainer = view.findViewById(R.id.statusContainer);
            editTimeButton = view.findViewById(R.id.editTimeButton);
        }
    }
}
