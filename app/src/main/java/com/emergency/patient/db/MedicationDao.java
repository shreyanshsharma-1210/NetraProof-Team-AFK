package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * MedicationDao — Data Access Object for the medications table.
 *
 * Used by MedicationOcrActivity (local OCR flow) and MedicationSyncService
 * (cloud guardian sync flow).
 */
@Dao
public interface MedicationDao {

    /**
     * Insert a new medication record. REPLACE strategy handles any
     * duplicate primary-key edge cases gracefully.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertMedication(MedicationEntity medication);

    /** Update an existing medication record (used by sync engine). */
    @Update
    void updateMedication(MedicationEntity medication);

    /** Retrieve all medications for a specific patient, newest first. */
    @Query("SELECT * FROM medications WHERE patientUuid = :patientUuid ORDER BY createdAt DESC")
    List<MedicationEntity> getMedicationsForPatient(String patientUuid);

    /** Retrieve every medication row (admin / debug use). */
    @Query("SELECT * FROM medications ORDER BY createdAt DESC")
    List<MedicationEntity> getAllMedications();

    /** Find a local medication by its name for schedule display. */
    @Query("SELECT * FROM medications WHERE name = :name LIMIT 1")
    MedicationEntity findByName(String name);

    /** Find a local medication by its Firestore document ID (for sync deduplication). */
    @Query("SELECT * FROM medications WHERE firestoreId = :firestoreId LIMIT 1")
    MedicationEntity findByFirestoreId(String firestoreId);

    /** Mark a local record as synced by stamping its firestoreId + lastSyncedAt. */
    @Query("UPDATE medications SET firestoreId = :firestoreId, lastSyncedAt = :syncedAt WHERE id = :id")
    void markSynced(int id, String firestoreId, long syncedAt);

    /** Delete a medication by its Firestore document ID (guardian-initiated remove). */
    @Query("DELETE FROM medications WHERE firestoreId = :firestoreId")
    void deleteByFirestoreId(String firestoreId);

    /** Delete a specific medication by primary key. */
    @Query("DELETE FROM medications WHERE id = :id")
    void deleteMedication(int id);

    /** Wipe all medication records (logout / profile-reset scenarios). */
    @Query("DELETE FROM medications")
    void deleteAllMedications();

    @androidx.room.Delete
    void delete(MedicationEntity medication);
}
