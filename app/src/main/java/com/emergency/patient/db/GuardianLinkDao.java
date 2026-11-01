package com.emergency.patient.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface GuardianLinkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(GuardianLinkEntity link);

    @Query("SELECT * FROM guardian_links WHERE patientUid = :patientUid AND active = 1")
    List<GuardianLinkEntity> getLinksForPatient(String patientUid);

    @Query("SELECT * FROM guardian_links WHERE guardianUid = :guardianUid AND active = 1")
    List<GuardianLinkEntity> getLinksByGuardian(String guardianUid);

    @Query("UPDATE guardian_links SET active = 0 WHERE linkId = :linkId")
    void deactivateLink(String linkId);

    @Query("DELETE FROM guardian_links WHERE linkId = :linkId")
    void deleteLink(String linkId);
}
