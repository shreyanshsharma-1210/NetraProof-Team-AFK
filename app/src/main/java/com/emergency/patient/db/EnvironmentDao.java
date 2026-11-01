package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EnvironmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertEnvironment(EnvironmentEntity entity);

    @Query("SELECT * FROM environment_records ORDER BY timestampMillis DESC LIMIT 1")
    EnvironmentEntity getLatestEnvironment();

    @Query("SELECT * FROM environment_records ORDER BY timestampMillis DESC")
    List<EnvironmentEntity> getAllRecords();
}
