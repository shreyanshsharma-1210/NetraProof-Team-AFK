package com.emergency.patient.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.emergency.patient.R;
import com.emergency.patient.adapters.CalendarAdapter;
import com.emergency.patient.adapters.MedicationAdapter;
import com.emergency.patient.adapters.ScheduleAdapter;
import com.emergency.patient.adapters.ScheduleTimelineAdapter;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.MedicationEntity;
import com.emergency.patient.db.MedicationScheduleEntity;
import com.emergency.patient.network.FirebaseRepository;
import com.emergency.patient.scheduling.ActionReceiver;
import com.emergency.patient.scheduling.AdherenceStateValidator;
import com.emergency.patient.scheduling.AdherenceStateValidator.EditVerdict;
import com.emergency.patient.scheduling.AdherenceStateValidator.ValidationResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MedicationDashboardFragment extends Fragment {

    // ─── View References ──────────────────────────────────────────────────────

    // Header & Hero
    private View settingsIcon, btnNotification, avatarContainer;
    private TextView tvMedHeroSubtitle, monthYearText;
    private View btnDateToday, btnTakeAllDue;

    // DDI banner
    private View cardDdiBanner, btnDdiViewDetails;
    private TextView tvDdiBannerTitle, tvDdiBannerDesc;

    // Date Navigator
    private View btnDatePrev, btnDateNext;

    // Pagers & Lists
    private RecyclerView calendarRecyclerView;
    private RecyclerView scheduleRecyclerView;
    private RecyclerView medicationRecyclerView;

    // Empty states
    private View emptyScheduleContainer;
    private View emptyCabinetLayout;
    private View tvCabinetAddBtn;

    // Sections for entrance animations
    private View medHeaderSection, medHeroSection, medDateNavSection, medSummarySection;
    private View medScheduleHeaderSection, medQuickActionsSection;

    // Summary cards
    private TextView tvSummaryScheduledCount, tvSummaryPendingCount, tvSummaryMissedCount;

    // Quick actions
    private View btnMedQuickAdd, btnMedQuickScan, btnMedQuickInteractions, btnMedQuickRefill;

    // ─── Adapters ─────────────────────────────────────────────────────────────

    private CalendarAdapter calendarAdapter;
    private ScheduleTimelineAdapter scheduleTimelineAdapter;
    private MedicationAdapter medicationAdapter;

    // ─── State ────────────────────────────────────────────────────────────────

    private Calendar selectedDate = Calendar.getInstance();
    private static final int REQUEST_CAMERA = 1001;
    private static final int REQUEST_GALLERY = 1002;
    private Uri currentPhotoUri;
    private List<String> accumulatedPhotoUris = new ArrayList<>();
    private boolean isEditMode = false;

    // Broadcast receiver for instant UI refresh after TAKE/SNOOZE/DISMISS
    private final BroadcastReceiver scheduleUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadData();
        }
    };

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_medication_dashboard, container, false);
        bindViews(view);
        setupCalendar();
        setupDateNavButtons();
        setupSettings();
        setupQuickActions();
        loadData();
        startEntranceAnimations();
        return view;
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
    public void onResume() {
        super.onResume();
        loadData();
    }

    // ─── View Binding ─────────────────────────────────────────────────────────

    private void bindViews(View v) {
        // Header & Hero
        btnNotification     = v.findViewById(R.id.btnNotification);
        avatarContainer     = v.findViewById(R.id.avatarContainer);
        settingsIcon        = v.findViewById(R.id.settingsIcon);
        tvMedHeroSubtitle   = v.findViewById(R.id.tvMedHeroSubtitle);

        // DDI Banner
        cardDdiBanner       = v.findViewById(R.id.card_ddi_banner);
        tvDdiBannerTitle    = v.findViewById(R.id.tv_ddi_banner_title);
        tvDdiBannerDesc     = v.findViewById(R.id.tv_ddi_banner_desc);
        btnDdiViewDetails   = v.findViewById(R.id.btn_ddi_view_details);

        // Date nav
        monthYearText       = v.findViewById(R.id.monthYearText);
        btnDatePrev         = v.findViewById(R.id.btnDatePrev);
        btnDateNext         = v.findViewById(R.id.btnDateNext);
        btnDateToday        = v.findViewById(R.id.btnDateToday);
        btnTakeAllDue       = v.findViewById(R.id.btnTakeAllDue);

        // RecyclerViews
        calendarRecyclerView   = v.findViewById(R.id.calendarRecyclerView);
        scheduleRecyclerView   = v.findViewById(R.id.scheduleRecyclerView);
        medicationRecyclerView = v.findViewById(R.id.medicationRecyclerView);

        // Empty states & buttons
        emptyScheduleContainer = v.findViewById(R.id.emptyScheduleLayout);
        emptyCabinetLayout     = v.findViewById(R.id.emptyCabinetLayout);
        tvCabinetAddBtn        = v.findViewById(R.id.tvCabinetAddBtn);
        if (tvCabinetAddBtn != null) {
            tvCabinetAddBtn.setOnClickListener(btn -> {
                animatePressScale(btn);
                showScanOptionsDialog();
            });
        }

        // Sections
        medHeaderSection       = v.findViewById(R.id.medHeaderSection);
        medHeroSection         = v.findViewById(R.id.medHeroSection);
        medDateNavSection      = v.findViewById(R.id.medDateNavSection);
        medSummarySection      = v.findViewById(R.id.medSummarySection);
        medScheduleHeaderSection = v.findViewById(R.id.medScheduleHeaderRow);
        medQuickActionsSection   = v.findViewById(R.id.medQuickActionsSection);

        // Summary counts
        tvSummaryScheduledCount = v.findViewById(R.id.tvSummaryScheduledCount);
        tvSummaryPendingCount   = v.findViewById(R.id.tvSummaryPendingCount);
        tvSummaryMissedCount    = v.findViewById(R.id.tvSummaryMissedCount);

        // Quick actions
        btnMedQuickAdd          = v.findViewById(R.id.btnQuickAddMed);
        btnMedQuickScan         = v.findViewById(R.id.btnQuickScanMed);
        btnMedQuickInteractions = v.findViewById(R.id.btnQuickInteractions);
        btnMedQuickRefill       = v.findViewById(R.id.btnQuickRefills);

        // Layout managers
        calendarRecyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        scheduleRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        if (medicationRecyclerView != null) {
            medicationRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        }
    }

    // ─── Calendar / Date Navigation ───────────────────────────────────────────

    private void setupCalendar() {
        List<Calendar> dates = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        for (int i = 0; i < 30; i++) {
            dates.add((Calendar) cal.clone());
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        calendarAdapter = new CalendarAdapter(dates, date -> {
            selectedDate = date;
            updateMonthYearText();
            loadSchedulesForSelectedDate();
        });
        calendarRecyclerView.setAdapter(calendarAdapter);
        calendarRecyclerView.scrollToPosition(7);
        updateMonthYearText();
    }

    private void setupDateNavButtons() {
        if (btnDatePrev != null) {
            btnDatePrev.setOnClickListener(v -> {
                animatePressScale(v);
                selectedDate.add(Calendar.WEEK_OF_YEAR, -1);
                updateMonthYearText();
                loadSchedulesForSelectedDate();
            });
        }
        if (btnDateNext != null) {
            btnDateNext.setOnClickListener(v -> {
                animatePressScale(v);
                selectedDate.add(Calendar.WEEK_OF_YEAR, 1);
                updateMonthYearText();
                loadSchedulesForSelectedDate();
            });
        }
        if (btnDateToday != null) {
            btnDateToday.setOnClickListener(v -> {
                animatePressScale(v);
                selectedDate = Calendar.getInstance();
                updateMonthYearText();
                loadSchedulesForSelectedDate();
                // Scroll calendar strip to today
                if (calendarRecyclerView != null) calendarRecyclerView.scrollToPosition(7);
            });
        }
        if (btnTakeAllDue != null) {
            btnTakeAllDue.setOnClickListener(v -> {
                animatePressScale(v);
                handleTakeAllDue();
            });
        }
    }

    private void updateMonthYearText() {
        if (monthYearText == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        monthYearText.setText(sdf.format(selectedDate.getTime()));
    }

    // ─── Settings / Edit Mode ─────────────────────────────────────────────────

    private void setupSettings() {
        if (settingsIcon != null) {
            settingsIcon.setOnClickListener(v -> {
                animatePressScale(v);
                isEditMode = !isEditMode;
                if (medicationAdapter != null) medicationAdapter.setEditMode(isEditMode);
                if (scheduleTimelineAdapter != null) scheduleTimelineAdapter.setEditMode(isEditMode);
                Toast.makeText(getContext(),
                        isEditMode ? "Edit Mode Enabled" : "Edit Mode Disabled",
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    // ─── Quick Actions ────────────────────────────────────────────────────────

    private void setupQuickActions() {
        if (btnMedQuickAdd != null) {
            btnMedQuickAdd.setOnClickListener(v -> {
                animatePressScale(v);
                showScanOptionsDialog();
            });
        }
        if (btnMedQuickScan != null) {
            btnMedQuickScan.setOnClickListener(v -> {
                animatePressScale(v);
                showScanOptionsDialog();
            });
        }
        if (btnMedQuickInteractions != null) {
            btnMedQuickInteractions.setOnClickListener(v -> {
                animatePressScale(v);
                // Trigger interaction check via re-loading full medication list
                new Thread(() -> {
                    List<MedicationEntity> meds = AppDatabaseProvider
                            .getInstance(requireContext()).medicationDao().getAllMedications();
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> checkAndDisplayAllInteractions(meds));
                }).start();
            });
        }
        if (btnMedQuickRefill != null) {
            btnMedQuickRefill.setOnClickListener(v -> {
                animatePressScale(v);
                showStyledDialog(
                        "⏰ Refill Reminder",
                        "Set a reminder when your medication supply is running low. Tap below to schedule a refill alert for any active medication.",
                        "Set Reminder",
                        () -> Toast.makeText(getContext(),
                                "Refill reminder feature coming soon!", Toast.LENGTH_SHORT).show()
                );
            });
        }

        // DDI banner click
        View.OnClickListener ddiClickListener = v -> {
            animatePressScale(v);
            showStyledDialog(
                    "⚠ Drug Interaction Alert",
                    "High Severity Warning:\n\n• Interaction: Warfarin ↔ Paracetamol\n• Risk: Increased risk of bleeding due to potential INR elevation with prolonged co-administration.\n• Action: Monitor INR levels closely. Consult your physician before taking concomitant doses.",
                    "Understood",
                    null
            );
        };
        if (cardDdiBanner != null) cardDdiBanner.setOnClickListener(ddiClickListener);
        if (btnDdiViewDetails != null) btnDdiViewDetails.setOnClickListener(ddiClickListener);

        // Notification Bell
        if (btnNotification != null) {
            btnNotification.setOnClickListener(v -> {
                animatePressScale(v);
                showStyledDialog(
                        "System Alerts",
                        "• Heatwave high-temperature alert active in your region\n• Routine evening medication due at 8:00 PM\n• High-severity interaction detected between Warfarin and Paracetamol",
                        "Close",
                        null
                );
            });
        }

        // Avatar
        if (avatarContainer != null) {
            avatarContainer.setOnClickListener(v -> {
                animatePressScale(v);
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new SettingsFragment())
                            .addToBackStack("settings")
                            .commit();
                }
            });
        }
    }

    // ─── Take All Due ──────────────────────────────────────────────────────────

    private void handleTakeAllDue() {
        new Thread(() -> {
            List<MedicationScheduleEntity> all = AppDatabaseProvider
                    .getInstance(requireContext()).scheduleDao().getAll();
            long now = System.currentTimeMillis();
            int count = 0;
            for (MedicationScheduleEntity s : all) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(s.timeMillis);
                boolean sameDay = isSameDay(c, selectedDate);
                String status = s.status != null ? s.status.toUpperCase() : "PENDING";
                // Only mark PENDING doses that are due (time <= now+15min) on selected day
                if (sameDay && "PENDING".equals(status) && s.timeMillis <= now + 15 * 60 * 1000L) {
                    AppDatabaseProvider.getInstance(requireContext())
                            .scheduleDao().updateStatus(s.id, "TAKEN");
                    count++;
                }
            }
            final int markedCount = count;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    loadSchedulesForSelectedDate();
                    String msg = markedCount > 0
                            ? markedCount + " dose" + (markedCount > 1 ? "s" : "") + " marked as Taken"
                            : "No pending doses due right now";
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // ─── Data Loading ─────────────────────────────────────────────────────────

    private void loadData() {
        new Thread(() -> {
            List<MedicationEntity> medications = AppDatabaseProvider
                    .getInstance(requireContext()).medicationDao().getAllMedications();
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                medicationAdapter = new MedicationAdapter(medications,
                        this::handleDeleteMedication, this::handleMedicationClick);
                medicationAdapter.setEditMode(isEditMode);
                if (medicationRecyclerView != null) {
                    medicationRecyclerView.setAdapter(medicationAdapter);
                    if (medications.isEmpty()) {
                        if (emptyCabinetLayout != null) emptyCabinetLayout.setVisibility(View.VISIBLE);
                        medicationRecyclerView.setVisibility(View.GONE);
                    } else {
                        if (emptyCabinetLayout != null) emptyCabinetLayout.setVisibility(View.GONE);
                        medicationRecyclerView.setVisibility(View.VISIBLE);
                    }
                }
                updateAdherenceHero(medications.size());
            });
            loadSchedulesForSelectedDate();
            checkAndDisplayAllInteractions(medications);
        }).start();
    }

    private void updateAdherenceHero(int medCount) {
        if (tvMedHeroSubtitle == null) return;
        if (medCount == 0) {
            tvMedHeroSubtitle.setText("Stay consistent. Stay healthier.");
        } else {
            tvMedHeroSubtitle.setText("Stay consistent. Stay healthier. (" + medCount + " in cabinet)");
        }
    }

    private void loadSchedulesForSelectedDate() {
        new Thread(() -> {
            List<MedicationScheduleEntity> allSchedules = AppDatabaseProvider
                    .getInstance(requireContext()).scheduleDao().getAll();
            long now = System.currentTimeMillis();

            // Filter for selected calendar day
            List<MedicationScheduleEntity> todaySchedules = new ArrayList<>();

            Calendar calItem = Calendar.getInstance();
            for (MedicationScheduleEntity s : allSchedules) {
                calItem.setTimeInMillis(s.timeMillis);
                if (isSameDay(calItem, selectedDate)) {
                    todaySchedules.add(s);
                }
            }

            // Sort by time
            todaySchedules.sort((a, b) -> Long.compare(a.timeMillis, b.timeMillis));

            // Summary counts
            int scheduled = todaySchedules.size();
            int pending = 0, missed = 0;
            for (MedicationScheduleEntity s : todaySchedules) {
                String st = s.status != null ? s.status.toUpperCase(Locale.ROOT) : "PENDING";
                if ("PENDING".equals(st)) {
                    if (s.timeMillis < now) missed++;
                    else pending++;
                } else if ("MISSED".equals(st)) {
                    missed++;
                }
            }
            int finalPending = pending;
            int finalMissed = missed;

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                // Update summary count views
                if (tvSummaryScheduledCount != null)
                    tvSummaryScheduledCount.setText(String.valueOf(scheduled));
                if (tvSummaryPendingCount != null)
                    tvSummaryPendingCount.setText(String.valueOf(finalPending));
                if (tvSummaryMissedCount != null)
                    tvSummaryMissedCount.setText(String.valueOf(finalMissed));

                // Today's schedule
                if (todaySchedules.isEmpty()) {
                    if (emptyScheduleContainer != null) emptyScheduleContainer.setVisibility(View.VISIBLE);
                    scheduleRecyclerView.setVisibility(View.GONE);
                } else {
                    if (emptyScheduleContainer != null) emptyScheduleContainer.setVisibility(View.GONE);
                    scheduleRecyclerView.setVisibility(View.VISIBLE);

                    scheduleTimelineAdapter = new ScheduleTimelineAdapter(todaySchedules,
                            new ScheduleTimelineAdapter.OnScheduleActionListener() {
                                @Override
                                public void onScheduleClick(MedicationScheduleEntity schedule) {
                                    showScheduleStatusDialog(schedule);
                                }

                                @Override
                                public void onEditTime(MedicationScheduleEntity schedule) {
                                    handleEditScheduleTime(schedule);
                                }

                                @Override
                                public void onMarkStatus(MedicationScheduleEntity schedule, String newStatus) {
                                    updateDoseStatus(schedule, newStatus);
                                }
                            });
                    scheduleTimelineAdapter.setEditMode(isEditMode);
                    scheduleRecyclerView.setAdapter(scheduleTimelineAdapter);
                }
            });
        }).start();
    }

    // ─── Styled Dialog Helper ─────────────────────────────────────────────────

    /**
     * Shows a consistently styled info dialog across the medication screen.
     * Replaces raw AlertDialog.Builder popups for better visual consistency.
     */
    private void showStyledDialog(String title, String message,
                                  String positiveLabel, @Nullable Runnable onPositive) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveLabel, (d, w) -> {
                    if (onPositive != null) onPositive.run();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // ─── Schedule Status Dialog ───────────────────────────────────────────────

    private void showScheduleStatusDialog(MedicationScheduleEntity schedule) {
        if (isEditMode) {
            handleEditScheduleTime(schedule);
            return;
        }

        SimpleDateFormat timeSdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        String timeStr = timeSdf.format(new java.util.Date(schedule.timeMillis));

        String[] options = {
                "✅ Mark as Taken",
                "❌ Mark as Missed",
                "⏳ Mark as Pending",
                "🕒 Change Reminder Time"
        };

        new AlertDialog.Builder(requireContext())
                .setTitle(schedule.drugName + " (" + timeStr + ")")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: updateDoseStatus(schedule, "TAKEN"); break;
                        case 1: updateDoseStatus(schedule, "MISSED"); break;
                        case 2: updateDoseStatus(schedule, "PENDING"); break;
                        case 3: handleEditScheduleTime(schedule); break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Status Update ────────────────────────────────────────────────────────

    private void updateDoseStatus(MedicationScheduleEntity schedule, String newStatus) {
        new Thread(() -> {
            schedule.status = newStatus;
            AppDatabaseProvider.getInstance(requireContext())
                    .scheduleDao().updateStatus(schedule.id, newStatus);

            // Log adherence
            String patientUid = "";
            FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
            if (fbUser != null) patientUid = fbUser.getUid();

            com.emergency.patient.db.AdherenceLogEntity log = new com.emergency.patient.db.AdherenceLogEntity();
            log.medicationId       = 0;
            log.medicationName     = schedule.drugName != null ? schedule.drugName : "";
            log.patientUid         = patientUid;
            log.scheduledTimeMillis = schedule.timeMillis;
            log.actionType         = newStatus;
            log.actedAt            = System.currentTimeMillis();
            log.deviceTimestamp    = System.currentTimeMillis();
            log.snoozeCount        = 0;

            AppDatabaseProvider.getInstance(requireContext()).adherenceLogDao().insertLog(log);
            FirebaseRepository.getInstance(requireContext())
                    .syncAdherenceLog(requireContext(), log, null);

            // Re-arm WorkManager if reverted to PENDING in future
            if ("PENDING".equals(newStatus) && schedule.timeMillis > System.currentTimeMillis()) {
                long delay = schedule.timeMillis - System.currentTimeMillis();
                androidx.work.Data workerData = new androidx.work.Data.Builder()
                        .putString("drug", schedule.drugName)
                        .putInt("scheduleId", schedule.id)
                        .putLong("scheduledTimeMillis", schedule.timeMillis)
                        .build();

                androidx.work.OneTimeWorkRequest work =
                        new androidx.work.OneTimeWorkRequest.Builder(
                                com.emergency.patient.scheduling.MedicationWorker.class)
                                .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                                .setInputData(workerData)
                                .addTag("med_" + schedule.drugName)
                                .build();

                androidx.work.WorkManager.getInstance(requireContext()).enqueue(work);
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(this::loadSchedulesForSelectedDate);
            }
        }).start();
    }

    // ─── Edit Schedule Time ───────────────────────────────────────────────────

    private void handleEditScheduleTime(MedicationScheduleEntity schedule) {
        String currentStatus = schedule.status != null ? schedule.status.toUpperCase() : "PENDING";
        if ("TAKEN".equals(currentStatus)) {
            showStyledDialog(
                    "Cannot Edit",
                    "This dose has already been marked as taken and cannot be modified for today.",
                    "OK", null
            );
            return;
        }

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(schedule.timeMillis);

        new android.app.TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
            Calendar newCal = Calendar.getInstance();
            newCal.setTimeInMillis(schedule.timeMillis);
            newCal.set(Calendar.HOUR_OF_DAY, hourOfDay);
            newCal.set(Calendar.MINUTE, minute);
            newCal.set(Calendar.SECOND, 0);
            newCal.set(Calendar.MILLISECOND, 0);
            final long newTimeMillis = newCal.getTimeInMillis();

            new Thread(() -> {
                ValidationResult result = AdherenceStateValidator.validate(
                        requireContext(), schedule, newTimeMillis);

                if (result.verdict == EditVerdict.BLOCKED_TAKEN
                        || result.verdict == EditVerdict.BLOCKED_PAST_TIME) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                showStyledDialog(
                                        result.verdict == EditVerdict.BLOCKED_TAKEN
                                                ? "Cannot Edit" : "Invalid Time",
                                        result.message, "OK", null
                                )
                        );
                    }
                    return;
                }

                String patientUid = "";
                FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
                if (fbUser != null) patientUid = fbUser.getUid();

                schedule.timeMillis = newTimeMillis;
                AppDatabaseProvider.getInstance(requireContext()).scheduleDao().update(schedule);

                if (result.verdict == EditVerdict.ALLOWED_MISSED_RECOVERY) {
                    AdherenceStateValidator.applyMissedRecovery(requireContext(), schedule, patientUid);
                }

                // Rebuild WorkManager workers
                String imageUri = "", dosage = "", fsId = "";
                List<MedicationEntity> allMeds = AppDatabaseProvider.getInstance(requireContext())
                        .medicationDao().getAllMedications();
                for (MedicationEntity med : allMeds) {
                    if (schedule.drugName != null && schedule.drugName.equals(med.name)) {
                        imageUri = med.imageUri != null ? med.imageUri : "";
                        dosage   = med.dosage != null ? med.dosage : "";
                        fsId     = med.firestoreId != null ? med.firestoreId : "";
                        break;
                    }
                }

                androidx.work.WorkManager.getInstance(requireContext())
                        .cancelAllWorkByTag("med_" + schedule.drugName);

                List<MedicationScheduleEntity> allSchedules = AppDatabaseProvider
                        .getInstance(requireContext()).scheduleDao().getAll();

                final long sevenDaysMs    = 7L * 24 * 60 * 60 * 1000;
                final String finalPuid    = patientUid;
                final String finalImgUri  = imageUri;
                final String finalDosage  = dosage;
                final String finalFsId    = fsId;

                for (MedicationScheduleEntity s : allSchedules) {
                    if (!schedule.drugName.equals(s.drugName)) continue;
                    String sStatus = s.status != null ? s.status.toUpperCase() : "PENDING";
                    if ("TAKEN".equals(sStatus)) continue;

                    long delay = s.timeMillis - System.currentTimeMillis();
                    if (delay <= 0 || delay > sevenDaysMs) continue;

                    androidx.work.Data workerData = new androidx.work.Data.Builder()
                            .putString("drug",              s.drugName)
                            .putInt("scheduleId",           s.id)
                            .putInt("medicationId",         0)
                            .putString("firestoreId",       finalFsId)
                            .putString("patientUid",        finalPuid)
                            .putLong("scheduledTimeMillis", s.timeMillis)
                            .putString("imageUri",          finalImgUri)
                            .putString("dosage",            finalDosage)
                            .putInt("snoozeCount",          0)
                            .build();

                    androidx.work.OneTimeWorkRequest work =
                            new androidx.work.OneTimeWorkRequest.Builder(
                                    com.emergency.patient.scheduling.MedicationWorker.class)
                                    .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                                    .setInputData(workerData)
                                    .addTag("med_" + s.drugName)
                                    .build();

                    androidx.work.WorkManager.getInstance(requireContext()).enqueue(work);
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(this::loadSchedulesForSelectedDate);
                }
            }).start();

        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
    }

    // ─── Delete Medication ────────────────────────────────────────────────────

    private void handleDeleteMedication(MedicationEntity med) {
        showStyledDialog(
                "Delete " + med.name + "?",
                "This will permanently remove " + med.name +
                        " and all its scheduled doses. This action cannot be undone.",
                "Delete",
                () -> new Thread(() -> {
                    AppDatabaseProvider.getInstance(requireContext()).medicationDao().delete(med);
                    AppDatabaseProvider.getInstance(requireContext()).scheduleDao().deleteByDrugName(med.name);
                    androidx.work.WorkManager.getInstance(requireContext())
                            .cancelAllWorkByTag("med_" + med.name);
                    if (getActivity() != null) getActivity().runOnUiThread(this::loadData);
                }).start()
        );
    }

    // ─── AI Medication Insights ────────────────────────────────────────────────

    private void handleMedicationClick(MedicationEntity med) {
        if (isEditMode) return;

        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
                .setTitle("AI Insights ✨")
                .setMessage("Fetching insights for " + med.name + "...\n\nPlease wait...")
                .setCancelable(false)
                .show();

        com.emergency.patient.ocr.GroqInterpreter.getMedicationPointers(med.name, resultText -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (loadingDialog.isShowing()) loadingDialog.dismiss();

                if (resultText == null) {
                    Toast.makeText(getContext(), "Failed to fetch insights", Toast.LENGTH_SHORT).show();
                    return;
                }

                String htmlResult = resultText
                        .replaceAll("(?m)^\\s*\\*\\s+(.*)$", "&#8226; $1<br>")
                        .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                        .replaceAll("\\*(.*?)\\*", "<i>$1</i>")
                        .replace("\n", "<br>");

                new AlertDialog.Builder(requireContext())
                        .setTitle("Why take " + med.name + "?")
                        .setMessage(android.text.Html.fromHtml(htmlResult,
                                android.text.Html.FROM_HTML_MODE_COMPACT))
                        .setPositiveButton("Got it", null)
                        .show();
            });
        });
    }

    // ─── Drug Interaction Check ────────────────────────────────────────────────

    private void checkAndDisplayAllInteractions(List<MedicationEntity> medications) {
        if (medications == null || medications.size() < 2) {
            if (getActivity() != null) getActivity().runOnUiThread(
                    () -> { if (cardDdiBanner != null) cardDdiBanner.setVisibility(View.GONE); });
            return;
        }

        List<String> drugNames = new ArrayList<>();
        for (MedicationEntity med : medications) {
            if (med.name != null && !med.name.trim().isEmpty()) drugNames.add(med.name);
        }

        com.emergency.patient.ocr.DrugInteractionEngine.checkAllInteractions(
                requireContext(), drugNames, interactions -> {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        if (cardDdiBanner == null) return;
                        if (interactions.isEmpty()) {
                            cardDdiBanner.setVisibility(View.GONE);
                        } else {
                            cardDdiBanner.setVisibility(View.VISIBLE);
                            int highCount = 0;
                            for (com.emergency.patient.ocr.Interaction i : interactions) {
                                if ("HIGH".equalsIgnoreCase(i.severity)
                                        || "SEVERE".equalsIgnoreCase(i.severity)) highCount++;
                            }
                            if (highCount > 0) {
                                tvDdiBannerTitle.setText("⚠ High Severity Interactions!");
                                tvDdiBannerTitle.setTextColor(android.graphics.Color.parseColor("#D32F2F"));
                                tvDdiBannerDesc.setText(highCount + " critical interaction"
                                        + (highCount > 1 ? "s" : "") + " — tap to review");
                                tvDdiBannerDesc.setTextColor(android.graphics.Color.parseColor("#9B1C1C"));
                            } else {
                                tvDdiBannerTitle.setText("Drug Interactions Detected");
                                tvDdiBannerTitle.setTextColor(android.graphics.Color.parseColor("#D97706"));
                                tvDdiBannerDesc.setText(interactions.size() + " interaction"
                                        + (interactions.size() > 1 ? "s" : "") + " in your cabinet — tap to review");
                                tvDdiBannerDesc.setTextColor(android.graphics.Color.parseColor("#92400E"));
                            }
                            cardDdiBanner.setOnClickListener(vv -> showInteractionsDialog(interactions));
                        }
                    });
                });
    }

    private void showInteractionsDialog(List<com.emergency.patient.ocr.Interaction> interactions) {
        StringBuilder sb = new StringBuilder();
        for (com.emergency.patient.ocr.Interaction i : interactions) {
            String emoji = "HIGH".equalsIgnoreCase(i.severity) || "SEVERE".equalsIgnoreCase(i.severity)
                    ? "🔴" : "🟡";
            sb.append(emoji).append(" ").append(i.drugA).append(" + ").append(i.drugB).append("\n");
            sb.append(i.description).append("\n\n");
        }
        String interactionsText = sb.toString().trim();

        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Analyzing Interactions ✨")
                .setMessage("Generating breakdown of risks...\n\nPlease wait...")
                .setCancelable(false)
                .show();

        com.emergency.patient.ocr.GroqInterpreter.getDdiPointers(interactionsText, resultText -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (loadingDialog.isShowing()) loadingDialog.dismiss();

                String finalMessage = interactionsText;
                if (resultText != null) {
                    String htmlResult = resultText
                            .replaceAll("(?m)^\\s*\\*\\s+(.*)$", "&#8226; $1<br>")
                            .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                            .replaceAll("\\*(.*?)\\*", "<i>$1</i>")
                            .replace("\n", "<br>");
                    finalMessage = finalMessage.replace("\n", "<br>") + "<br><br>---<br><br>" + htmlResult;
                } else {
                    finalMessage = finalMessage.replace("\n", "<br>");
                }

                new AlertDialog.Builder(requireContext())
                        .setTitle("Medication Interactions")
                        .setMessage(android.text.Html.fromHtml(finalMessage,
                                android.text.Html.FROM_HTML_MODE_COMPACT))
                        .setPositiveButton("Understood", null)
                        .show();
            });
        });
    }

    // ─── Scan / Camera ────────────────────────────────────────────────────────

    private void showScanOptionsDialog() {
        String[] options = {"📷 Take Photo", "🖼 Choose from Gallery", "✏️ Add Manually"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Add Medicine")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) openCamera();
                    else if (which == 1) openGallery();
                    else {
                        Intent intent = new Intent(getContext(), MedicationOcrActivity.class);
                        intent.putExtra(MedicationOcrActivity.EXTRA_IS_MANUAL, true);
                        startActivity(intent);
                    }
                })
                .show();
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            try {
                File photoFile = new File(requireActivity().getCacheDir(),
                        "med_scan_" + System.currentTimeMillis() + ".jpg");
                currentPhotoUri = FileProvider.getUriForFile(requireContext(),
                        requireContext().getApplicationContext().getPackageName() + ".provider",
                        photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                startActivityForResult(intent, REQUEST_CAMERA);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Unable to launch camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAMERA && resultCode == getActivity().RESULT_OK) {
            if (currentPhotoUri != null) {
                accumulatedPhotoUris.add(currentPhotoUri.toString());
                showAddAnotherImageDialog();
            }
        } else if (requestCode == REQUEST_GALLERY && resultCode == getActivity().RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                accumulatedPhotoUris.add(imageUri.toString());
                showAddAnotherImageDialog();
            }
        }
    }

    private void showAddAnotherImageDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_scan_prompt, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        dialogView.findViewById(R.id.btn_scan_more).setOnClickListener(v -> {
            dialog.dismiss();
            showScanOptionsDialog();
        });
        dialogView.findViewById(R.id.btn_finish_scan).setOnClickListener(v -> {
            dialog.dismiss();
            startOcrActivity();
        });

        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void startOcrActivity() {
        if (accumulatedPhotoUris.isEmpty()) return;
        Intent intent = new Intent(getContext(), MedicationOcrActivity.class);
        intent.putStringArrayListExtra(MedicationOcrActivity.EXTRA_IMAGE_URIS,
                new ArrayList<>(accumulatedPhotoUris));
        startActivity(intent);
        accumulatedPhotoUris.clear();
    }

    // ─── Animations ───────────────────────────────────────────────────────────

    private void startEntranceAnimations() {
        animateFadeSlide(medHeaderSection, 0);
        animateFadeSlide(medHeroSection, 1);
        animateFadeSlide(cardDdiBanner, 2);
        animateFadeSlide(medDateNavSection, 2);
        animateFadeSlide(calendarRecyclerView, 3);
        animateFadeSlide(medSummarySection, 4);
        animateFadeSlide(medScheduleHeaderSection, 5);
        animateFadeSlide(scheduleRecyclerView, 6);
        animateFadeSlide(medQuickActionsSection, 7);
    }

    private void animateFadeSlide(View view, int index) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(28f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(380)
                .setStartDelay(index * 55L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void animatePressScale(View v) {
        if (v == null) return;
        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70)
                .withEndAction(() ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120)
                                .setInterpolator(new OvershootInterpolator(1.8f)).start()
                ).start();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
}
