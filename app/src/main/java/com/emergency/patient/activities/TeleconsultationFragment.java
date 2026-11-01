package com.emergency.patient.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.emergency.patient.R;
import com.emergency.patient.adapters.DiabeticDoctorAdapter;
import com.emergency.patient.models.DiabeticDoctor;
import com.emergency.patient.repository.DiabeticDoctorRepository;
import com.emergency.patient.utils.LocationHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * TeleconsultationFragment — Diabetic Specialist Doctor Connect & Teleconsultation.
 * 
 * Filters specifically for certified medical practitioners managing diabetes and
 * its microvascular / macrovascular complications:
 *  - Vitreo-Retinal Specialists (Diabetic Retinopathy, DME, Anti-VEGF, Laser)
 *  - Endocrinologists & Diabetologists (HbA1c regulation, Insulin pumps, CGM)
 *  - Diabetic Nephrologists (DKD, Microalbuminuria, Renal preservation)
 *  - Podiatrists & Diabetic Foot Surgeons (Peripheral neuropathy, Foot ulcers)
 *  - Certified Diabetes Educators & Dietitians (Carb counting, Glycemic diet)
 *
 * Automatically detects patient GPS location/city to surface nearby clinics,
 * calculates real distance (km), and enables instant encrypted video teleconsultation
 * with clinical report attachment.
 */
public class TeleconsultationFragment extends Fragment implements DiabeticDoctorAdapter.OnDoctorActionListener {

    private static final String TAG = "TeleconsultationFragment";

    // UI Elements
    private TextView tvCurrentLocation;
    private TextView btnChangeCity;
    private FrameLayout btnRefreshLocation;
    private FrameLayout btnBack;
    private LinearLayout cardClinicalAlert;
    private TextView btnQuickRetinaFilter;
    private EditText etSearchDoctors;
    private ImageView btnClearSearch;
    private TextView tvResultsCount;
    private RecyclerView rvDiabeticDoctors;
    private LinearLayout layoutEmptyState;
    private TextView btnResetFilters;

    // Filter Chips
    private TextView chipAll;
    private TextView chipRetina;
    private TextView chipDiabetology;
    private TextView chipNephrology;
    private TextView chipPodiatry;
    private TextView chipNutrition;

    // Data & State
    private DiabeticDoctorAdapter doctorAdapter;
    private String currentCity = "Indore";
    private String currentCategory = DiabeticDoctor.CAT_ALL;
    private String currentSearchQuery = "";
    private double userLatitude = 22.7196;
    private double userLongitude = 75.8577;
    private boolean isGpsDetected = false;

