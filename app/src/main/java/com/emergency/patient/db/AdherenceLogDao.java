package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AdherenceLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertLog(AdherenceLogEntity log);

    @Update
    void updateLog(AdherenceLogEntity log);

    @Query("SELECT * FROM adherence_logs WHERE patientUid = :patientUid ORDER BY scheduledTimeMillis DESC")
    List<AdherenceLogEntity> getLogsForPatient(String patientUid);

    @Query("SELECT * FROM adherence_logs WHERE synced = 0")
    List<AdherenceLogEntity> getUnsyncedLogs();

    @Query("UPDATE adherence_logs SET synced = 1, firestoreLogId = :firestoreId WHERE id = :id")
    void markSynced(int id, String firestoreId);

    @Query("UPDATE adherence_logs SET actionType = :action, actedAt = :actedAt, snoozeCount = :snoozeCount WHERE id = :id")
    void updateAction(int id, String action, long actedAt, int snoozeCount);

    /** Update scheduledTimeMillis for an existing log entry (used on reminder time edit). */
    @Query("UPDATE adherence_logs SET scheduledTimeMillis = :newTimeMillis, actionType = :actionType, actedAt = :actedAt, synced = 0 WHERE id = :id")
    void updateScheduledTime(int id, long newTimeMillis, String actionType, long actedAt);

    @Query("SELECT * FROM adherence_logs WHERE medicationId = :medicationId ORDER BY scheduledTimeMillis DESC")
    List<AdherenceLogEntity> getLogsForMedication(int medicationId);

    /** Lookup by drug name — used by AdherenceStateValidator fuzzy-match fallback. */
    @Query("SELECT * FROM adherence_logs WHERE medicationName = :drugName ORDER BY scheduledTimeMillis DESC")
    List<AdherenceLogEntity> getLogsForMedication(String drugName);

    /** Exact lookup by scheduled time (epoch ms) — primary key for dedup and validation. */
    @Query("SELECT * FROM adherence_logs WHERE scheduledTimeMillis = :scheduledTimeMillis ORDER BY id DESC LIMIT 1")
    AdherenceLogEntity getByScheduledTime(long scheduledTimeMillis);

    @Query("SELECT * FROM adherence_logs WHERE id = :id LIMIT 1")
    AdherenceLogEntity getById(int id);

    @Query("DELETE FROM adherence_logs WHERE patientUid = :patientUid")
    void deleteAllForPatient(String patientUid);
}
