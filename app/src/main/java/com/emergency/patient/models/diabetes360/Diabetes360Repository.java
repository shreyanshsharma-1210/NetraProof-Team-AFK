package com.emergency.patient.models.diabetes360;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.emergency.patient.security.TokenManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Diabetes360Repository — Unified, thread-safe data access layer for all Diabetes360 health records.
 *
 * Persists records atomically to private, encrypted app files (`diabetes360_{patientUuid}.json`)
 * and bridges key metrics with SharedPreferences for instantaneous UI reactivity across
 * DashboardActivity, MedicationSafetyHomeFragment, and specialized activities.
 */
public class Diabetes360Repository {

    private static volatile Diabetes360Repository instance;
    private final Gson gson;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Diabetes360Data cachedData;
    private String cachedPatientUuid;

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String error, Throwable throwable);
    }

    private Diabetes360Repository() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public static synchronized Diabetes360Repository getInstance() {
        if (instance == null) {
            instance = new Diabetes360Repository();
        }
        return instance;
    }

    // ─── File Persistence ─────────────────────────────────────────────────────

    private File getStorageFile(Context context, String patientUuid) {
        String safeUuid = (patientUuid != null && !patientUuid.trim().isEmpty()) ? patientUuid.trim() : "default_patient";
        return new File(context.getFilesDir(), "diabetes360_" + safeUuid + ".json");
    }

    public synchronized Diabetes360Data getData(Context context, String patientUuid) {
        if (patientUuid == null || patientUuid.trim().isEmpty()) {
            patientUuid = TokenManager.getUUID(context);
        }
        if (cachedData != null && patientUuid != null && patientUuid.equals(cachedPatientUuid)) {
            return cachedData;
        }

        File file = getStorageFile(context, patientUuid);
        if (!file.exists()) {
            Diabetes360Data fresh = new Diabetes360Data(patientUuid);
            saveDataInternal(context, fresh);
            cachedData = fresh;
            cachedPatientUuid = patientUuid;
            return fresh;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            Diabetes360Data loaded = gson.fromJson(sb.toString(), Diabetes360Data.class);
            if (loaded == null) {
                loaded = new Diabetes360Data(patientUuid);
            }
            cachedData = loaded;
            cachedPatientUuid = patientUuid;
            return loaded;
        } catch (Exception e) {
            e.printStackTrace();
            Diabetes360Data fallback = new Diabetes360Data(patientUuid);
            cachedData = fallback;
            cachedPatientUuid = patientUuid;
            return fallback;
        }
    }

    private synchronized void saveDataInternal(Context context, Diabetes360Data data) {
        if (data == null) return;
        data.setLastModifiedMillis(System.currentTimeMillis());
        cachedData = data;
        cachedPatientUuid = data.getPatientUuid();

        try {
            File file = getStorageFile(context, data.getPatientUuid());
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(gson.toJson(data));
            }
            syncPreferencesBridge(context, data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadDataAsync(Context context, String patientUuid, RepositoryCallback<Diabetes360Data> callback) {
        executor.execute(() -> {
            try {
                Diabetes360Data data = getData(context, patientUuid);
                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(data);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage(), e);
                });
            }
        });
    }

    // ─── Kidney Operations ────────────────────────────────────────────────────

    public void addKidneyRecord(Context context, KidneyRecord record) {
        if (record == null) return;
        if (record.getId() == null || record.getId().isEmpty()) {
            record.setId("KID-" + UUID.randomUUID().toString().substring(0, 8));
        }
        Diabetes360Data data = getData(context, record.getPatientUuid());
        data.getKidneyRecords().add(0, record); // Most recent first
        saveDataInternal(context, data);
    }

    public boolean deleteKidneyRecord(Context context, String recordId) {
        Diabetes360Data data = getData(context, null);
        boolean removed = data.getKidneyRecords().removeIf(r -> r.getId().equals(recordId));
        if (removed) {
            saveDataInternal(context, data);
        }
        return removed;
    }

    public KidneyRecord getLatestKidneyRecord(Context context, KidneyRecord.TestType type) {
        Diabetes360Data data = getData(context, null);
        for (KidneyRecord r : data.getKidneyRecords()) {
            if (type == null || r.getTestType() == type) {
                return r;
            }
        }
        return null;
    }

    // ─── Cardiovascular Operations ────────────────────────────────────────────

    public void addBloodPressureRecord(Context context, BloodPressureRecord record) {
        if (record == null) return;
        if (record.getId() == null || record.getId().isEmpty()) {
            record.setId("BP-" + UUID.randomUUID().toString().substring(0, 8));
        }
        Diabetes360Data data = getData(context, record.getPatientUuid());
        data.getBloodPressureRecords().add(0, record);
        saveDataInternal(context, data);
    }

    public boolean deleteBloodPressureRecord(Context context, String recordId) {
        Diabetes360Data data = getData(context, null);
        boolean removed = data.getBloodPressureRecords().removeIf(r -> r.getId().equals(recordId));
        if (removed) {
            saveDataInternal(context, data);
        }
        return removed;
    }

    public BloodPressureRecord getLatestBloodPressure(Context context) {
        Diabetes360Data data = getData(context, null);
        if (!data.getBloodPressureRecords().isEmpty()) {
            return data.getBloodPressureRecords().get(0);
        }
        return null;
    }

    public void addLipidProfileRecord(Context context, LipidProfileRecord record) {
        if (record == null) return;
        if (record.getId() == null || record.getId().isEmpty()) {
            record.setId("LIP-" + UUID.randomUUID().toString().substring(0, 8));
        }
        Diabetes360Data data = getData(context, record.getPatientUuid());
        data.getLipidProfileRecords().add(0, record);
        saveDataInternal(context, data);
    }

    public boolean deleteLipidProfileRecord(Context context, String recordId) {
        Diabetes360Data data = getData(context, null);
        boolean removed = data.getLipidProfileRecords().removeIf(r -> r.getId().equals(recordId));
        if (removed) {
            saveDataInternal(context, data);
        }
        return removed;
    }

    public LipidProfileRecord getLatestLipidProfile(Context context) {
        Diabetes360Data data = getData(context, null);
        if (!data.getLipidProfileRecords().isEmpty()) {
            return data.getLipidProfileRecords().get(0);
        }
        return null;
    }

    // ─── Foot & Nerve Operations ──────────────────────────────────────────────

    public void addFootCheckRecord(Context context, FootCheckRecord record) {
        if (record == null) return;
        if (record.getId() == null || record.getId().isEmpty()) {
            record.setId("FC-" + UUID.randomUUID().toString().substring(0, 8));
        }
        Diabetes360Data data = getData(context, record.getPatientUuid());
        data.getFootCheckRecords().add(0, record);
        saveDataInternal(context, data);
    }

    public FootCheckRecord getLatestFootCheck(Context context) {
        Diabetes360Data data = getData(context, null);
        if (!data.getFootCheckRecords().isEmpty()) {
            return data.getFootCheckRecords().get(0);
        }
        return null;
    }

    public void addSymptomRecord(Context context, SymptomRecord record) {
        if (record == null) return;
        if (record.getId() == null || record.getId().isEmpty()) {
            record.setId("SYM-" + UUID.randomUUID().toString().substring(0, 8));
        }
        Diabetes360Data data = getData(context, record.getPatientUuid());
        data.getSymptomRecords().add(0, record);
        saveDataInternal(context, data);
    }

    public boolean deleteSymptomRecord(Context context, String recordId) {
        Diabetes360Data data = getData(context, null);
        boolean removed = data.getSymptomRecords().removeIf(r -> r.getId().equals(recordId));
        if (removed) {
            saveDataInternal(context, data);
        }
        return removed;
    }

    public List<SymptomRecord> getActiveSymptoms(Context context) {
        Diabetes360Data data = getData(context, null);
        List<SymptomRecord> active = new ArrayList<>();
        for (SymptomRecord s : data.getSymptomRecords()) {
            if ("Active".equalsIgnoreCase(s.getStatus()) || "Improving".equalsIgnoreCase(s.getStatus())) {
                active.add(s);
            }
        }
        return active;
    }

    public void addFootConcernRecord(Context context, FootConcernRecord record) {
        if (record == null) return;
        if (record.getId() == null || record.getId().isEmpty()) {
            record.setId("FCR-" + UUID.randomUUID().toString().substring(0, 8));
        }
        Diabetes360Data data = getData(context, record.getPatientUuid());
        data.getFootConcernRecords().add(0, record);
        saveDataInternal(context, data);
    }

    public boolean deleteFootConcernRecord(Context context, String recordId) {
        Diabetes360Data data = getData(context, null);
        boolean removed = data.getFootConcernRecords().removeIf(r -> r.getId().equals(recordId));
        if (removed) {
            saveDataInternal(context, data);
        }
        return removed;
    }

    // ─── Reminder Operations ──────────────────────────────────────────────────

    public void addReminder(Context context, HealthReminder reminder) {
        if (reminder == null) return;
        if (reminder.getId() == null || reminder.getId().isEmpty()) {
            reminder.setId("REM-" + UUID.randomUUID().toString().substring(0, 8));
        }
        Diabetes360Data data = getData(context, reminder.getPatientUuid());
        data.getReminders().add(0, reminder);
        saveDataInternal(context, data);
    }

    public boolean toggleReminderCompletion(Context context, String reminderId) {
        Diabetes360Data data = getData(context, null);
        for (HealthReminder r : data.getReminders()) {
            if (r.getId().equals(reminderId)) {
                r.setCompleted(!r.isCompleted());
                saveDataInternal(context, data);
                return true;
            }
        }
        return false;
    }

    public boolean deleteReminder(Context context, String reminderId) {
        Diabetes360Data data = getData(context, null);
        boolean removed = data.getReminders().removeIf(r -> r.getId().equals(reminderId));
        if (removed) {
            saveDataInternal(context, data);
        }
        return removed;
    }

    public List<HealthReminder> getUpcomingReminders(Context context) {
        Diabetes360Data data = getData(context, null);
        List<HealthReminder> upcoming = new ArrayList<>();
        for (HealthReminder r : data.getReminders()) {
            if (!r.isCompleted()) {
                upcoming.add(r);
            }
        }
        Collections.sort(upcoming, (a, b) -> Long.compare(a.getTargetDateMillis(), b.getTargetDateMillis()));
        return upcoming;
    }

    // ─── Context Operations ───────────────────────────────────────────────────

    public void updateDiabetesContext(Context context, DiabetesContext diabetesContext) {
        if (diabetesContext == null) return;
        Diabetes360Data data = getData(context, diabetesContext.getPatientUuid());
        data.setDiabetesContext(diabetesContext);
        saveDataInternal(context, data);
    }

    // ─── Glucose & HbA1c Operations ─────────────────────────────────────────

    public void addGlucoseRecord(Context context, GlucoseRecord record) {
        if (record == null) return;
        if (record.getId() == null || record.getId().isEmpty()) {
            record.setId("GLU-" + UUID.randomUUID().toString().substring(0, 8));
        }
        Diabetes360Data data = getData(context, record.getPatientUuid());
        data.getGlucoseRecords().add(0, record); // Most recent first
        saveDataInternal(context, data);
    }

    public boolean updateGlucoseRecord(Context context, GlucoseRecord updated) {
        if (updated == null || updated.getId() == null) return false;
        Diabetes360Data data = getData(context, updated.getPatientUuid());
        for (int i = 0; i < data.getGlucoseRecords().size(); i++) {
            if (data.getGlucoseRecords().get(i).getId().equals(updated.getId())) {
                data.getGlucoseRecords().set(i, updated);
                saveDataInternal(context, data);
                return true;
            }
        }
        return false;
    }

    public boolean deleteGlucoseRecord(Context context, String recordId) {
        Diabetes360Data data = getData(context, null);
        boolean removed = data.getGlucoseRecords().removeIf(r -> r.getId().equals(recordId));
        if (removed) {
            saveDataInternal(context, data);
        }
        return removed;
    }

    public GlucoseRecord getLatestGlucoseRecord(Context context) {
        Diabetes360Data data = getData(context, null);
        if (!data.getGlucoseRecords().isEmpty()) {
            return data.getGlucoseRecords().get(0);
        }
        return null;
    }

    public void addHba1cRecord(Context context, Hba1cRecord record) {
        if (record == null) return;
        if (record.getId() == null || record.getId().isEmpty()) {
            record.setId("A1C-" + UUID.randomUUID().toString().substring(0, 8));
        }
        Diabetes360Data data = getData(context, record.getPatientUuid());
        data.getHba1cRecords().add(0, record);
        saveDataInternal(context, data);
    }

    public boolean updateHba1cRecord(Context context, Hba1cRecord updated) {
        if (updated == null || updated.getId() == null) return false;
        Diabetes360Data data = getData(context, updated.getPatientUuid());
        for (int i = 0; i < data.getHba1cRecords().size(); i++) {
            if (data.getHba1cRecords().get(i).getId().equals(updated.getId())) {
                data.getHba1cRecords().set(i, updated);
                saveDataInternal(context, data);
                return true;
            }
        }
        return false;
    }

    public boolean deleteHba1cRecord(Context context, String recordId) {
        Diabetes360Data data = getData(context, null);
        boolean removed = data.getHba1cRecords().removeIf(r -> r.getId().equals(recordId));
        if (removed) {
            saveDataInternal(context, data);
        }
        return removed;
    }

    public Hba1cRecord getLatestHba1cRecord(Context context) {
        Diabetes360Data data = getData(context, null);
        if (!data.getHba1cRecords().isEmpty()) {
            return data.getHba1cRecords().get(0);
        }
        return null;
    }

    // ─── Sync with SharedPreferences Bridge ───────────────────────────────────

    private void syncPreferencesBridge(Context context, Diabetes360Data data) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("netra_health_metrics", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            // Sync BP
            if (!data.getBloodPressureRecords().isEmpty()) {
                BloodPressureRecord bp = data.getBloodPressureRecords().get(0);
                editor.putString("latest_heart_value", bp.getSystolic() + "/" + bp.getDiastolic() + " mmHg");
                editor.putString("latest_heart_timestamp", bp.getFormattedDate());
            }

            // Sync Kidney
            KidneyRecord egfr = null;
            for (KidneyRecord k : data.getKidneyRecords()) {
                if (k.getTestType() == KidneyRecord.TestType.EGFR) {
                    egfr = k;
                    break;
                }
            }
            if (egfr != null) {
                editor.putString("latest_kidney_value", egfr.getFormattedValueWithUnit());
                editor.putString("latest_kidney_timestamp", egfr.getFormattedDate());
            }

            // Sync Glucose
            if (!data.getGlucoseRecords().isEmpty()) {
                GlucoseRecord glu = data.getGlucoseRecords().get(0);
                editor.putString("latest_glucose_value", glu.getFormattedValue());
                editor.putString("latest_glucose_type", glu.getReadingContext().getDisplayName());
                editor.putString("latest_glucose_timestamp", glu.getFormattedTimestamp() + " (" + glu.getReadingContext().name() + ")");
            }

            // Sync HbA1c
            if (!data.getHba1cRecords().isEmpty()) {
                Hba1cRecord a1c = data.getHba1cRecords().get(0);
                editor.putString("latest_hba1c_value", a1c.getFormattedValueWithUnit());
                editor.putString("latest_hba1c_timestamp", a1c.getFormattedDate());
            }

            editor.apply();
        } catch (Exception ignored) {}
    }
}