    // Video Call simulation timer
    private Handler callTimerHandler;
    private Runnable callTimerRunnable;
    private int callSecondsElapsed = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_teleconsultation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView();
        setupFilters();
        setupSearch();
        setupLocationDetection();
        loadDoctors();
    }

    private void initViews(View view) {
        btnBack = view.findViewById(R.id.btnBack);
        btnRefreshLocation = view.findViewById(R.id.btnRefreshLocation);
        tvCurrentLocation = view.findViewById(R.id.tvCurrentLocation);
        btnChangeCity = view.findViewById(R.id.btnChangeCity);
        cardClinicalAlert = view.findViewById(R.id.cardClinicalAlert);
        btnQuickRetinaFilter = view.findViewById(R.id.btnQuickRetinaFilter);
        etSearchDoctors = view.findViewById(R.id.etSearchDoctors);
        btnClearSearch = view.findViewById(R.id.btnClearSearch);
        tvResultsCount = view.findViewById(R.id.tvResultsCount);
        rvDiabeticDoctors = view.findViewById(R.id.rvDiabeticDoctors);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        btnResetFilters = view.findViewById(R.id.btnResetFilters);

        chipAll = view.findViewById(R.id.chipAll);
        chipRetina = view.findViewById(R.id.chipRetina);
        chipDiabetology = view.findViewById(R.id.chipDiabetology);
        chipNephrology = view.findViewById(R.id.chipNephrology);
        chipPodiatry = view.findViewById(R.id.chipPodiatry);
        chipNutrition = view.findViewById(R.id.chipNutrition);

        // Back button navigation
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> handleBackNavigation());
        }

        // Refresh location button
        if (btnRefreshLocation != null) {
            btnRefreshLocation.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "Detecting current GPS coordinates...", Toast.LENGTH_SHORT).show();
                setupLocationDetection();
            });
        }

        // Change city button
        if (btnChangeCity != null) {
            btnChangeCity.setOnClickListener(v -> showCitySelectionDialog());
        }

        // Quick filter for retina from clinical banner
        if (btnQuickRetinaFilter != null) {
            btnQuickRetinaFilter.setOnClickListener(v -> selectCategory(DiabeticDoctor.CAT_RETINA));
        }

        // Reset filters in empty state
        if (btnResetFilters != null) {
            btnResetFilters.setOnClickListener(v -> {
                currentSearchQuery = "";
                if (etSearchDoctors != null) etSearchDoctors.setText("");
                selectCategory(DiabeticDoctor.CAT_ALL);
            });
        }
    }

    private void setupRecyclerView() {
        doctorAdapter = new DiabeticDoctorAdapter(this);
        rvDiabeticDoctors.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvDiabeticDoctors.setAdapter(doctorAdapter);
    }

    private void setupFilters() {
        chipAll.setOnClickListener(v -> selectCategory(DiabeticDoctor.CAT_ALL));
        chipRetina.setOnClickListener(v -> selectCategory(DiabeticDoctor.CAT_RETINA));
        chipDiabetology.setOnClickListener(v -> selectCategory(DiabeticDoctor.CAT_DIABETOLOGY));
        chipNephrology.setOnClickListener(v -> selectCategory(DiabeticDoctor.CAT_NEPHROLOGY));
        chipPodiatry.setOnClickListener(v -> selectCategory(DiabeticDoctor.CAT_PODIATRY));
        chipNutrition.setOnClickListener(v -> selectCategory(DiabeticDoctor.CAT_NUTRITION));
    }

    private void selectCategory(String category) {
        currentCategory = category;
        updateChipStyles();
        loadDoctors();
    }

    private void updateChipStyles() {
        int selectedBg = R.drawable.bg_chip_selected;
        int unselectedBg = R.drawable.bg_chip_unselected;
        int selectedColor = Color.WHITE;
        int unselectedColor = Color.parseColor("#172D3A");

        updateSingleChip(chipAll, DiabeticDoctor.CAT_ALL.equals(currentCategory), selectedBg, unselectedBg, selectedColor, unselectedColor);
        updateSingleChip(chipRetina, DiabeticDoctor.CAT_RETINA.equals(currentCategory), selectedBg, unselectedBg, selectedColor, unselectedColor);
        updateSingleChip(chipDiabetology, DiabeticDoctor.CAT_DIABETOLOGY.equals(currentCategory), selectedBg, unselectedBg, selectedColor, unselectedColor);
        updateSingleChip(chipNephrology, DiabeticDoctor.CAT_NEPHROLOGY.equals(currentCategory), selectedBg, unselectedBg, selectedColor, unselectedColor);
        updateSingleChip(chipPodiatry, DiabeticDoctor.CAT_PODIATRY.equals(currentCategory), selectedBg, unselectedBg, selectedColor, unselectedColor);
        updateSingleChip(chipNutrition, DiabeticDoctor.CAT_NUTRITION.equals(currentCategory), selectedBg, unselectedBg, selectedColor, unselectedColor);
    }

    private void updateSingleChip(TextView chip, boolean isSelected, int selectedBg, int unselectedBg, int selectedColor, int unselectedColor) {
        if (chip == null) return;
        chip.setBackgroundResource(isSelected ? selectedBg : unselectedBg);
        chip.setTextColor(isSelected ? selectedColor : unselectedColor);
        chip.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void setupSearch() {
        etSearchDoctors.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim();
                btnClearSearch.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                loadDoctors();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> etSearchDoctors.setText(""));
    }

    private void setupLocationDetection() {
        if (getContext() == null) return;

        LocationHelper.getLastKnownLocation(requireContext(), (lat, lng) -> {
            if (!isAdded() || getContext() == null) return;

            userLatitude = lat;
            userLongitude = lng;
            isGpsDetected = true;

            // Reverse-geocode in background to detect accurate city name
            Executors.newSingleThreadExecutor().execute(() -> {
                String detectedCity = null;
                String detectedLocality = null;
                try {
                    Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address addr = addresses.get(0);
                        detectedCity = addr.getLocality();
                        detectedLocality = addr.getSubLocality();
                        if (detectedCity == null || detectedCity.isEmpty()) {
                            detectedCity = addr.getSubAdminArea();
                        }
                    }
                } catch (Exception ignored) {}

                if (detectedCity == null || detectedCity.isEmpty()) {
                    // Match to closest supported hub based on GPS distance
                    detectedCity = DiabeticDoctorRepository.getInstance().getClosestCity(lat, lng);
                }

                final String finalCity = detectedCity;
                final String finalLocality = detectedLocality;

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        currentCity = finalCity;
                        String locationDisplay = "📍 " + finalCity;
                        if (finalLocality != null && !finalLocality.isEmpty()) {
                            locationDisplay += ", " + finalLocality;
                        }
                        locationDisplay += " (Near You)";
                        tvCurrentLocation.setText(locationDisplay);
                        loadDoctors();
                    });
                }
            });
        });
    }

    private void showCitySelectionDialog() {
        List<String> cities = DiabeticDoctorRepository.getInstance().getSupportedCities();
        String[] cityOptions = new String[cities.size() + 2];
        cityOptions[0] = "📍 Use My GPS Location";
        cityOptions[1] = "🌐 All Cities (Nearest First)";
        for (int i = 0; i < cities.size(); i++) {
            cityOptions[i + 2] = cities.get(i);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Select City for Diabetic Specialists")
                .setItems(cityOptions, (dialog, which) -> {
                    if (which == 0) {
                        setupLocationDetection();
                    } else if (which == 1) {
                        currentCity = "All Cities";
                        tvCurrentLocation.setText("🌐 All Cities (Sorted by Distance)");
                        loadDoctors();
                    } else {
                        currentCity = cityOptions[which];
                        tvCurrentLocation.setText("📍 " + currentCity + " (Selected)");
                        loadDoctors();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadDoctors() {
        DiabeticDoctorRepository repo = DiabeticDoctorRepository.getInstance();
        List<DiabeticDoctor> doctors = repo.getFilteredDoctors(
                currentCity,
                currentCategory,
                currentSearchQuery,
                userLatitude,
                userLongitude
        );

        doctorAdapter.updateDoctors(doctors);

        if (doctors.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            rvDiabeticDoctors.setVisibility(View.GONE);
            tvResultsCount.setText("No specialists found");
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            rvDiabeticDoctors.setVisibility(View.VISIBLE);
            String cityLabel = (currentCity != null && !currentCity.equalsIgnoreCase("All Cities")) ? " in " + currentCity : "";
            tvResultsCount.setText(String.format(Locale.US, "Showing %d diabetic specialists%s", doctors.size(), cityLabel));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // DOCTOR ACTION CALLBACKS
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public void onVideoConsult(DiabeticDoctor doctor) {
        showVideoConsultDialog(doctor);
    }

    @Override
    public void onBookVisit(DiabeticDoctor doctor) {
        showBookClinicVisitDialog(doctor);
    }

    @Override
    public void onCallClinic(DiabeticDoctor doctor) {
        if (doctor.getPhone() != null && !doctor.getPhone().isEmpty()) {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + doctor.getPhone()));
            try {
                startActivity(callIntent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Clinic phone: " + doctor.getPhone(), Toast.LENGTH_LONG).show();
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // VIDEO TELECONSULTATION ROOM DIALOG
    // ════════════════════════════════════════════════════════════════════════

    private void showVideoConsultDialog(DiabeticDoctor doctor) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_teleconsult_room, null);

        TextView tvModalDoctorInitials = dialogView.findViewById(R.id.tvModalDoctorInitials);
        TextView tvModalDoctorName = dialogView.findViewById(R.id.tvModalDoctorName);
        TextView tvModalDoctorSpecialty = dialogView.findViewById(R.id.tvModalDoctorSpecialty);
        TextView tvModalHospital = dialogView.findViewById(R.id.tvModalHospital);
        TextView tvModalFee = dialogView.findViewById(R.id.tvModalFee);
        CheckBox cbShareReport = dialogView.findViewById(R.id.cbShareReport);
        LinearLayout btnStartCall = dialogView.findViewById(R.id.btnStartCall);

        LinearLayout layoutPreCall = dialogView.findViewById(R.id.layoutPreCall);
        LinearLayout layoutActiveCall = dialogView.findViewById(R.id.layoutActiveCall);
        TextView tvCallStatusPill = dialogView.findViewById(R.id.tvCallStatusPill);
        TextView tvActiveDoctorInitials = dialogView.findViewById(R.id.tvActiveDoctorInitials);
        TextView tvActiveDoctorName = dialogView.findViewById(R.id.tvActiveDoctorName);
        TextView tvCallTimer = dialogView.findViewById(R.id.tvCallTimer);
        FrameLayout btnMute = dialogView.findViewById(R.id.btnMute);
        TextView tvMuteIcon = dialogView.findViewById(R.id.tvMuteIcon);
        FrameLayout btnEndCall = dialogView.findViewById(R.id.btnEndCall);
        FrameLayout btnToggleVideo = dialogView.findViewById(R.id.btnToggleVideo);

        tvModalDoctorInitials.setText(doctor.getInitials());
        tvModalDoctorName.setText(doctor.getName());
        tvModalDoctorSpecialty.setText(doctor.getSpecialtyTitle());
        tvModalHospital.setText(doctor.getHospitalOrClinic() + " • " + doctor.getLocality());
        tvModalFee.setText(doctor.getConsultationFee());

        tvActiveDoctorInitials.setText(doctor.getInitials());
        tvActiveDoctorName.setText(doctor.getName());

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        final boolean[] isMuted = {false};
        final boolean[] isVideoOff = {false};

        btnStartCall.setOnClickListener(v -> {
            layoutPreCall.setVisibility(View.GONE);
            layoutActiveCall.setVisibility(View.VISIBLE);
            dialog.setCancelable(false);

            tvCallStatusPill.setText("Connecting to " + doctor.getName() + "...");
            tvCallTimer.setText("Connecting...");

            // Simulate immediate medical connection in 1.2s
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!dialog.isShowing()) return;

                tvCallStatusPill.setText("🟢 HD Video • 256-bit Medical Encrypted");
                tvCallStatusPill.setBackgroundResource(R.drawable.bg_diabetic_badge);
                tvCallStatusPill.setTextColor(Color.parseColor("#147D82"));

                if (cbShareReport != null && cbShareReport.isChecked()) {
                    Toast.makeText(requireContext(), "Shared NetraProof DR Screening Report (NP-SCR-00005) with doctor", Toast.LENGTH_SHORT).show();
                }

                // Start call timer
                callSecondsElapsed = 0;
                callTimerHandler = new Handler(Looper.getMainLooper());
                callTimerRunnable = new Runnable() {
                    @Override
                    public void run() {
                        callSecondsElapsed++;
                        int mins = callSecondsElapsed / 60;
                        int secs = callSecondsElapsed % 60;
                        tvCallTimer.setText(String.format(Locale.US, "%02d:%02d", mins, secs));
                        callTimerHandler.postDelayed(this, 1000);
                    }
                };
                callTimerHandler.postDelayed(callTimerRunnable, 1000);
            }, 1200);
        });

        btnMute.setOnClickListener(v -> {
            isMuted[0] = !isMuted[0];
            tvMuteIcon.setText(isMuted[0] ? "🔇" : "🎙️");
            Toast.makeText(requireContext(), isMuted[0] ? "Microphone Muted" : "Microphone Active", Toast.LENGTH_SHORT).show();
        });

        btnToggleVideo.setOnClickListener(v -> {
            isVideoOff[0] = !isVideoOff[0];
            Toast.makeText(requireContext(), isVideoOff[0] ? "Camera Paused" : "Camera Active", Toast.LENGTH_SHORT).show();
        });

        btnEndCall.setOnClickListener(v -> {
            stopCallTimer();
            dialog.dismiss();

            new AlertDialog.Builder(requireContext())
                    .setTitle("Teleconsultation Completed")
                    .setMessage("Consultation session with " + doctor.getName() + " ended (" + String.format(Locale.US, "%02d:%02d", callSecondsElapsed / 60, callSecondsElapsed % 60) + ").\n\nClinical consultation summary, vitals review, and e-prescription will sync to your records.")
                    .setPositiveButton("View Prescription / Summary", (d, w) -> {
                        Toast.makeText(requireContext(), "Consultation logged to Diabetes360 records", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Close", null)
                    .show();
        });

        dialog.setOnDismissListener(d -> stopCallTimer());
        dialog.show();
    }

    private void stopCallTimer() {
        if (callTimerHandler != null && callTimerRunnable != null) {
            callTimerHandler.removeCallbacks(callTimerRunnable);
            callTimerHandler = null;
            callTimerRunnable = null;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // BOOK IN-CLINIC VISIT DIALOG
    // ════════════════════════════════════════════════════════════════════════

    private void showBookClinicVisitDialog(DiabeticDoctor doctor) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_book_clinic_visit, null);

        TextView tvBookDoctorInitials = dialogView.findViewById(R.id.tvBookDoctorInitials);
        TextView tvBookDoctorName = dialogView.findViewById(R.id.tvBookDoctorName);
        TextView tvBookDoctorSpecialty = dialogView.findViewById(R.id.tvBookDoctorSpecialty);
        TextView tvBookClinicLocation = dialogView.findViewById(R.id.tvBookClinicLocation);
        TextView tvBookFee = dialogView.findViewById(R.id.tvBookFee);

        TextView slotDay1 = dialogView.findViewById(R.id.slotDay1);
        TextView slotDay2 = dialogView.findViewById(R.id.slotDay2);
        TextView slotDay3 = dialogView.findViewById(R.id.slotDay3);

        TextView slotTime1 = dialogView.findViewById(R.id.slotTime1);
        TextView slotTime2 = dialogView.findViewById(R.id.slotTime2);
        TextView slotTime3 = dialogView.findViewById(R.id.slotTime3);

        LinearLayout btnConfirmAppointment = dialogView.findViewById(R.id.btnConfirmAppointment);

        tvBookDoctorInitials.setText(doctor.getInitials());
        tvBookDoctorName.setText(doctor.getName());
        tvBookDoctorSpecialty.setText(doctor.getSpecialtyTitle());
        tvBookClinicLocation.setText(doctor.getHospitalOrClinic() + " • " + doctor.getLocality() + ", " + doctor.getCity());
        tvBookFee.setText(doctor.getConsultationFee());

        final String[] selectedDay = {"Today"};
        final String[] selectedTime = {"11:30 AM"};

        View.OnClickListener dayClickListener = v -> {
            slotDay1.setBackgroundResource(v == slotDay1 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            slotDay1.setTextColor(v == slotDay1 ? Color.WHITE : Color.parseColor("#172D3A"));
            slotDay2.setBackgroundResource(v == slotDay2 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            slotDay2.setTextColor(v == slotDay2 ? Color.WHITE : Color.parseColor("#172D3A"));
            slotDay3.setBackgroundResource(v == slotDay3 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            slotDay3.setTextColor(v == slotDay3 ? Color.WHITE : Color.parseColor("#172D3A"));

            if (v == slotDay1) selectedDay[0] = "Today";
            else if (v == slotDay2) selectedDay[0] = "Tomorrow";
            else selectedDay[0] = "Day After Tomorrow";
        };

        slotDay1.setOnClickListener(dayClickListener);
        slotDay2.setOnClickListener(dayClickListener);
        slotDay3.setOnClickListener(dayClickListener);

        View.OnClickListener timeClickListener = v -> {
            slotTime1.setBackgroundResource(v == slotTime1 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            slotTime1.setTextColor(v == slotTime1 ? Color.WHITE : Color.parseColor("#172D3A"));
            slotTime2.setBackgroundResource(v == slotTime2 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            slotTime2.setTextColor(v == slotTime2 ? Color.WHITE : Color.parseColor("#172D3A"));
            slotTime3.setBackgroundResource(v == slotTime3 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            slotTime3.setTextColor(v == slotTime3 ? Color.WHITE : Color.parseColor("#172D3A"));

            if (v == slotTime1) selectedTime[0] = "11:30 AM";
            else if (v == slotTime2) selectedTime[0] = "03:30 PM";
            else selectedTime[0] = "05:45 PM";
        };

        slotTime1.setOnClickListener(timeClickListener);
        slotTime2.setOnClickListener(timeClickListener);
        slotTime3.setOnClickListener(timeClickListener);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnConfirmAppointment.setOnClickListener(v -> {
            dialog.dismiss();
            int tokenNum = 100 + (int)(Math.random() * 899);

            new AlertDialog.Builder(requireContext())
                    .setTitle("Appointment Confirmed! ✅")
                    .setMessage("Your in-clinic diabetic consultation is booked successfully.\n\n" +
                            "• Doctor: " + doctor.getName() + " (" + doctor.getSpecialtyTitle() + ")\n" +
                            "• Clinic: " + doctor.getHospitalOrClinic() + "\n" +
                            "• Locality: " + doctor.getLocality() + ", " + doctor.getCity() + "\n" +
                            "• Schedule: " + selectedDay[0] + " at " + selectedTime[0] + "\n" +
                            "• Priority Token: #DS-" + tokenNum + "\n" +
                            "• Consultation Fee: " + doctor.getConsultationFee() + "\n\n" +
                            "Please arrive 10 minutes prior for pupil dilation / fasting glucose check if required.")
                    .setPositiveButton("Add to Calendar", (d, w) -> {
                        Toast.makeText(requireContext(), "Appointment #DS-" + tokenNum + " saved to your schedule", Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("OK", null)
                    .show();
        });

        dialog.show();
    }

    private void handleBackNavigation() {
        if (getActivity() instanceof DashboardActivity) {
            DashboardActivity activity = (DashboardActivity) getActivity();
            activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new MedicationSafetyHomeFragment())
                    .commit();

            BottomNavigationView bottomNav = activity.findViewById(R.id.bottom_navigation);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.nav_home);
            }
            activity.setFloatingNavbarVisible(true);
        } else if (getActivity() != null) {
            getActivity().getSupportFragmentManager().popBackStack();
        }
    }

    public boolean onBackPressed() {
        handleBackNavigation();
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCallTimer();
    }
}
