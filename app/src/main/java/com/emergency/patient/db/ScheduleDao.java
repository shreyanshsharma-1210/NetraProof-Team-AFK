package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ScheduleDao {

    @Insert
    long insert(MedicationScheduleEntity entity);

    @Query("UPDATE medication_schedule SET status = :status WHERE id = :id")
    void updateStatus(int id, String status);

    @Query("SELECT * FROM medication_schedule")
    List<MedicationScheduleEntity> getAll();

    @Query("SELECT * FROM medication_schedule WHERE drugName = :drugName")
    List<MedicationScheduleEntity> getByDrugName(String drugName);

    @androidx.room.Update
    void update(MedicationScheduleEntity entity);

    @Query("DELETE FROM medication_schedule WHERE drugName = :drugName")
    void deleteByDrugName(String drugName);

    @Query("DELETE FROM medication_schedule WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM medication_schedule")
    void deleteAll();

    @Query("UPDATE medication_schedule SET status = 'MISSED' WHERE status = 'PENDING' AND timeMillis < :cutoffMillis")
    void markPendingBeforeAsMissed(long cutoffMillis);

    /** Find a specific PENDING row by drug name and exact scheduled time (used by MissedDoseHandler). */
    @Query("SELECT * FROM medication_schedule WHERE drugName = :drugName AND timeMillis = :timeMillis AND status = 'PENDING' LIMIT 1")
    MedicationScheduleEntity getByDrugNameAndTime(String drugName, long timeMillis);
}
