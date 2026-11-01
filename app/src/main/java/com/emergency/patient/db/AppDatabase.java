package com.emergency.patient.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.emergency.patient.luna.db.CycleDao;
import com.emergency.patient.luna.db.LunaTypeConverters;
import com.emergency.patient.luna.db.SymptomDao;
import com.emergency.patient.luna.model.CycleEntry;
import com.emergency.patient.luna.model.SymptomEntry;

@Database(
    entities = {
        PatientEntity.class,
        EmergencyContactEntity.class,
        HealthDocumentEntity.class,
        HealthDocumentChunkEntity.class,
        ChatMessageEntity.class,
        MedicationEntity.class,         // ← OCR medication records
        MedicationScheduleEntity.class, // ← Scheduling tracking
        OcrCorrectionEntity.class,      // ← OCR self-improvement corrections
        FallEventEntity.class,          // ← Fall detection black box log
        CycleEntry.class,               // ← Luna: menstrual cycle entries
        SymptomEntry.class,             // ← Luna: daily symptom logs
        GuardianLinkEntity.class,       // ← Guardian-patient relationships
        AdherenceLogEntity.class,       // ← Medication adherence audit log
        EnvironmentEntity.class         // ← Environmental & seasonal telemetry
    },
    version = 19,
    exportSchema = false
)
@TypeConverters({LunaTypeConverters.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract PatientDao patientDao();
    public abstract EmergencyContactDao emergencyContactDao();
    public abstract HealthDocumentDao healthDocumentDao();
    public abstract HealthDocumentChunkDao healthDocumentChunkDao();
    public abstract ChatMessageDao chatMessageDao();
    public abstract MedicationDao medicationDao();
    public abstract ScheduleDao scheduleDao();
    public abstract OcrCorrectionDao ocrCorrectionDao();
    public abstract FallEventDao fallEventDao();
    public abstract GuardianLinkDao guardianLinkDao();
    public abstract AdherenceLogDao adherenceLogDao();
    public abstract EnvironmentDao environmentDao();

    // ── Luna DAOs ──────────────────────────────────────────────────────────
    public abstract CycleDao cycleDao();
    public abstract SymptomDao symptomDao();

    // ── Migration 16 → 17: Add Luna tables ────────────────────────────────
    public static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS luna_cycle_entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "startDate INTEGER NOT NULL, " +
                "length INTEGER NOT NULL, " +
                "notes TEXT)"
            );
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS luna_symptom_entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "date INTEGER NOT NULL, " +
                "symptomsJson TEXT, " +
                "intensity INTEGER NOT NULL, " +
                "cycleDay INTEGER NOT NULL, " +
                "phase TEXT, " +
                "notes TEXT)"
            );
        }
    };

    // ── Migration 17 → 18: Add cloud sync fields + guardian/adherence tables ──
    public static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add cloud sync columns to medications table
            database.execSQL("ALTER TABLE medications ADD COLUMN firestoreId TEXT");
            database.execSQL("ALTER TABLE medications ADD COLUMN lastSyncedAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE medications ADD COLUMN syncSource TEXT DEFAULT 'ocr'");
            database.execSQL("ALTER TABLE medications ADD COLUMN updatedByGuardian INTEGER NOT NULL DEFAULT 0");

            // Create guardian_links table
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS guardian_links (" +
                "linkId TEXT PRIMARY KEY NOT NULL, " +
                "guardianUid TEXT NOT NULL DEFAULT '', " +
                "patientUid TEXT NOT NULL DEFAULT '', " +
                "permissionsJson TEXT, " +
                "createdAt INTEGER NOT NULL DEFAULT 0, " +
                "active INTEGER NOT NULL DEFAULT 1)"
            );

            // Create adherence_logs table
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS adherence_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "patientUid TEXT NOT NULL DEFAULT '', " +
                "medicationId INTEGER NOT NULL DEFAULT 0, " +
                "medicationFirestoreId TEXT, " +
                "medicationName TEXT, " +
                "scheduledTimeMillis INTEGER NOT NULL DEFAULT 0, " +
                "actionType TEXT NOT NULL DEFAULT 'PENDING', " +
                "actedAt INTEGER NOT NULL DEFAULT 0, " +
                "snoozeCount INTEGER NOT NULL DEFAULT 0, " +
                "deviceTimestamp INTEGER NOT NULL DEFAULT 0, " +
                "firestoreLogId TEXT, " +
                "synced INTEGER NOT NULL DEFAULT 0)"
            );
        }
    };
}
