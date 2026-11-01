package com.emergency.patient.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.emergency.patient.R;
import com.emergency.patient.models.DiabeticDoctor;

import java.util.ArrayList;
import java.util.List;

/**
 * DiabeticDoctorAdapter — RecyclerView adapter for verified diabetic specialists.
 */
public class DiabeticDoctorAdapter extends RecyclerView.Adapter<DiabeticDoctorAdapter.DoctorViewHolder> {

    public interface OnDoctorActionListener {
        void onVideoConsult(DiabeticDoctor doctor);
        void onBookVisit(DiabeticDoctor doctor);
        void onCallClinic(DiabeticDoctor doctor);
    }

    private final List<DiabeticDoctor> doctorList = new ArrayList<>();
    private final OnDoctorActionListener actionListener;

    public DiabeticDoctorAdapter(OnDoctorActionListener listener) {
        this.actionListener = listener;
    }

    public void updateDoctors(List<DiabeticDoctor> newDoctors) {
        doctorList.clear();
        if (newDoctors != null) {
            doctorList.addAll(newDoctors);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DoctorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_diabetic_doctor_card, parent, false);
        return new DoctorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DoctorViewHolder holder, int position) {
        DiabeticDoctor doctor = doctorList.get(position);
        holder.bind(doctor, actionListener);
    }

    @Override
    public int getItemCount() {
        return doctorList.size();
    }

    static class DoctorViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvDoctorInitials;
        private final View viewOnlineIndicator;
        private final TextView tvDoctorName;
        private final TextView tvSpecialtyTitle;
        private final TextView tvQualifications;
        private final TextView tvHospitalAndLocality;
        private final TextView tvDistance;
        private final TextView tvRating;
        private final TextView tvExperience;
        private final TextView tvHighlightBadge;
        private final LinearLayout layoutConditionsTreated;
        private final TextView tvNextSlot;
        private final TextView tvFee;
        private final LinearLayout btnVideoConsult;
        private final LinearLayout btnBookVisit;
        private final FrameLayout btnCallClinic;

        public DoctorViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDoctorInitials = itemView.findViewById(R.id.tvDoctorInitials);
            viewOnlineIndicator = itemView.findViewById(R.id.viewOnlineIndicator);
            tvDoctorName = itemView.findViewById(R.id.tvDoctorName);
            tvSpecialtyTitle = itemView.findViewById(R.id.tvSpecialtyTitle);
            tvQualifications = itemView.findViewById(R.id.tvQualifications);
            tvHospitalAndLocality = itemView.findViewById(R.id.tvHospitalAndLocality);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            tvRating = itemView.findViewById(R.id.tvRating);
            tvExperience = itemView.findViewById(R.id.tvExperience);
            tvHighlightBadge = itemView.findViewById(R.id.tvHighlightBadge);
            layoutConditionsTreated = itemView.findViewById(R.id.layoutConditionsTreated);
            tvNextSlot = itemView.findViewById(R.id.tvNextSlot);
            tvFee = itemView.findViewById(R.id.tvFee);
            btnVideoConsult = itemView.findViewById(R.id.btnVideoConsult);
            btnBookVisit = itemView.findViewById(R.id.btnBookVisit);
            btnCallClinic = itemView.findViewById(R.id.btnCallClinic);
        }

        public void bind(DiabeticDoctor doc, OnDoctorActionListener listener) {
            Context context = itemView.getContext();

            tvDoctorInitials.setText(doc.getInitials());
            viewOnlineIndicator.setVisibility(doc.isOnlineForTeleconsult() ? View.VISIBLE : View.GONE);
            tvDoctorName.setText(doc.getName());
            tvSpecialtyTitle.setText(doc.getSpecialtyTitle());
            tvQualifications.setText(doc.getQualification());
            tvHospitalAndLocality.setText(doc.getHospitalOrClinic() + " • " + doc.getLocality() + ", " + doc.getCity());
            tvDistance.setText(doc.getFormattedDistance());

            tvRating.setText(String.format(java.util.Locale.US, "⭐ %.1f (%d reviews)", doc.getRating(), doc.getReviewCount()));
            tvExperience.setText(doc.getExperienceYears() + " yrs exp");

            if (doc.getBadgeText() != null && !doc.getBadgeText().isEmpty()) {
                tvHighlightBadge.setVisibility(View.VISIBLE);
                tvHighlightBadge.setText(doc.getBadgeText());
            } else {
                tvHighlightBadge.setVisibility(View.GONE);
            }

            tvNextSlot.setText("🟢 Next Slot: " + doc.getNextSlot());
            tvFee.setText(doc.getConsultationFee());

            // Build Condition Chips
            layoutConditionsTreated.removeAllViews();
            List<String> conditions = doc.getConditionsTreated();
            int maxTags = Math.min(3, conditions.size());
            for (int i = 0; i < maxTags; i++) {
                String condition = conditions.get(i);
                TextView tagView = new TextView(context);
                tagView.setText(condition);
                tagView.setTextSize(10.5f);
                tagView.setTextColor(Color.parseColor("#147D82"));
                tagView.setBackgroundResource(R.drawable.bg_condition_tag);
                tagView.setPadding(dpToPx(context, 8), dpToPx(context, 3), dpToPx(context, 8), dpToPx(context, 3));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                lp.setMarginEnd(dpToPx(context, 6));
                tagView.setLayoutParams(lp);

                layoutConditionsTreated.addView(tagView);
            }

            // Click Handlers
            btnVideoConsult.setOnClickListener(v -> {
                if (listener != null) listener.onVideoConsult(doc);
            });

            btnBookVisit.setOnClickListener(v -> {
                if (listener != null) listener.onBookVisit(doc);
            });

            btnCallClinic.setOnClickListener(v -> {
                if (listener != null) listener.onCallClinic(doc);
            });
        }

        private int dpToPx(Context context, int dp) {
            float density = context.getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        }
    }
}

